package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.ColorFxModes;

/**
 * One channel's SOUND COLOR FX stage: the effect the big COLOR knob drives on a DJM-900NXS2.
 *
 * <p>The mixer has six of them, and the knob is centre-detented — dead centre is always "no
 * effect", turning left does one thing and turning right does another. What each side does follows
 * the operating manual:
 *
 * <ul>
 *   <li><b>FILTER</b> — left sweeps a low-pass cutoff down, right sweeps a high-pass cutoff up.</li>
 *   <li><b>SPACE</b> — reverb; left sends the mid and low range in, right the mid and high.</li>
 *   <li><b>DUB ECHO</b> — echo; left keeps the repeats in the mid range, right extends them up.</li>
 *   <li><b>SWEEP</b> — left is a gate that tightens the sound, right a band pass that narrows.</li>
 *   <li><b>NOISE</b> — white noise through a filter; left drops its cutoff, right raises it.</li>
 *   <li><b>CRUSH</b> — left piles on distortion, right crushes before a high-pass.</li>
 * </ul>
 *
 * <p>The PARAMETER knob scales how much of the effect you get, exactly as on the hardware.
 * Instances are per audio channel and are only touched from the audio thread.
 */
public final class ColorFx {
    private static final int SPACE = ColorFxModes.SPACE;
    private static final int DUB_ECHO = ColorFxModes.DUB_ECHO;
    private static final int SWEEP = ColorFxModes.SWEEP;
    private static final int NOISE = ColorFxModes.NOISE;
    private static final int CRUSH = ColorFxModes.CRUSH;
    private static final int FILTER = ColorFxModes.FILTER;

    private static final double DEAD_ZONE = 0.02; // knob travel around centre that stays dry

    // PARAMETER is the filter's resonance — Pioneer's own page for the NXS2 says turning it right
    // increases resonance, and that squelch is most of what the effect is for. It used to run
    // 0.9..2.0, worth only +1.6 dB at the cutoff: measurably present, audibly nothing, which is
    // why it read as a knob that did not work. It now reaches a Q of 8.
    //
    // A resonant filter peaks at roughly Q times its input at the cutoff, and +18 dB of it would
    // simply live in the limiter. Scaling the output by 1/sqrt(Q) keeps the peak prominent —
    // around +9 dB at the top — while the body of the sound thins out as resonance comes up,
    // which is what a resonant filter does anyway.
    private static final double FILTER_Q_MIN = 0.7;
    private static final double FILTER_Q_RANGE = 2.8; // tops out at 3.5
    /** Travel over which the filter fades up from dry, so it does not switch in mid-waveform. */
    private static final double FILTER_FADE_IN = 0.15;
    private static final double REVERB_SECONDS = 0.09;  // comb spread for the SPACE reverb
    private static final double ECHO_SECONDS = 0.28;    // DUB ECHO repeat time
    private static final int COMBS = 4;

    private final Biquad sweep = new Biquad();      // FILTER / SWEEP band shaping
    private final Biquad send = new Biquad();       // which range feeds SPACE / DUB ECHO
    private final Biquad noiseFilter = new Biquad();
    private final Biquad crushHp = new Biquad();

    // Reverb: a small bank of combs into one allpass. Cheap, and enough to read as "space".
    private final float[][] comb = new float[COMBS][];
    private final int[] combPos = new int[COMBS];
    private float[] allpass;
    private int allpassPos;

    // Echo delay line.
    private float[] echo;
    private int echoPos;

    private double fs = 48000;
    private long noiseState = 0x9E3779B97F4A7C15L;

    // Where the knobs are being asked to go, written when the panel moves.
    private int mode = FILTER;
    private double targetKnob = 0.5, targetParam = 0.5;

