package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.BeatFxTypes;

/**
 * One channel's BEAT FX stage: the tempo-locked effect on the right of a DJM-900NXS2.
 *
 * <p>Everything here is driven by one number — the effect time, which the mixer works out from the
 * BPM and the selected beat fraction — so switching from 1/4 to 1 beat retimes the effect rather
 * than restarting it. The LEVEL/DEPTH knob scales how much you hear, and turning it fully down
 * leaves the dry signal alone whatever the effect is, exactly as the manual describes.
 *
 * <p>The effects fall into four families, which is why they share so much machinery:
 * <ul>
 *   <li><b>Delay</b> — DELAY, ECHO, PING PONG, SPIRAL, and the recorded loops (SLIP ROLL, ROLL,
 *       HELIX) all read a delay line; they differ in feedback, stereo offset and read speed.</li>
 *   <li><b>Modulated</b> — FLANGER, PHASER and FILTER sweep something with an LFO whose cycle is
 *       one effect time.</li>
 *   <li><b>Gated</b> — TRANS chops the signal on the same LFO.</li>
 *   <li><b>Rate</b> — PITCH and VINYL BRAKE resample, the latter ramping to a standstill.</li>
 * </ul>
 *
 * <p>Instances are per audio channel and only touched from the audio thread.
 */
public final class BeatFx {
    /**
     * Longest effect time the panel can ask for: the slowest tempo the mixer accepts (40 BPM)
     * against the widest beat fraction (4 beats). Anything shorter than this and the beat buttons
     * would quietly stop matching the beat at low tempos.
     */
    private static final double MAX_SECONDS = 6.0;
    /** Headroom on the buffer so a tap at exactly MAX_SECONDS doesn't land on the wrap point,
     *  where it would read the sample just written and collapse to no delay at all. */
    private static final double BUFFER_MARGIN_SECONDS = 0.1;
    private static final int ALLPASS_STAGES = 4;     // phaser depth
    private static final double FLANGER_MS = 6.0;    // flanger sweep range
    private static final double BRAKE_SECONDS = 1.6; // how long VINYL BRAKE takes to stop

    private float[] line = new float[1];  // main delay / recording buffer
    private int writePos;
    private double readPos;               // fractional, so the rate effects can resample

    private final Biquad lfoFilter = new Biquad();          // FILTER sweep
    private final Allpass1[] phase = new Allpass1[ALLPASS_STAGES]; // PHASER
    private int sincePhaserBake; // samples since the phaser's corner was last recomputed
    private final Biquad bandLow = new Biquad();            // FX FREQUENCY splits
    private final Biquad bandHigh = new Biquad();

    private double fs = 48000;
    private double lfoPhase;              // 0..1 across one effect time
    private double brake = 1.0;           // VINYL BRAKE playback rate, ramping to 0
    private boolean recording;            // SLIP ROLL / ROLL / HELIX capture window
    private int recorded;                 // samples captured so far

    private int type = BeatFxTypes.DELAY;
    private boolean on;
    private double timeSeconds = 0.5;
    private double depth;                 // LEVEL/DEPTH, 0..1
    private int bands = BeatFxTypes.BANDS_ALL;
    private boolean rightChannel;         // PING PONG needs to know which side it is

    public BeatFx(boolean rightChannel) {
        this.rightChannel = rightChannel;
        for (int i = 0; i < ALLPASS_STAGES; i++) {
            phase[i] = new Allpass1();
        }
    }

    /** (Re)allocate for a sample rate. Call whenever the audio format changes. */
    public void setup(double sampleRate) {
        this.fs = sampleRate;
        line = new float[Math.max(1, (int) (sampleRate * (MAX_SECONDS + BUFFER_MARGIN_SECONDS)))];
        writePos = 0;
        readPos = 0;
        bandLow.lowpass(sampleRate, 200.0, 0.7);
        bandHigh.highpass(sampleRate, 2000.0, 0.7);
        reset();
    }

