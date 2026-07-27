package com.osgworld.djbooth.client.audio.dsp;

/**
 * One knob position, moved toward its target slowly enough that the DSP reading it never jumps.
 *
 * <p>A knob arrives as a step: one value this block, a different one the next. Baking filter
 * coefficients straight onto the new value moves the response between one sample and the next, and
 * that discontinuity is a click; sweeping a knob then makes a click per step, heard as crackle.
 *
 * <p>Two things are needed to stop it, and neither is sufficient alone. Approaching by a fraction
 * of the remaining distance smooths small moves, but a knob slammed across half its travel makes
 * that first fraction larger than the wheel notch the smoothing exists to absorb, so the ramp's own
 * first step clicks. Capping the move per chunk fixes that, but on its own it makes every move
 * linear and arrives with a corner. Together they give a ramp that is fast for small moves, bounded
 * for large ones, and never steps further than a smoothed notch.
 *
 * <p>Advance it every {@link #CHUNK_FRAMES} samples, not once per audio block: a block can be a
 * thousand samples, and a ramp that only moves once per block is a stair.
 */
public final class ParamRamp {
    /** How often to step. Fine enough that no single step is audible, coarse enough to be cheap. */
    public static final int CHUNK_FRAMES = 64;
    /** Approach factor per chunk: about 9 ms to close a gap at 48 kHz. */
    private static final double SMOOTH = 0.15;
    /** One wheel notch is 0.04 of the travel; never move further in a chunk than a smoothed notch. */
    private static final double MAX_STEP = SMOOTH * 0.04;
    /** Below this the remaining distance is far under what an ear or a coefficient can resolve. */
    private static final double SNAP = 1e-4;

    private final int cadence;
    private final double smooth;
    private final double maxStep;

    private double value;
    private boolean primed;

    /** A ramp stepping on the default cadence, which suits a knob mapped to decibels. */
    public ParamRamp() {
        this(CHUNK_FRAMES);
    }

    /**
     * A ramp stepping every {@code cadenceFrames} samples.
     *
     * <p>Finer cadences take the same time overall but in smaller steps, which is what a knob
     * mapped to something steeper than decibels needs. The COLOR knob sweeps a cutoff across
     * decades, so a step that is negligible in dB moves it several percent in Hz; at the default
     * cadence that still bent the waveform 6.4x more than the signal did on its own.
     */
    public ParamRamp(int cadenceFrames) {
        this.cadence = Math.max(1, cadenceFrames);
        double scale = this.cadence / (double) CHUNK_FRAMES;
        this.smooth = SMOOTH * scale;
        this.maxStep = MAX_STEP * scale;
    }

    /** How many samples to run between {@link #advance} calls. */
    public int cadenceFrames() {
        return cadence;
    }

    /** Jump straight to a value, for when a ramp would be wrong: a fresh deck, a format change. */
    public void snapTo(double v) {
        this.value = v;
        this.primed = true;
    }

    /** Step once toward {@code target} and return the new position. */
    public double advance(double target) {
        if (!primed) {
            // First call: start where the knob already is, so loading a deck with the effect
            // already turned doesn't sweep audibly up to it.
            value = target;
            primed = true;
            return value;
        }
        double move = (target - value) * smooth;
        value += Math.max(-maxStep, Math.min(maxStep, move));
        if (Math.abs(target - value) < SNAP) {
            value = target; // don't rebake forever chasing an exponential tail
        }
        return value;
    }

    /** Where the ramp is now, without moving it. */
    public double value() {
        return value;
    }

    /** Forget the position so the next {@link #advance} snaps instead of sweeping. */
    public void unprime() {
        primed = false;
    }
}
