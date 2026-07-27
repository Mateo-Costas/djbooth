package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.ChannelSettings;

import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.engines.SFXEngine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A {@link SFXEngine} that inserts a DJ EQ + colour filter + echo into WaterMedia's audio path. It
 * wraps a real {@link ALEngine} (OpenAL) and forwards every call to it, except {@link #upload(ByteBuffer)}:
 * there it intercepts the decoded PCM, runs a 3-band shelving/peaking EQ, a resonant sweep filter and
 * a feedback delay over each channel in Java, then hands the processed PCM to the inner engine.
 *
 * <p>This is why real EQ/filter/FX is possible without any native DSP: {@code FFMediaPlayer} only ever
 * talks to the {@code SFXEngine} interface, so a custom engine can transform the audio in between.
 * Handles S16 and FLT sample formats (what FFmpeg negotiates in practice); any other format is passed
 * through untouched so nothing breaks.
 */
public final class DspSfxEngine extends SFXEngine {
    // The three-band channel EQ, its knob smoothing and the output limiting all live in
    // ChannelEq: it holds no WaterMedia types, so unlike this class it can be unit tested.
    private static final double ECHO_SECONDS = 0.35; // fixed delay time (matches a slow beat echo)
    private static final double GAIN_MAX = 2.0;    // trim: knob 0.5 = unity, 1.0 = +6 dB

    private final ALEngine inner = ALEngine.buildDefault();

    // Per-channel filter chains: the three-band EQ, then a COLOR FX stage.
    private final ChannelEq eq = new ChannelEq();
    private ColorFx[] color;
    private BeatFx[] beat;
    private PitchShifter[] keyLock;
    private boolean supported; // true when the negotiated format is one we filter
    private boolean warnedReadOnly; // log the read-only fallback once, not per audio block

    // Per-channel echo delay lines.
    private float[][] delay;
    private int[] delayPos;
    private int delayLen;

    // Knob params (0..1). Written from the client thread, read on the audio thread.
    private volatile float pLow = 0.5f, pMid = 0.5f, pHigh = 0.5f, pFilter = 0.5f, pEcho = 0f;
    private volatile float pGain = 0.5f;
    private volatile int pColorMode = com.osgworld.djbooth.mixer.ColorFxModes.FILTER;
    private volatile float pColorParam = 0.5f;
    private volatile int pBeatType = com.osgworld.djbooth.mixer.BeatFxTypes.DELAY;
    private volatile int pBeatBands = com.osgworld.djbooth.mixer.BeatFxTypes.BANDS_ALL;
    private volatile float pBeatSeconds = 0.5f, pBeatDepth = 0.5f;
    private volatile boolean pBeatOn = false;
    private volatile double pKeyRatio = 1.0; // MASTER TEMPO correction, 1.0 = off
    private volatile float pBalance = 0.5f; // master BALANCE: 0 = hard left, 1 = hard right

    // Peak level of the last block, per side, for the panel meters. Written on the audio thread
    // and read on the client thread; a float write is atomic so no lock is needed.
    private volatile float peakLeft, peakRight;
    private volatile boolean pIsolator = false; // EQ curve: false = -26 dB EQ, true = -inf kill

    /**
     * Set the MASTER TEMPO correction: the pitch ratio that cancels the tempo fader.
     *
     * <p>Separate from the mixer settings because it belongs to the deck, not the channel.
     */
    public void setKeyCorrection(double ratio) {
        this.pKeyRatio = ratio;
    }

    /** Point the whole DSP chain at the mixer's current settings. */
    public void setParams(ChannelSettings cfg) {
        this.pLow = cfg.eqLow();
        this.pMid = cfg.eqMid();
        this.pHigh = cfg.eqHigh();
        this.pFilter = cfg.colour();
        this.pEcho = cfg.echo();
        this.pGain = cfg.trim();
        this.pIsolator = cfg.isolator();
        this.pColorMode = cfg.colourMode();
        this.pColorParam = cfg.colourParam();
        this.pBeatType = cfg.beatType();
        this.pBeatOn = cfg.beatOn();
        this.pBeatSeconds = cfg.beatSeconds();
        this.pBeatDepth = cfg.beatDepth();
        this.pBeatBands = cfg.beatBands();
        this.pBalance = cfg.balance();
    }

    /** Loudest sample of the last block on each side, 0..1, for drawing the channel meters. */
    public float peakLeft() { return peakLeft; }
    public float peakRight() { return peakRight; }

    /** Gain for one audio channel from the BALANCE knob: constant-power, so the centre doesn't
     *  sound louder than either extreme. Channels beyond the first two are left alone. */
    private double balanceGain(int channel) {
        if (channels < 2 || channel > 1) {
            return 1.0;
        }
        double angle = pBalance * (Math.PI / 2.0);
        return channel == 0 ? Math.cos(angle) * Math.sqrt(2) : Math.sin(angle) * Math.sqrt(2);
    }

    @Override
    public long[] supportedChannels() {
        return inner.supportedChannels();
    }

    @Override
    public SampleType[] supportedTypes() {
        return inner.supportedTypes();
    }

    @Override
    public boolean setAudioFormat(SampleType type, int channels, int sampleRate) {
        // Take the flag down first: upload() reads it to decide whether the per-channel arrays
        // below exist, and a format change must never leave it true while they're half-built.
        this.supported = false;
        this.sampleType = type;
        this.channels = channels;
        this.sampleRate = sampleRate;
        boolean canFilter = (type == SampleType.S16 || type == SampleType.FLT) && channels > 0;
        if (canFilter) {
            eq.setup(sampleRate, channels);
            color = new ColorFx[channels];
            beat = new BeatFx[channels];
            keyLock = new PitchShifter[channels];
            delayLen = Math.max(1, (int) (sampleRate * ECHO_SECONDS));
            delay = new float[channels][delayLen];
            delayPos = new int[channels];
            for (int c = 0; c < channels; c++) {
                color[c] = new ColorFx();
                color[c].setup(sampleRate);
                // Odd channels are the right-hand side, which PING PONG offsets against the left.
                beat[c] = new BeatFx(c % 2 == 1);
                beat[c].setup(sampleRate);
                keyLock[c] = new PitchShifter();
                keyLock[c].setup(sampleRate);
            }
            this.supported = true;
        }
        return inner.setAudioFormat(type, channels, sampleRate);
    }

    /** Point every stage at the current knobs. Once per block is enough: they each smooth their
     *  own moves, the EQ inside {@link ChannelEq#advance()} and the rest internally. */
    private void syncStages() {
        eq.setTargets(pLow, pMid, pHigh, pIsolator);
        for (int c = 0; c < channels; c++) {
            color[c].set(pColorMode, pFilter, pColorParam);
            beat[c].set(pBeatType, pBeatOn, pBeatSeconds, pBeatDepth, pBeatBands);
            keyLock[c].setRatio(pKeyRatio);
        }
    }


    @Override
    public boolean upload(ByteBuffer buf) {
        if (!supported || buf == null) {
            return inner.upload(buf);
        }
        // We filter in place, so a read-only buffer would throw on the audio thread and take the
        // deck's sound with it. Nothing observed hands us one, but passing the audio through
        // unfiltered is a far better failure than silence, so check rather than assume.
        if (buf.isReadOnly()) {
            if (!warnedReadOnly) {
                warnedReadOnly = true;
                com.osgworld.djbooth.DJBooth.LOGGER.warn(
                        "Audio buffer is read-only; playing this deck without EQ or effects");
            }
            return inner.upload(buf);
        }
        syncStages();
        float echoMix = pEcho * 0.6f;       // wet level
        float echoFb = pEcho * 0.5f;        // feedback
        double gain = pGain * GAIN_MAX;     // channel trim, 0.5 = unity
        float pkL = 0, pkR = 0;
        int pos = buf.position();
        int lim = buf.limit();
        int frame = 0;
        ByteBuffer v = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (sampleType == SampleType.S16) {
            int frameBytes = 2 * channels;
            for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
                if (frame++ % ChannelEq.CHUNK_FRAMES == 0) { eq.advance(); }
                for (int c = 0; c < channels; c++) {
                    int idx = i + c * 2;
                    double s = gain * balanceGain(c)
                            * filter(c, v.getShort(idx) / 32768.0, echoMix, echoFb);
                    s = clamp(s);
                    if (c == 0) { pkL = Math.max(pkL, (float) Math.abs(s)); }
                    else if (c == 1) { pkR = Math.max(pkR, (float) Math.abs(s)); }
                    v.putShort(idx, (short) Math.round(s * 32767.0));
                }
            }
        } else { // FLT
            int frameBytes = 4 * channels;
            for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
                if (frame++ % ChannelEq.CHUNK_FRAMES == 0) { eq.advance(); }
                for (int c = 0; c < channels; c++) {
                    int idx = i + c * 4;
                    double s = gain * balanceGain(c) * filter(c, v.getFloat(idx), echoMix, echoFb);
                    s = clamp(s);
                    if (c == 0) { pkL = Math.max(pkL, (float) Math.abs(s)); }
                    else if (c == 1) { pkR = Math.max(pkR, (float) Math.abs(s)); }
                    v.putFloat(idx, (float) s);
                }
            }
        }
        // Fall, don't jump, so the meters read like LEDs instead of flickering.
        peakLeft = Math.max(pkL, peakLeft * 0.75f);
        peakRight = Math.max(pkR, peakRight * 0.75f);
        return inner.upload(buf);
    }

    private double filter(int c, double s, float echoMix, float echoFb) {
        s = keyLock[c].process(s);
        s = eq.process(c, s);
        s = color[c].process(s);
        s = beat[c].process(s);
        if (echoMix > 1e-4f) {
            int p = delayPos[c];
            float echoed = delay[c][p];
            double out = s + echoMix * echoed;
            delay[c][p] = (float) (s + echoFb * echoed);
            delayPos[c] = (p + 1) % delayLen;
            s = out;
        }
        return s;
    }

    private static double clamp(double s) {
        return ChannelEq.softClip(s);
    }

    @Override
    protected int genSource() {
        return inner.source(); // never called by FFMediaPlayer; the inner engine owns the AL source
    }

    @Override public void pause() { inner.pause(); }
    @Override public void play() { inner.play(); }
    @Override public void speed(float speed) { inner.speed(speed); }
    @Override public void volume(float volume) { inner.volume(volume); }
    @Override public void flush() {
        if (supported) {
            eq.reset();
            for (int c = 0; c < channels; c++) {
                color[c].reset(); beat[c].reset(); keyLock[c].reset();
                java.util.Arrays.fill(delay[c], 0f);
            }
        }
        inner.flush();
    }
    @Override public long pendingMs() { return inner.pendingMs(); }
    @Override public long playbackMs() { return inner.playbackMs(); }
    @Override public void release() { inner.release(); }
}
