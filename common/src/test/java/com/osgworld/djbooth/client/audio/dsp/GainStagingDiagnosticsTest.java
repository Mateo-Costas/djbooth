package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.BeatFxTypes;
import com.osgworld.djbooth.mixer.ColorFxModes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sweeps every knob on the mixer against a realistic signal and writes the levels to
 * build/diagnostics-gain.txt.
 *
 * <p>Not an assertion suite. This is the instrument: it turns "it sounds like electrical noise"
 * into a number saying which stage adds it, and it is how each of the faults below was located
 * rather than guessed at. {@link LimiterTest}, {@link ChannelEqTest} and {@link ColorFxTest} hold
 * the assertions that stop them coming back.
 *
 * <p>What to look for in the output: an effect that leaves the signal far louder than it found it
 * is one that will live permanently in the limiter, and an effect whose level jumps between
 * neighbouring knob positions is one that will be heard as a step rather than a sweep.
 */
class GainStagingDiagnosticsTest {

    private static final double FS = 48000;
    private final List<String> out = new ArrayList<>();

    private void say(String fmt, Object... args) {
        out.add(String.format(fmt, args));
    }

    private static double peak(double[] x) {
        double p = 0;
        for (double v : x) p = Math.max(p, Math.abs(v));
        return p;
    }

    private static double rms(double[] x) {
        double s = 0;
        for (double v : x) s += v * v;
        return Math.sqrt(s / Math.max(1, x.length));
    }

    private static double db(double lin) {
        return 20 * Math.log10(Math.max(1e-12, lin));
    }

    private static double[] sine(double freq, double amp, int n) {
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = amp * Math.sin(2 * Math.PI * freq * i / FS);
        return x;
    }

