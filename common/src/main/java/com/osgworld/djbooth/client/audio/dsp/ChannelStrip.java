package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.ChannelSettings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One deck's whole signal path, run over interleaved PCM, in the order the DJM-900NXS2 puts it:
 * trim, key lock, EQ, SOUND COLOR FX, <em>the channel meter</em>, <em>the channel fader</em>,
 * BEAT FX, echo, balance and the output limiter.
 *
 * <p>The fader sits between the meter and the effects because that is where the manual puts it:
 * the level indicator shows the signal "before passing through the channel faders", and the BEAT FX
 * section warns that lowering the fader leaves the effect's repeats ringing. Applied after the
 * effects instead (as OpenAL's volume would), closing the fader would cut the tail off with it,
 * and the echo-out that effect exists for would be impossible.
 *
 * <p>Lives apart from {@link DspSfxEngine} for the same reason as {@link ChannelEq}: that class
 * extends a WaterMedia type and cannot be loaded in a test, so anything inside it can only be
 * checked by ear. Everything here is plain Java, which is what lets the assembled chain be measured
 * at all.
 *
 * <p>Threading: {@link #setSettings} and {@link #setKeyCorrection} are called from the client
 * thread; everything else runs on the audio thread.
 */
public final class ChannelStrip {
    private final ChannelEq eq = new ChannelEq();
    // One limiter for the whole strip, not one per channel: see the frame loop in processInPlace.
    private final Limiter limiter = new Limiter();

    private double[] work = new double[2]; // one frame, held between the two passes of the loop
    private ColorFx[] color = new ColorFx[0];
    private ChannelEcho[] echo = new ChannelEcho[0];
    private BeatFx[] beat = new BeatFx[0];
    private PitchShifter[] keyLock = new PitchShifter[0];

    private int channels;
    private boolean s16;
    private boolean ready;

    // The channel fader's gain, chased per sample. It only arrives once a tick, and a step in a
    // gain is a click, so it glides over about 7 ms. The first block snaps rather than fading in.
    private double faderNow = 1.0;
    private double faderSmooth = 0.003;
    private boolean faderPrimed;

    private volatile ChannelSettings settings = ChannelSettings.flat();
    private volatile double keyRatio = 1.0; // MASTER TEMPO + KEY SYNC pitch correction, 1.0 = off

    // Peak of the last block per side, for the panel meters. A float write is atomic.
    private volatile float peakLeft, peakRight;
    private float pkL, pkR; // the running peaks of the block being processed

    /** Build one chain per audio channel for a new format. */
    public void setup(int sampleRate, int channelCount, boolean signed16) {
        ready = false; // never leave the flag up while the arrays below are half-built
        if (channelCount <= 0) {
            return;
        }
        this.channels = channelCount;
        this.s16 = signed16;
        eq.setup(sampleRate, channelCount);
        limiter.setup(sampleRate);
        faderSmooth = 1.0 - Math.exp(-1.0 / (0.007 * Math.max(1, sampleRate)));
        faderPrimed = false;
        work = new double[channelCount];
        color = new ColorFx[channelCount];
        echo = new ChannelEcho[channelCount];
        beat = new BeatFx[channelCount];
        keyLock = new PitchShifter[channelCount];
        for (int c = 0; c < channelCount; c++) {
            color[c] = new ColorFx();
            color[c].setup(sampleRate);
            echo[c] = new ChannelEcho();
            echo[c].setup(sampleRate);
            // Odd channels are the right-hand side, which PING PONG offsets against the left.
            beat[c] = new BeatFx(c % 2 == 1);
            beat[c].setup(sampleRate);
            keyLock[c] = new PitchShifter();
            keyLock[c].setup(sampleRate);
        }
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }

    /** Point the whole chain at the mixer's current settings for this channel. */
    public void setSettings(ChannelSettings cfg) {
        this.settings = cfg;
    }

    /**
     * The pitch ratio that cancels the tempo fader (MASTER TEMPO) and applies KEY SYNC. Separate
     * from the mixer settings because it belongs to the deck, not the channel.
     */
    public void setKeyCorrection(double ratio) {
        this.keyRatio = ratio;
    }

    /**
     * Loudest sample of the last block on each side, for drawing the channel meters. Read before
     * the channel fader, as the hardware's indicator is, so it does not move when the fader does.
     * It can exceed 1.0: that is a channel being driven hot, which is what the meter is for.
     */
    public float peakLeft() { return peakLeft; }
    public float peakRight() { return peakRight; }

    /** Drop every filter state and tail, for a seek: old audio must not ring into the new spot. */
    public void reset() {
        if (!ready) {
            return;
        }
        eq.reset();
        limiter.reset();
        for (int c = 0; c < channels; c++) {
            color[c].reset();
            beat[c].reset();
            keyLock[c].reset();
            echo[c].reset();
        }
    }

    /** Gain for one audio channel from the BALANCE knob: constant-power, so the centre doesn't
     *  sound louder than either extreme. Channels beyond the first two are left alone. */
    private double balanceGain(int channel, float balance) {
        if (channels < 2 || channel > 1) {
            return 1.0;
        }
        double angle = balance * (Math.PI / 2.0);
        return channel == 0 ? Math.cos(angle) * Math.sqrt(2) : Math.sin(angle) * Math.sqrt(2);
    }

    /** Point every stage at the current knobs. Once per block is enough: they each smooth their
     *  own moves, the EQ inside {@link ChannelEq#advance()} and the rest internally. */
    private void syncStages(ChannelSettings cfg) {
        eq.setTargets(cfg.eqLow(), cfg.eqMid(), cfg.eqHigh(), cfg.isolator());
        for (int c = 0; c < channels; c++) {
            color[c].set(cfg.colourMode(), cfg.colour(), cfg.colourParam());
            beat[c].set(cfg.beatType(), cfg.beatOn(), cfg.beatSeconds(), cfg.beatDepth(),
                    cfg.beatBands());
            keyLock[c].setRatio(keyRatio);
        }
    }

    /**
     * Run a block of interleaved PCM through the chain, overwriting it.
     *
     * <p>Every call advances the filters, delay lines and limiter, so a block must only ever be
     * passed through once: that is {@link BlockRelay}'s job. Frames are read and written
     * little-endian from the buffer's position to its limit; a trailing partial frame is left alone.
     */
    public void processInPlace(ByteBuffer buf) {
        if (!ready) {
            return;
        }
        ChannelSettings cfg = settings;
        syncStages(cfg);
        double faderTarget = com.osgworld.djbooth.mixer.MixLevels.clamp01(cfg.gain());
        if (!faderPrimed) {
            faderNow = faderTarget;
            faderPrimed = true;
        }
        double echoKnob = cfg.echo();
        // TRIM before the EQ, as on the hardware: it sets how hard the bands are driven.
        double trim = ChannelEq.trimGain(cfg.trim());
        pkL = 0;
        pkR = 0;
        int pos = buf.position();
        int lim = buf.limit();
        int frame = 0;
        ByteBuffer v = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int bytes = s16 ? 2 : 4;
        int frameBytes = bytes * channels;
        for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
            if (frame++ % ChannelEq.CHUNK_FRAMES == 0) {
                eq.advance();
            }

            // Run the whole frame first and note how loud it is, because the limiter has to act on
            // every channel by the same amount. Ducking one side more than the other would drag
            // the stereo image sideways whenever a peak came through.
            faderNow += (faderTarget - faderNow) * faderSmooth;
            double framePeak = 0;
            for (int c = 0; c < channels; c++) {
                int idx = i + c * bytes;
                double in = s16 ? v.getShort(idx) / 32768.0 : v.getFloat(idx);
                double s = balanceGain(c, cfg.balance()) * filter(c, trim * in, echoKnob);
                work[c] = s;
                framePeak = Math.max(framePeak, Math.abs(s));
            }

            double g = limiter.gainFor(framePeak);
            for (int c = 0; c < channels; c++) {
                double s = Limiter.ceiling(work[c] * g);
                int idx = i + c * bytes;
                if (s16) {
                    v.putShort(idx, (short) Math.round(s * 32767.0));
                } else {
                    v.putFloat(idx, (float) s);
                }
            }
        }
        // Fall, don't jump, so the meters read like LEDs instead of flickering.
        peakLeft = Math.max(pkL, peakLeft * 0.75f);
        peakRight = Math.max(pkR, peakRight * 0.75f);
    }

    private double filter(int c, double s, double echoKnob) {
        s = keyLock[c].process(s);
        s = eq.process(c, s);
        s = color[c].process(s);
        // The channel meter taps here: after the tone controls, before the fader.
        if (c == 0) { pkL = Math.max(pkL, (float) Math.abs(s)); }
        else if (c == 1) { pkR = Math.max(pkR, (float) Math.abs(s)); }
        s *= faderNow;
        s = beat[c].process(s);
        return echo[c].process(s, echoKnob);
    }
}