    /**
     * Point the stage at the current panel settings.
     *
     * @param newType     effect index from {@link BeatFxTypes}
     * @param newOn       whether ON/OFF is lit
     * @param seconds     effect time, already derived from BPM and the beat fraction
     * @param newDepth    LEVEL/DEPTH, 0..1
     * @param newBands    FX FREQUENCY mask
     */
    public void set(int newType, boolean newOn, double seconds, double newDepth, int newBands) {
        if (newOn && !on) {
            // Switching on arms the effects that capture a slice of audio, and resets the ones
            // that have to start from a known place (the brake, the LFO phase).
            recording = isRecorder(newType);
            recorded = 0;
            lfoPhase = 0;
            brake = 1.0;
            readPos = writePos;
        } else if (!newOn && on) {
            recording = false;
        }
        if (newType != type) {
            recording = newOn && isRecorder(newType);
            recorded = 0;
            brake = 1.0;
        }
        this.type = newType;
        this.on = newOn;
        this.timeSeconds = Math.max(0.005, Math.min(MAX_SECONDS, seconds));
        this.depth = Math.max(0.0, Math.min(1.0, newDepth));
        this.bands = newBands;
    }

    private static boolean isRecorder(int t) {
        return t == BeatFxTypes.SLIP_ROLL || t == BeatFxTypes.ROLL || t == BeatFxTypes.HELIX;
    }

    /** Run one sample through the stage. */
    public double process(double s) {
        if (!on || depth <= 0.0) {
            // Still write to the line so switching on mid-track has history behind it.
            push(s);
            advanceLfo();
            return s;
        }
        if (sincePhaserBake-- <= 0) {
            sincePhaserBake = ParamRamp.CHUNK_FRAMES - 1;
            bakePhaser();
        }
        // FX FREQUENCY: only the enabled bands are sent into the effect, the rest stay dry.
        double lo = bandLow.process(s);
        double hi = bandHigh.process(s);
        double mid = s - lo - hi;
        double send = 0, dry = 0;
        if ((bands & BeatFxTypes.BAND_LOW) != 0) { send += lo; } else { dry += lo; }
        if ((bands & BeatFxTypes.BAND_MID) != 0) { send += mid; } else { dry += mid; }
        if ((bands & BeatFxTypes.BAND_HI) != 0) { send += hi; } else { dry += hi; }

        double wet = effect(send);
        advanceLfo();
        return dry + send + depth * (wet - send);
    }

    private double effect(double s) {
        return switch (type) {
            case BeatFxTypes.DELAY -> singleTap(s, timeSeconds, 0.0);
            case BeatFxTypes.ECHO -> singleTap(s, timeSeconds, 0.45 + 0.35 * depth);
            // One side is half an effect time behind the other, which is what makes it bounce.
            case BeatFxTypes.PING_PONG ->
                    singleTap(s, rightChannel ? timeSeconds * 1.5 : timeSeconds, 0.4 + 0.3 * depth);
            case BeatFxTypes.SPIRAL -> spiral(s);
            case BeatFxTypes.REVERB -> reverb(s);
            case BeatFxTypes.TRANS -> trans(s);
            case BeatFxTypes.FILTER -> sweptFilter(s);
            case BeatFxTypes.FLANGER -> flanger(s);
            case BeatFxTypes.PHASER -> phaser(s);
            case BeatFxTypes.PITCH -> resample(s, pitchRate());
            case BeatFxTypes.SLIP_ROLL, BeatFxTypes.ROLL -> roll(s);
            case BeatFxTypes.VINYL_BRAKE -> vinylBrake(s);
            case BeatFxTypes.HELIX -> helix(s);
            default -> s;
        };
    }

    // --- delay-line helpers ---------------------------------------------------------------

    private void push(double s) {
        line[writePos] = (float) s;
        writePos = (writePos + 1) % line.length;
    }

    /**
     * Read the line {@code seconds} back, interpolating so modulated taps don't click.
     *
     * <p>Clamped to what the line actually holds. Some effects scale the effect time — PING PONG
     * offsets one side by half again, SPIRAL drifts its tap as it decays — so a request can reach
     * past the end even when the effect time itself fits. Asking for more than the line holds
     * would wrap around and read a completely different, much shorter delay.
     */
    private double tap(double seconds) {
        double maxSeconds = (line.length - 2) / fs;
        double back = Math.min(seconds, maxSeconds) * fs;
        double p = writePos - back;
        while (p < 0) {
            p += line.length;
        }
        int i0 = (int) p;
        int i1 = (i0 + 1) % line.length;
        double frac = p - i0;
        return line[i0 % line.length] * (1 - frac) + line[i1] * frac;
    }