    /** A mastered track: several tones at once, peaking just under full scale. */
    private static double[] mastered(int n, double peakTarget) {
        double[] x = new double[n];
        double[] f = {55, 110, 440, 1200, 5000, 11000};
        double[] g = {1.0, 0.8, 0.5, 0.4, 0.25, 0.15};
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int k = 0; k < f.length; k++) s += g[k] * Math.sin(2 * Math.PI * f[k] * i / FS);
            x[i] = s;
        }
        double p = peak(x);
        for (int i = 0; i < n; i++) x[i] *= peakTarget / p;
        return x;
    }

    /** Distortion of a memoryless stage, as a fraction of the fundamental. */
    private static double thd(double[] out, double freq) {
        int n = out.length;
        double re = 0, im = 0, ref = 0;
        for (int i = 0; i < n; i++) {
            double w = 2 * Math.PI * freq * i / FS;
            re += out[i] * Math.sin(w);
            im += out[i] * Math.cos(w);
            ref += Math.sin(w) * Math.sin(w);
        }
        double a = re / ref, b = im / ref;
        double resid = 0, fund = 0;
        for (int i = 0; i < n; i++) {
            double w = 2 * Math.PI * freq * i / FS;
            double f = a * Math.sin(w) + b * Math.cos(w);
            fund += f * f;
            resid += (out[i] - f) * (out[i] - f);
        }
        return Math.sqrt(resid / Math.max(1e-12, fund));
    }

    @Test
    void measureEveryKnob() throws IOException {
        int n = (int) FS;
        double[] src = mastered(n, 0.95);
        double srcRms = rms(src);

        say("=== 1. OUTPUT STAGE: does ordinary loud audio survive it? ===");
        say("  peak in   peak out   THD");
        for (double amp : new double[]{0.5, 0.7, 0.8, 0.9, 0.95, 1.0, 2.0}) {
            double[] in = sine(1000, amp, n);
            Limiter lim = new Limiter();
            lim.setup(FS);
            double[] o = new double[n];
            for (int i = 0; i < n; i++) o[i] = Limiter.ceiling(in[i] * lim.gainFor(in[i]));
            say("  %.2f      %.3f      %6.2f%%", amp, peak(o), 100 * thd(o, 1000));
        }

        say("");
        say("=== 2. EQ: band centres, and how hard each one drives the limiter ===");
        say("  code: LOW %.0f Hz  MID %.0f Hz  HI %.0f Hz  (spec: 70 / 1000 / 13000)",
                ChannelEq.F_LOW, ChannelEq.F_MID, ChannelEq.F_HIGH);
        say("");
        say("  setting      pre-limit peak    limiter pulls down");
        for (String[] cfg : new String[][]{
                {"flat", "0.5", "0.5", "0.5"},
                {"LOW +6", "1.0", "0.5", "0.5"},
                {"MID +6", "0.5", "1.0", "0.5"},
                {"HI +6", "0.5", "0.5", "1.0"},
                {"all +6", "1.0", "1.0", "1.0"},
                {"LOW kill", "0.0", "0.5", "0.5"},
                {"MID kill", "0.5", "0.0", "0.5"},
                {"HI kill", "0.5", "0.5", "0.0"}}) {
            ChannelEq eq = new ChannelEq();
            eq.setup((int) FS, 1);
            eq.setTargets(Float.parseFloat(cfg[1]), Float.parseFloat(cfg[2]),
                    Float.parseFloat(cfg[3]), false);
            for (int i = 0; i < 500; i++) eq.advance();
            Limiter lim = new Limiter();
            lim.setup(FS);
            double[] pre = new double[n];
            double worst = 1.0;
            for (int i = 0; i < n; i++) {
                if (i % ChannelEq.CHUNK_FRAMES == 0) eq.advance();
                pre[i] = eq.process(0, src[i]);
                worst = Math.min(worst, lim.gainFor(pre[i]));
            }
            say("  %-11s  %.3f            %.1f dB", cfg[0], peak(pre), -db(worst));
        }

        say("");
        say("=== 3. TRIM: the real law, in dB ===");
        for (double k : new double[]{0.0, 0.1, 0.25, 0.4, 0.46, 0.5, 0.54, 0.6, 0.75, 1.0}) {
            double g = ChannelEq.trimGain(k);
            say("  knob %.2f -> x%.4f  (%+.2f dB)", k, g, db(g));
        }

        say("");
        say("=== 4. SOUND COLOR FX: level against dry, every mode, both sides ===");
        say("Watch for: a mode much louder than dry (lives in the limiter), or a big jump");
        say("between neighbouring knob positions (heard as a step, not a sweep).");
        for (int mode = 0; mode < ColorFxModes.MODES; mode++) {
            say("");
            say("  %-9s   knob:   0.05    0.20    0.35   [0.50]   0.65    0.80    0.95",
                    ColorFxModes.NAMES[mode]);
            for (double p : new double[]{0.0, 0.5, 1.0}) {
                StringBuilder row = new StringBuilder(String.format("    param %.1f          ", p));
                for (double k : new double[]{0.05, 0.20, 0.35, 0.50, 0.65, 0.80, 0.95}) {
                    ColorFx fx = new ColorFx();
                    fx.setup(FS);
                    fx.set(mode, k, p);
                    double[] o = new double[n];
                    for (int i = 0; i < n; i++) o[i] = fx.process(src[i]);
                    row.append(String.format("%+6.1f  ", db(rms(o)) - db(srcRms)));
                }
                say("%s", row);
            }
        }

        say("");
        say("=== 5. BEAT FX: level against dry, at depth 0.5 and 1.0 ===");
        say("  effect          depth 0.5   depth 1.0   worst-case peak");
        for (int type = 0; type < BeatFxTypes.TYPES; type++) {
            StringBuilder row = new StringBuilder(String.format("  %-14s", BeatFxTypes.NAMES[type]));
            double worstPeak = 0;
            for (double depth : new double[]{0.5, 1.0}) {
                BeatFx fx = new BeatFx(false);
                fx.setup(FS);
                fx.set(type, true, 0.5, depth, BeatFxTypes.BANDS_ALL);
                double[] o = new double[n];
                for (int i = 0; i < n; i++) o[i] = fx.process(src[i]);
                row.append(String.format("  %+7.1f  ", db(rms(o)) - db(srcRms)));
                worstPeak = Math.max(worstPeak, peak(o));
            }
            row.append(String.format("     %.3f", worstPeak));
            say("%s", row);
        }

        say("");
        say("=== 6. CHANNEL ECHO (the FX knob on each strip) ===");
        say("  knob    rms vs dry   peak");
        for (double e : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            ChannelEcho fx = new ChannelEcho();
            fx.setup(FS);
            double[] o = new double[n];
            for (int i = 0; i < n; i++) o[i] = fx.process(src[i], e);
            say("  %.2f    %+6.2f dB    %.3f", e, db(rms(o)) - db(srcRms), peak(o));
        }

        say("");
        say("=== 7. BALANCE: constant power? ===");
        say("  knob    left     right    sum of squares");
        for (double b : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            double angle = b * (Math.PI / 2.0);
            double l = Math.cos(angle) * Math.sqrt(2), r = Math.sin(angle) * Math.sqrt(2);
            say("  %.2f    x%.3f   x%.3f   %.3f", b, l, r, l * l + r * r);
        }

        Path f = Path.of("build", "diagnostics-gain.txt");
        Files.createDirectories(f.getParent());
        Files.write(f, out);
        for (String l : out) System.out.println(l);
    }
}