    // Where they actually are. The COLOR knob drives filter cutoffs, so baking coefficients
    // straight onto a new position steps the response mid-waveform and clicks - the same fault
    // the channel EQ had, and worse here because the cutoff sweeps decades rather than dB.
    // Far finer than the EQ's cadence, because the knob maps to a cutoff exponentially: equal
    // steps of knob are equal *ratios* of frequency, so a step worth nothing in dB moves the
    // corner several percent. Measured against what the same filter does standing still, a sweep
    // of the low-pass across a 110 Hz tone bends the waveform 2.6x sharper at a cadence of 4 and
    // 1.8x at 2, so this is where it stops being audible rather than where it stops being cheap.
    // The cost is only paid while a knob is actually moving: the ramp rebakes nothing otherwise.
    private static final int RAMP_CADENCE = ParamRamp.CHUNK_FRAMES / 32;
    private final ParamRamp knobRamp = new ParamRamp(RAMP_CADENCE);
    private final ParamRamp paramRamp = new ParamRamp(RAMP_CADENCE);
    private int sinceChunk;   // samples since the ramps last moved
    private double knob = 0.5, param = 0.5; // last values baked
    private boolean baked;

    // Derived, per side of the knob.
    private double depth;     // 0..1, how far from centre the knob is
    private boolean rightSide;

    /** (Re)allocate the delay lines for a sample rate. Call whenever the format changes. */
    public void setup(double sampleRate) {
        this.fs = sampleRate;
        // Mutually prime-ish comb lengths avoid a metallic, ringing reverb.
        double[] spread = {1.0, 0.79, 1.31, 1.13};
        for (int i = 0; i < COMBS; i++) {
            comb[i] = new float[Math.max(1, (int) (sampleRate * REVERB_SECONDS * spread[i]))];
            combPos[i] = 0;
        }
        allpass = new float[Math.max(1, (int) (sampleRate * 0.005))];
        allpassPos = 0;
        echo = new float[Math.max(1, (int) (sampleRate * ECHO_SECONDS))];
        echoPos = 0;
        baked = false;
        sinceChunk = 0;
        knobRamp.unprime();
        paramRamp.unprime();
        reset();
    }

    /**
     * Point the stage at a mode and knob positions. Cheap: the knobs are only targets here, and
     * {@link #process} walks the ramps toward them.
     *
     * <p>A mode change is not ramped. There is no continuous path from a reverb to a bit crusher,
     * so the switch snaps and the tails are dropped, which is what the hardware does when you press
     * a different button.
     */
    public void set(int newMode, double newKnob, double newParam) {
        if (newMode != mode) {
            this.mode = newMode;
            this.targetKnob = newKnob;
            this.targetParam = newParam;
            knobRamp.snapTo(newKnob);
            paramRamp.snapTo(newParam);
            reset();
            bake();
            return;
        }
        this.targetKnob = newKnob;
        this.targetParam = newParam;
        if (!baked) {
            knobRamp.snapTo(newKnob);
            paramRamp.snapTo(newParam);
            bake();
        }
    }

    /** Step the ramps and rebake if they moved. Called on the chunk cadence from {@link #process}. */
    private void advanceRamps() {
        double k = knobRamp.advance(targetKnob);
        double p = paramRamp.advance(targetParam);
        if (k == knob && p == param && baked) {
            return;
        }
        this.knob = k;
        this.param = p;
        bake();
    }