    /**
     * One delay tap with feedback.
     *
     * <p>The input is scaled by {@code 1 - feedback} on the way into the line. Without that, a loop
     * that returns a fraction {@code g} of itself settles at {@code 1 / (1 - g)} times its input:
     * ECHO's 0.8 meant a steady tone came back five times louder than it went in, straight into the
     * limiter. Scaling the input makes the repeats sum to unity instead, so the tail is as long as
     * before and the level is the one the DJ set.
     */
    private double singleTap(double s, double seconds, double feedback) {
        double delayed = tap(seconds);
        push(s * (1.0 - feedback) + delayed * feedback);
        return delayed;
    }

    private void advanceLfo() {
        lfoPhase += 1.0 / (timeSeconds * fs);
        if (lfoPhase >= 1.0) {
            lfoPhase -= 1.0;
        }
    }

    // --- the effects ----------------------------------------------------------------------

    /** SPIRAL: an echo whose repeats drift in pitch, because the tap slides as it decays. */
    private double spiral(double s) {
        double drift = 1.0 + 0.35 * depth * lfoPhase; // tap slowly lengthens across the cycle
        double feedback = 0.5 + 0.35 * depth;
        double delayed = tap(timeSeconds * drift);
        push(s * (1.0 - feedback) + delayed * feedback); // see singleTap for why the input is scaled
        return delayed;
    }

    /** REVERB built from the same line: a handful of taps at incommensurate spacings. */
    private double reverb(double s) {
        double size = Math.min(0.12, timeSeconds * 0.25);
        double sum = tap(size) * 0.8 + tap(size * 1.37) * 0.6
                + tap(size * 1.93) * 0.45 + tap(size * 2.51) * 0.3;
        sum *= 0.45;
        push(s + sum * (0.35 + 0.4 * depth));
        return sum;
    }

    /** TRANS: chop the signal on and off once per effect time. LEVEL/DEPTH sets the duty. */
    private double trans(double s) {
        push(s);
        double duty = 0.5 + 0.4 * (1.0 - depth); // deeper = shorter "on" window
        if (lfoPhase < duty) {
            return s;
        }
        // Short ramp at the edges so the gate doesn't click.
        double edge = Math.min(0.05, duty * 0.5);
        double past = lfoPhase - duty;
        return past < edge ? s * (1.0 - past / edge) : 0.0;
    }

    /** FILTER: cutoff rides an LFO across the effect time. */
    private double sweptFilter(double s) {
        push(s);
        double t = 0.5 - 0.5 * Math.cos(2 * Math.PI * lfoPhase); // 0..1..0
        double cutoff = 150.0 * Math.pow(12000.0 / 150.0, t);
        // A resonant low-pass peaks at roughly Q times its input. Q ran to 3.9 here, so a sweep
        // across a bassline came back four times louder than it went in. Capped near +6 dB, which
        // still whistles the way the effect is supposed to.
        lfoFilter.lowpass(fs, Math.min(cutoff, fs * 0.49), 0.9 + depth * 1.1);
        return lfoFilter.process(s);
    }

    /** FLANGER: a very short delay swept across one cycle, mixed back with the dry send. */
    private double flanger(double s) {
        double t = 0.5 - 0.5 * Math.cos(2 * Math.PI * lfoPhase);
        double delaySec = (0.5 + t * FLANGER_MS) / 1000.0;
        double delayed = tap(delaySec);
        push(s + delayed * 0.6 * depth);
        return (s + delayed) * 0.7;
    }

    /**
     * PHASER: a chain of allpasses whose corner sweeps, notching the spectrum as it moves.
     *
     * <p>A phaser only works if each stage passes every frequency at the same level and only moves
     * its phase - that is what makes the sum with the dry signal notch rather than colour. This was
     * built out of low-passes turned into allpasses by {@code 2*lp - y}, which is only an allpass
     * when the low-pass is exactly one pole; with a biquad at Q 0.7 it overshoots, and four stages
     * compounded that into 4.35x the input. A real first-order allpass has unity magnitude by
     * construction, so the chain cannot add level however many stages it has.
     *
     * <p>The coefficient was also recomputed every sample - four sets of trigonometry per sample
     * per channel, for an LFO that takes a whole beat to cross its range.
     */
    private double phaser(double s) {
        push(s);
        double y = s;
        for (Allpass1 stage : phase) {
            y = stage.process(y);
        }
        return (s + y) * 0.7;
    }

