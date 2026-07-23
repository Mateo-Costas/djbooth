package com.osgworld.djbooth.client.audio.dsp;

import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.engines.SFXEngine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A {@link SFXEngine} that inserts a DJ EQ + colour filter into WaterMedia's audio path. It wraps
 * a real {@link ALEngine} (OpenAL) and forwards every call to it, except {@link #upload(ByteBuffer)}:
 * there it intercepts the decoded PCM, runs a 3-band shelving/peaking EQ and a resonant sweep filter
 * over each channel in Java, then hands the processed PCM to the inner engine to play.
 *
 * <p>This is why real EQ/filter is possible without any native DSP: {@code FFMediaPlayer} only ever
 * talks to the {@code SFXEngine} interface, so a custom engine can transform the audio in between.
 * Handles S16 and FLT sample formats (what FFmpeg negotiates in practice); any other format is passed
 * through untouched so nothing breaks.
 */
public final class DspSfxEngine extends SFXEngine {
    // Fixed EQ corner frequencies (Hz) — typical DJ isolator bands.
    private static final double F_LOW = 200.0;
    private static final double F_MID = 1000.0;
    private static final double F_HIGH = 4000.0;
    private static final double MID_Q = 0.9;
    private static final double FILTER_Q = 2.0; // resonance of the colour sweep

    private final ALEngine inner = ALEngine.buildDefault();

    // Per-channel filter chains: [ch] -> {low shelf, mid peak, high shelf, sweep}.
    private Biquad[] low, mid, high, sweep;
    private boolean supported; // true when the negotiated format is one we filter

    // Knob params (0..1), 0.5 = neutral. Written from the client thread, read on the audio thread.
    private volatile float pLow = 0.5f, pMid = 0.5f, pHigh = 0.5f, pFilter = 0.5f;
    // Last params baked into coefficients, so we only recompute when a knob actually moves.
    private float aLow = -1, aMid = -1, aHigh = -1, aFilter = -1;

    /** Update the EQ/filter knobs (0..1, 0.5 = flat). Cheap; coefficients rebake lazily. */
    public void setParams(float lowV, float midV, float highV, float filterV) {
        this.pLow = lowV;
        this.pMid = midV;
        this.pHigh = highV;
        this.pFilter = filterV;
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

    /** Rebake coefficients if a knob moved since the last block. */
    private void rebakeIfNeeded() {
        float l = pLow, m = pMid, h = pHigh, f = pFilter;
        if (l == aLow && m == aMid && h == aHigh && f == aFilter) {
            return;
        }
        double fs = sampleRate;
        for (int c = 0; c < channels; c++) {
            low[c].lowShelf(fs, F_LOW, dbForBand(l));
            mid[c].peaking(fs, F_MID, dbForBand(m), MID_Q);
            high[c].highShelf(fs, F_HIGH, dbForBand(h));
            configureSweep(sweep[c], fs, f);
        }
        aLow = l; aMid = m; aHigh = h; aFilter = f;
    }

    /** Knob 0..1 -> gain in dB. 0.5 = flat; up to +8 dB boost, down toward -40 dB (kill). */
    private static double dbForBand(double v) {
        if (v >= 0.5) {
            return (v - 0.5) / 0.5 * 8.0;
        }
        return (v / 0.5 - 1.0) * 40.0;
    }

    /** Colour knob: centre = bypass, left = low-pass sweep down, right = high-pass sweep up. */
    private static void configureSweep(Biquad bq, double fs, double v) {
        double nyq = fs / 2.0;
        if (v < 0.48) {
            double t = (0.48 - v) / 0.48;              // 0..1 as knob turns left
            double cutoff = 18000.0 * Math.pow(200.0 / 18000.0, t);
            bq.lowpass(fs, Math.min(cutoff, nyq * 0.98), FILTER_Q);
        } else if (v > 0.52) {
            double t = (v - 0.52) / 0.48;              // 0..1 as knob turns right
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
        int pos = buf.position();
        int lim = buf.limit();
        // Absolute-indexed view so we don't disturb the buffer's own position for the inner engine.
        ByteBuffer v = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (sampleType == SampleType.S16) {
            int frameBytes = 2 * channels;
            for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
                for (int c = 0; c < channels; c++) {
                    int idx = i + c * 2;
                    double s = v.getShort(idx) / 32768.0;
                    s = filter(c, s);
                    int q = (int) Math.round(clamp(s) * 32767.0);
                    v.putShort(idx, (short) q);
                }
            }
        } else { // FLT
            int frameBytes = 4 * channels;
            for (int i = pos; i + frameBytes <= lim; i += frameBytes) {
                for (int c = 0; c < channels; c++) {
                    int idx = i + c * 4;
                    double s = filter(c, v.getFloat(idx));
                    v.putFloat(idx, (float) clamp(s));
                }
            }
        }
        return inner.upload(buf);
    }

    private double filter(int c, double s) {
        s = low[c].process(s);
        s = mid[c].process(s);
        s = high[c].process(s);
        s = sweep[c].process(s);
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
            }
        }
        inner.flush();
    }
    @Override public long pendingMs() { return inner.pendingMs(); }
    @Override public long playbackMs() { return inner.playbackMs(); }
    @Override public void release() { inner.release(); }
}
