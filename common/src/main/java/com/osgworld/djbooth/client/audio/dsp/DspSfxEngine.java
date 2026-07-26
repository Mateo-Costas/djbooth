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
    // Channel EQ band split, matching how a DJM-900NXS2 behaves: LOW below 200 Hz, MID in
    // between, HI above 2 kHz. Pioneer doesn't publish the exact corners, so these are the
    // figures the manual and measurements point at.
    private static final double F_LOW = 200.0;
    private static final double F_HIGH = 2000.0;
    private static final double F_MID = Math.sqrt(F_LOW * F_HIGH); // bell sits between the shelves
    private static final double MID_Q = 0.9;
    private static final double EQ_BOOST_DB = 6.0;  // printed on the panel: +6 at the top
    private static final double EQ_CUT_DB = 26.0;   // ... and -26 at the bottom in EQ mode
    private static final double ISO_CUT_DB = 60.0;  // ISOLATOR mode kills the band instead (-inf)
    private static final double ECHO_SECONDS = 0.35; // fixed delay time (matches a slow beat echo)
    private static final double GAIN_MAX = 2.0;    // trim: knob 0.5 = unity, 1.0 = +6 dB

    private final ALEngine inner = ALEngine.buildDefault();

    // Per-channel filter chains: [ch] -> {low shelf, mid peak, high shelf} + a COLOR FX stage.
    private Biquad[] low, mid, high;
    private ColorFx[] color;
    private BeatFx[] beat;
    private boolean supported; // true when the negotiated format is one we filter

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
    private volatile float pBalance = 0.5f; // master BALANCE: 0 = hard left, 1 = hard right

    // Peak level of the last block, per side, for the panel meters. Written on the audio thread
    // and read on the client thread; a float write is atomic so no lock is needed.
    private volatile float peakLeft, peakRight;
    private volatile boolean pIsolator = false; // EQ curve: false = -26 dB EQ, true = -inf kill
    // Last params baked into coefficients, so we only recompute when a knob actually moves.
    private float aLow = -1, aMid = -1, aHigh = -1;
    private boolean aIsolator;

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
        this.sampleType = type;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.supported = (type == SampleType.S16 || type == SampleType.FLT) && channels > 0;
        if (supported) {
            low = new Biquad[channels];
            mid = new Biquad[channels];
            high = new Biquad[channels];
            color = new ColorFx[channels];
            beat = new BeatFx[channels];
            delayLen = Math.max(1, (int) (sampleRate * ECHO_SECONDS));
            delay = new float[channels][delayLen];
            delayPos = new int[channels];
            for (int c = 0; c < channels; c++) {
                low[c] = new Biquad();
                mid[c] = new Biquad();
                high[c] = new Biquad();
                color[c] = new ColorFx();
                color[c].setup(sampleRate);
                // Odd channels are the right-hand side, which PING PONG offsets against the left.
                beat[c] = new BeatFx(c % 2 == 1);
                beat[c].setup(sampleRate);
            }
            aLow = aMid = aHigh = -1; // force a rebake
        }
        return inner.setAudioFormat(type, channels, sampleRate);
    }

    /** Rebake coefficients if a knob (or the isolator mode) moved since the last block. The COLOR
     *  stage tracks its own knobs, so it only needs pointing at the current values. */
    private void rebakeIfNeeded() {
        for (int c = 0; c < channels; c++) {
            color[c].set(pColorMode, pFilter, pColorParam);
            beat[c].set(pBeatType, pBeatOn, pBeatSeconds, pBeatDepth, pBeatBands);
        }
        float l = pLow, m = pMid, h = pHigh;
        boolean iso = pIsolator;
        if (l == aLow && m == aMid && h == aHigh && iso == aIsolator) {
            return;
        }
        double fs = sampleRate;
        for (int c = 0; c < channels; c++) {
            low[c].lowShelf(fs, F_LOW, dbForBand(l, iso));
            mid[c].peaking(fs, F_MID, dbForBand(m, iso), MID_Q);
            high[c].highShelf(fs, F_HIGH, dbForBand(h, iso));
        }
        aLow = l; aMid = m; aHigh = h; aIsolator = iso;
    }

    /** Knob 0..1 -&gt; band gain in dB, as printed on the DJM-900NXS2: centre is flat, the top of the
     *  travel is +6 dB, and the bottom is -26 dB in EQ mode or a kill in ISOLATOR mode. */
    private static double dbForBand(double v, boolean isolator) {
        if (v >= 0.5) {
            return (v - 0.5) / 0.5 * EQ_BOOST_DB;
        }
        return (v / 0.5 - 1.0) * (isolator ? ISO_CUT_DB : EQ_CUT_DB);
    }


    @Override
    public boolean upload(ByteBuffer buf) {
        if (!supported || buf == null) {
            return inner.upload(buf);
        }
        rebakeIfNeeded();
        float echoMix = pEcho * 0.6f;       // wet level
        float echoFb = pEcho * 0.5f;        // feedback
        double gain = pGain * GAIN_MAX;     // channel trim, 0.5 = unity
        float pkL = 0, pkR = 0;
        int pos = buf.position();
        int lim = buf.limit();
        ByteBuffer v = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (sampleType == SampleType.S16) {
            int frameBytes = 2 * channels;
            for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
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
        s = low[c].process(s);
        s = mid[c].process(s);
        s = high[c].process(s);
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
        return s > 1.0 ? 1.0 : (s < -1.0 ? -1.0 : s);
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
            for (int c = 0; c < channels; c++) {
                low[c].reset(); mid[c].reset(); high[c].reset();
                color[c].reset(); beat[c].reset();
                java.util.Arrays.fill(delay[c], 0f);
            }
        }
        inner.flush();
    }
    @Override public long pendingMs() { return inner.pendingMs(); }
    @Override public long playbackMs() { return inner.playbackMs(); }
    @Override public void release() { inner.release(); }
}