    /** Update the phaser's corner from the LFO. Called on the ramp cadence, not per sample. */
    private void bakePhaser() {
        double t = 0.5 - 0.5 * Math.cos(2 * Math.PI * lfoPhase);
        double corner = Math.min(200.0 * Math.pow(6000.0 / 200.0, t), fs * 0.49);
        double coeff = Allpass1.coeffFor(fs, corner);
        for (Allpass1 stage : phase) {
            stage.setCoeff(coeff);
        }
    }

    /**
     * A one-pole allpass: unity magnitude at every frequency, phase rotating through 180 degrees
     * around its corner. The building block a phaser is actually made of.
     */
    private static final class Allpass1 {
        private double a;
        private double x1, y1;

        static double coeffFor(double fs, double cornerHz) {
            double t = Math.tan(Math.PI * cornerHz / fs);
            return (t - 1.0) / (t + 1.0);
        }

        void setCoeff(double coeff) {
            this.a = coeff;
        }

        double process(double x) {
            double y = a * x + x1 - a * y1;
            x1 = x;
            y1 = y;
            return y;
        }

        void reset() {
            x1 = 0;
            y1 = 0;
        }
    }

    /** PITCH: read the line faster or slower than it is written. */
    private double pitchRate() {
        // LEVEL/DEPTH is the pitch: centre-ish is unity, fully up is an octave.
        return 0.5 + depth * 1.5;
    }

    private double resample(double s, double rate) {
        push(s);
        readPos += rate;
        while (readPos >= line.length) {
            readPos -= line.length;
        }
        int i0 = (int) readPos;
        int i1 = (i0 + 1) % line.length;
        double frac = readPos - i0;
        return line[i0] * (1 - frac) + line[i1] * frac;
    }

    /** SLIP ROLL / ROLL: capture one effect time of audio, then repeat it. */
    private double roll(double s) {
        if (recording) {
            push(s);
            recorded++;
            if (recorded >= (int) (timeSeconds * fs)) {
                recording = false;
                readPos = writePos - recorded;
                while (readPos < 0) {
                    readPos += line.length;
                }
            }
            return s;
        }
        int len = Math.max(1, (int) (timeSeconds * fs));
        int start = writePos - recorded;
        while (start < 0) {
            start += line.length;
        }
        readPos += 1.0;
        if (readPos >= start + len) {
            readPos = start;
        }
        return line[((int) readPos) % line.length];
    }

    /** VINYL BRAKE: playback rate ramps to a standstill over roughly a second and a half. */
    private double vinylBrake(double s) {
        double out = resample(s, brake);
        brake -= 1.0 / (BRAKE_SECONDS * fs) * (0.5 + depth);
        if (brake < 0) {
            brake = 0;
        }
        return out;
    }

    /** HELIX: like ROLL, but the captured slice keeps layering on top of itself. */
    private double helix(double s) {
        if (recording) {
            push(s);
            recorded++;
            if (recorded >= (int) (timeSeconds * fs)) {
                recording = false;
                readPos = writePos - recorded;
                while (readPos < 0) {
                    readPos += line.length;
                }
            }
            return s;
        }
        int len = Math.max(1, (int) (timeSeconds * fs));
        int idx = ((int) readPos) % line.length;
        double looped = line[idx];
        // Feed the live signal back into the loop so it piles up, which is the helix. The two
        // shares have to sum to one: at 0.95 feedback against a fixed 0.5 of live signal the loop
        // settled ten times louder than its input, so the effect that is meant to build tension
        // just pinned the limiter.
        double feedback = 0.5 + 0.45 * depth;
        line[idx] = (float) (looped * feedback + s * (1.0 - feedback));
        readPos += 1.0;
        int start = writePos - recorded;
        while (start < 0) {
            start += line.length;
        }
        if (readPos >= start + len) {
            readPos = start;
        }
        return looped;
    }

    /** Drop every tail; call on seeks so old audio doesn't ring into the new position. */
    public void reset() {
        java.util.Arrays.fill(line, 0f);
        lfoFilter.reset();
        bandLow.reset();
        bandHigh.reset();
        for (Allpass1 stage : phase) {
            stage.reset();
        }
        sincePhaserBake = 0;
        lfoPhase = 0;
        brake = 1.0;
        recorded = 0;
        recording = false;
    }
}