    private void bake() {
        double off = knob - 0.5;
        rightSide = off > 0;
        depth = Math.min(1.0, Math.max(0.0, (Math.abs(off) - DEAD_ZONE) / (0.5 - DEAD_ZONE)));
        double nyq = fs / 2.0;

        switch (mode) {
            case FILTER -> {
                // Q comes from PARAMETER alone, so it holds still while the cutoff sweeps.
                //
                // Nothing compensates for the peak a high Q adds, and that is deliberate. Three
                // attempts at it were measured and thrown away: scaling the output by 1/sqrt(Q)
                // dropped the level 6 dB the moment the knob left the detent; fading that
                // compensation in over the first part of the travel turned the drop into a 4.6 dB
                // lurch packed into a few notches, which the click metric caught at 7.6x; and
                // growing Q with the knob changed the filter's shape mid-sweep, which it caught
                // at 3000x. The resonant peak is not a fault to be cancelled — it is the effect,
                // and it is what PARAMETER is for. The output limiter is what keeps it in range,
                // which is also how the hardware is arranged.
                double q = FILTER_Q_MIN + param * FILTER_Q_RANGE;
                if (depth <= 0) {
                    sweep.identity();
                } else if (rightSide) {
                    // High-pass cutoff rises as the knob goes right.
                    sweep.highpass(fs, Math.min(sweepHz(depth, 20.0, 10000.0), nyq * 0.98), q);
                } else {
                    // Low-pass cutoff descends as the knob goes left.
                    sweep.lowpass(fs, Math.min(sweepHz(1.0 - depth, 80.0, 18000.0), nyq * 0.98), q);
                }
            }
            case SWEEP -> {
                // Right is a band pass whose bandwidth shrinks; left is a gate, handled per sample.
                if (rightSide && depth > 0) {
                    sweep.bandpass(fs, 1200.0, 0.7 + depth * 12.0);
                } else {
                    sweep.identity();
                }
            }
            case SPACE, DUB_ECHO -> {
                // Which part of the spectrum is fed into the tail: mid+low on the left, mid+high
                // on the right, per the manual.
                if (rightSide) {
                    send.highpass(fs, 700.0, 0.7);
                } else {
                    send.lowpass(fs, Math.min(2500.0, nyq * 0.98), 0.7);
                }
                sweep.identity();
            }
            case NOISE -> {
                // The noise itself is filtered: cutoff descends to the left, rises to the right.
                double cutoff = rightSide
                        ? sweepHz(depth, 400.0, 16000.0)
                        : sweepHz(1.0 - depth, 120.0, 8000.0);
                noiseFilter.lowpass(fs, Math.min(cutoff, nyq * 0.98), 0.9);
                sweep.identity();
            }
            case CRUSH -> {
                // Turning right crushes before a high-pass, which is what thins the sound out.
                if (rightSide && depth > 0) {
                    crushHp.highpass(fs, sweepHz(depth, 200.0, 4000.0), 0.7);
                } else {
                    crushHp.identity();
                }
                sweep.identity();
            }
            default -> sweep.identity();
        }
        baked = true;
    }

    private static double sweepHz(double t, double minHz, double maxHz) {
        return minHz * Math.pow(maxHz / minHz, Math.min(1.0, Math.max(0.0, t)));
    }

    /** Cheap xorshift noise in -1..1; a real RNG per sample would dominate the profile. */
    private double noise() {
        noiseState ^= noiseState << 13;
        noiseState ^= noiseState >>> 7;
        noiseState ^= noiseState << 17;
        return (noiseState >> 11) / (double) (1L << 52);
    }

    /** Run one sample through the stage. */
    public double process(double s) {
        if (sinceChunk-- <= 0) {
            sinceChunk = RAMP_CADENCE - 1;
            advanceRamps();
        }
        if (depth <= 0) {
            return s; // centre detent: fully dry, whatever the mode
        }
        return switch (mode) {
            case SPACE -> s + depth * 0.8 * reverb(send.process(s));
            case DUB_ECHO -> s + depth * 0.9 * echo(send.process(s));
            case SWEEP -> rightSide ? mix(s, sweep.process(s)) : gate(s);
            case NOISE -> s + depth * (0.25 + 0.55 * param) * noiseFilter.process(noise());
            case CRUSH -> crush(s);
            // FILTER is fully wet once the knob is properly turned. Blending dry in across the
            // whole travel was wrong twice over: a filter on the hardware replaces the signal
            // rather than sitting beside it, and summing a filtered copy with the original
            // comb-filters the two, so cutting the bass left the bass audibly there while the
            // mids hollowed out.
            //
            // The first sliver of travel is the exception. There the resonance compensation would
            // otherwise land as a step — 6 dB of level gone the moment the knob moves — so fade
            // it in. Nothing is lost to comb filtering in that range because the cutoff is parked
            // at the far end of its sweep, where the filter is passing everything anyway.
            // FILTER: wet, once the knob is properly turned. Blending dry across the *whole*
            // travel was wrong — a filter replaces the signal rather than sitting beside it, and
            // summing a filtered copy with the original comb-filters the two, so cutting the bass
            // left the bass audibly there while the mids hollowed out.
            //
            // The first sliver of travel still has to fade, though, and not for a cosmetic
            // reason: the moment depth passes zero the output switches from the input to a filter
            // whose state is empty, and that step measured 2550x on the click metric. Fading over
            // the first fraction of the throw costs nothing, because the cutoff is parked at the
            // far end of its sweep there and the filter is passing the signal through anyway.
            default -> {
                double wet = sweep.process(s);
                double blend = Math.min(1.0, depth / FILTER_FADE_IN);
                yield s + blend * (wet - s);
            }
        };
    }

