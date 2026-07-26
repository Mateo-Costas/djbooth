package com.osgworld.djbooth.client.audio.dsp;

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
    // The three "EQ" knobs sweep a filter frequency rather than a band gain: each one is fully
    // open (bypassed) at the top of its travel and closes in as it is turned down.
    private static final double LOW_MIN_HZ = 20.0;    // LOW: low-pass, 20 Hz .. 100 Hz, bypass at 1.0
    private static final double LOW_MAX_HZ = 100.0;
    private static final double MID_MIN_HZ = 100.0;   // MID: swept bell, 100 Hz .. 20 kHz
    private static final double MID_MAX_HZ = 20000.0;
    private static final double HIGH_MIN_HZ = 25.0;   // HIGH: low-pass, 25 Hz .. 20 kHz
    private static final double HIGH_MAX_HZ = 20000.0;
    private static final double MID_Q = 1.2;
    private static final double MID_GAIN_DB = 9.0; // bell boost, so the swept mid is audible
    private static final double SWEEP_Q = 0.9;     // resonance of the EQ low-pass sweeps
    private static final double BYPASS_ABOVE = 0.995; // knob position that means "wide open"
    private static final double FILTER_Q = 2.0;    // resonance of the colour sweep
    private static final double ECHO_SECONDS = 0.35; // fixed delay time (matches a slow beat echo)
    private static final double GAIN_MAX = 2.0;    // trim: knob 0.5 = unity, 1.0 = +6 dB

    private final ALEngine inner = ALEngine.buildDefault();

    // Per-channel filter chains: [ch] -> {low shelf, mid peak, high shelf, sweep}.
    private Biquad[] low, mid, high, sweep;
    private boolean supported; // true when the negotiated format is one we filter

    // Per-channel echo delay lines.
    private float[][] delay;
    private int[] delayPos;
    private int delayLen;

    // Knob params (0..1). Written from the client thread, read on the audio thread.
    private volatile float pLow = 1f, pMid = 1f, pHigh = 1f, pFilter = 0.5f, pEcho = 0f, pGain = 0.5f;
    private volatile boolean pIsolator = false; // steeper sweep resonance when true
    // Last params baked into coefficients, so we only recompute when a knob actually moves.
    private float aLow = -1, aMid = -1, aHigh = -1, aFilter = -1;
    private boolean aIsolator;

    /** Update the EQ/filter/echo/trim knobs (0..1). EQ knobs are open at 1.0, the colour filter is
     *  bypassed at 0.5, echo is off at 0 and the trim is unity at 0.5. */
    public void setParams(float lowV, float midV, float highV, float filterV, float echoV,
                          float gainV, boolean isolator) {
        this.pLow = lowV;
        this.pMid = midV;
        this.pHigh = highV;
        this.pFilter = filterV;
        this.pEcho = echoV;
        this.pGain = gainV;
        this.pIsolator = isolator;
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
            sweep = new Biquad[channels];
            delayLen = Math.max(1, (int) (sampleRate * ECHO_SECONDS));
            delay = new float[channels][delayLen];
            delayPos = new int[channels];
            for (int c = 0; c < channels; c++) {
                low[c] = new Biquad();
                mid[c] = new Biquad();
                high[c] = new Biquad();
                sweep[c] = new Biquad();
            }
            aLow = aMid = aHigh = aFilter = -1; // force a rebake
        }
        return inner.setAudioFormat(type, channels, sampleRate);
    }

    /** Rebake coefficients if a knob (or the isolator mode) moved since the last block. */
    private void rebakeIfNeeded() {
        float l = pLow, m = pMid, h = pHigh, f = pFilter;
        boolean iso = pIsolator;
        if (l == aLow && m == aMid && h == aHigh && f == aFilter && iso == aIsolator) {
            return;
        }
        double fs = sampleRate;
        double nyq = fs / 2.0;
        double q = iso ? SWEEP_Q * 2.0 : SWEEP_Q; // isolator mode = sharper, more resonant sweeps
        for (int c = 0; c < channels; c++) {
            configureLowPass(low[c], fs, nyq, l, LOW_MIN_HZ, LOW_MAX_HZ, q);
            configureBell(mid[c], fs, nyq, m);
            configureLowPass(high[c], fs, nyq, h, HIGH_MIN_HZ, HIGH_MAX_HZ, q);
            configureSweep(sweep[c], fs, f);
        }
        aLow = l; aMid = m; aHigh = h; aFilter = f; aIsolator = iso;
    }

    /** Map a knob logarithmically onto {@code [minHz, maxHz]} so the sweep sounds even. */
    private static double sweepHz(double v, double minHz, double maxHz) {
        return minHz * Math.pow(maxHz / minHz, clamp01(v));
    }

    /** A knob that opens up as it is turned clockwise: bypassed at the top, closing in below it. */
    private static void configureLowPass(Biquad bq, double fs, double nyq, double v,
                                         double minHz, double maxHz, double q) {
        if (v >= BYPASS_ABOVE) {
            bq.identity();
            return;
        }
        double cutoff = sweepHz(v / BYPASS_ABOVE, minHz, maxHz);
        bq.lowpass(fs, Math.min(cutoff, nyq * 0.98), q);
    }

    /** MID knob: a bell whose centre frequency sweeps 100 Hz .. 20 kHz (inaudible, so flat, at the top). */
    private static void configureBell(Biquad bq, double fs, double nyq, double v) {
        if (v >= BYPASS_ABOVE) {
            bq.identity();
            return;
        }
        double centre = sweepHz(v / BYPASS_ABOVE, MID_MIN_HZ, MID_MAX_HZ);
        bq.peaking(fs, Math.min(centre, nyq * 0.98), MID_GAIN_DB, MID_Q);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Colour knob: centre = bypass, left = low-pass sweep down, right = high-pass sweep up. */
    private static void configureSweep(Biquad bq, double fs, double v) {
        double nyq = fs / 2.0;
        if (v < 0.48) {
            double t = (0.48 - v) / 0.48;
            double cutoff = 18000.0 * Math.pow(200.0 / 18000.0, t);
            bq.lowpass(fs, Math.min(cutoff, nyq * 0.98), FILTER_Q);
        } else if (v > 0.52) {
            double t = (v - 0.52) / 0.48;
            double cutoff = 20.0 * Math.pow(4000.0 / 20.0, t);
            bq.highpass(fs, Math.min(cutoff, nyq * 0.98), FILTER_Q);
        } else {
            bq.identity();
        }
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
        int pos = buf.position();
        int lim = buf.limit();
        ByteBuffer v = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (sampleType == SampleType.S16) {
            int frameBytes = 2 * channels;
            for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
                for (int c = 0; c < channels; c++) {
                    int idx = i + c * 2;
                    double s = gain * filter(c, v.getShort(idx) / 32768.0, echoMix, echoFb);
                    v.putShort(idx, (short) Math.round(clamp(s) * 32767.0));
                }
            }
        } else { // FLT
            int frameBytes = 4 * channels;
            for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
                for (int c = 0; c < channels; c++) {
                    int idx = i + c * 4;
                    double s = gain * filter(c, v.getFloat(idx), echoMix, echoFb);
                    v.putFloat(idx, (float) clamp(s));
                }
            }
        }
        return inner.upload(buf);
    }

    private double filter(int c, double s, float echoMix, float echoFb) {
        s = low[c].process(s);
        s = mid[c].process(s);
        s = high[c].process(s);
        s = sweep[c].process(s);
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
                low[c].reset(); mid[c].reset(); high[c].reset(); sweep[c].reset();
                java.util.Arrays.fill(delay[c], 0f);
            }
        }
        inner.flush();
    }
    @Override public long pendingMs() { return inner.pendingMs(); }
    @Override public long playbackMs() { return inner.playbackMs(); }
    @Override public void release() { inner.release(); }
}