    /** Blend dry into wet by how far the knob is turned, so the effect eases in. */
    private double mix(double dry, double wet) {
        return dry + depth * (wet - dry);
    }

    private double reverb(double in) {
        double sum = 0;
        double feedback = 0.72 + 0.24 * param; // PARAMETER stretches the tail
        for (int i = 0; i < COMBS; i++) {
            float[] line = comb[i];
            int p = combPos[i];
            double delayed = line[p];
            line[p] = (float) (in + delayed * feedback);
            combPos[i] = (p + 1) % line.length;
            sum += delayed;
        }
        sum /= COMBS;
        // One allpass to smear the comb output so it stops sounding like four discrete echoes.
        double ap = allpass[allpassPos];
        double out = -0.6 * sum + ap;
        allpass[allpassPos] = (float) (sum + 0.6 * out);
        allpassPos = (allpassPos + 1) % allpass.length;
        return out;
    }

    private double echo(double in) {
        double feedback = 0.35 + 0.5 * param;
        double delayed = echo[echoPos];
        echo[echoPos] = (float) (in + delayed * feedback);
        echoPos = (echoPos + 1) % echo.length;
        return delayed;
    }

    /** Left-hand SWEEP: a gate that clamps down on quiet parts, tightening the sound. */
    private double gate(double s) {
        double threshold = depth * 0.35;
        double a = Math.abs(s);
        if (a >= threshold) {
            return s;
        }
        // Soften the edge so the gate doesn't click on every zero crossing.
        double open = threshold > 1e-9 ? a / threshold : 1.0;
        return s * open * open;
    }

    private double crush(double s) {
        // Fewer and fewer quantisation steps as the knob moves away from centre.
        double steps = Math.max(2.0, Math.pow(2.0, 12.0 - 10.0 * depth));
        double crushed = Math.round(s * steps) / steps;
        if (rightSide) {
            return mix(s, crushHp.process(crushed));
        }
        // Left also drives the signal into a soft clip, which is where the distortion comes from.
        // Normalising by tanh(drive) fixes the peak but not the loudness: saturation fills in
        // everything under the peak too, so the measured level ran nearly 7 dB above dry and the
        // effect worked mostly by being louder. Backing off with the drive keeps the distortion
        // and drops the volume jump, so turning the knob sounds like a change rather than a boost.
        double drive = 1.0 + depth * (4.0 + 12.0 * param);
        double makeup = 0.9 / Math.sqrt(drive);
        return mix(s, Math.tanh(crushed * drive) / Math.tanh(drive) * makeup);
    }

    /** Drop every tail and filter state; call on seeks so old audio doesn't ring into the new spot. */
    public void reset() {
        sweep.reset();
        send.reset();
        noiseFilter.reset();
        crushHp.reset();
        for (float[] line : comb) {
            if (line != null) {
                java.util.Arrays.fill(line, 0f);
            }
        }
        if (allpass != null) {
            java.util.Arrays.fill(allpass, 0f);
        }
        if (echo != null) {
            java.util.Arrays.fill(echo, 0f);
        }
    }
}
