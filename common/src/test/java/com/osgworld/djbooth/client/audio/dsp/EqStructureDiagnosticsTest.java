package com.osgworld.djbooth.client.audio.dsp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Separates the two things that can make an EQ sound broken, because they need opposite fixes.
 *
 * <p>A linear filter cannot add harmonics — it can only change how loud each frequency already in
 * the signal comes out. So if a <em>static</em> setting measures distortion, the fault is in the
 * filter or in whatever is moving its coefficients. If it measures none, the filter is clean and
 * what sounds wrong is the shape of the response: the wrong curve, correctly applied.
 *
 * <p>Writes build/diagnostics-eq.txt.
 */
class EqStructureDiagnosticsTest {

    private static final double FS = 48000;
    private final List<String> out = new ArrayList<>();

    private void say(String fmt, Object... args) {
        out.add(String.format(fmt, args));
    }

    /** Harmonic distortion of a settled, static stage, as a percentage of the fundamental. */
    private static double thd(double[] y, double hz) {
        int n = y.length;
        double re = 0, im = 0, ref = 0;
        for (int i = 0; i < n; i++) {
            double w = 2 * Math.PI * hz * i / FS;
            re += y[i] * Math.sin(w);
            im += y[i] * Math.cos(w);
            ref += Math.sin(w) * Math.sin(w);
        }
        double a = re / ref, b = im / ref;
        double resid = 0, fund = 0;
        for (int i = 0; i < n; i++) {
            double w = 2 * Math.PI * hz * i / FS;
            double f = a * Math.sin(w) + b * Math.cos(w);
            fund += f * f;
            resid += (y[i] - f) * (y[i] - f);
        }
        return 100 * Math.sqrt(resid / Math.max(1e-12, fund));
    }

    /** Settle a static EQ, then measure it on a pure tone. */
    private static double[] runStatic(float lo, float mid, float hi, boolean iso,
                                      double hz, double amp, boolean keepAdvancing) {
        ChannelEq eq = new ChannelEq();
        eq.setup((int) FS, 1);
        eq.setTargets(lo, mid, hi, iso);
        for (int i = 0; i < 2000; i++) {
            eq.advance();
        }
        int n = (int) FS;
        double[] y = new double[n];
        // Discard the first half second so the filter's own start-up transient is not counted.
        for (int i = 0; i < n; i++) {
            if (keepAdvancing && i % ChannelEq.CHUNK_FRAMES == 0) {
                eq.advance();
            }
            eq.process(0, amp * Math.sin(2 * Math.PI * hz * i / FS));
        }
        for (int i = 0; i < n; i++) {
            if (keepAdvancing && i % ChannelEq.CHUNK_FRAMES == 0) {
                eq.advance();
            }
            y[i] = eq.process(0, amp * Math.sin(2 * Math.PI * hz * (i + n) / FS));
        }
        return y;
    }

    private static double gainDbAt(float lo, float mid, float hi, double hz) {
        double[] y = runStatic(lo, mid, hi, false, hz, 0.5, false);
        double peak = 0;
        for (double v : y) {
            peak = Math.max(peak, Math.abs(v));
        }
        return 20 * Math.log10(Math.max(1e-9, peak / 0.5));
    }

    @Test
    void isTheEqDistortingOrJustTheWrongShape() throws IOException {
        say("=== A. DOES A STATIC EQ ADD HARMONICS? ===");
        say("A linear filter cannot. Anything above about 0.01%% here is a real fault in the");
        say("filter itself, not a matter of taste about the curve.");
        say("");
        say("  setting        60 Hz     300 Hz    1 kHz     8 kHz");
        for (String[] cfg : new String[][]{
                {"flat", "0.5", "0.5", "0.5"},
                {"LOW +6", "1.0", "0.5", "0.5"},
                {"LOW kill", "0.0", "0.5", "0.5"},
                {"MID +6", "0.5", "1.0", "0.5"},
                {"MID kill", "0.5", "0.0", "0.5"},
                {"HI +6", "0.5", "0.5", "1.0"},
                {"HI kill", "0.5", "0.5", "0.0"}}) {
            float lo = Float.parseFloat(cfg[1]), md = Float.parseFloat(cfg[2]),
                    hi = Float.parseFloat(cfg[3]);
            StringBuilder row = new StringBuilder(String.format("  %-13s", cfg[0]));
            for (double hz : new double[]{60, 300, 1000, 8000}) {
                row.append(String.format("  %7.4f%%", thd(runStatic(lo, md, hi, false, hz, 0.5, false), hz)));
            }
            say("%s", row);
        }

        say("");
        say("=== B. SAME, BUT WITH advance() RUNNING (knob still) ===");
        say("If these differ from A, the ramp is rebaking coefficients when nothing moved.");
        say("");
        say("  setting        60 Hz     300 Hz    1 kHz     8 kHz");
        for (String[] cfg : new String[][]{
                {"flat", "0.5", "0.5", "0.5"},
                {"LOW kill", "0.0", "0.5", "0.5"},
                {"LOW +6", "1.0", "0.5", "0.5"}}) {
            float lo = Float.parseFloat(cfg[1]), md = Float.parseFloat(cfg[2]),
                    hi = Float.parseFloat(cfg[3]);
            StringBuilder row = new StringBuilder(String.format("  %-13s", cfg[0]));
            for (double hz : new double[]{60, 300, 1000, 8000}) {
                row.append(String.format("  %7.4f%%", thd(runStatic(lo, md, hi, false, hz, 0.5, true), hz)));
            }
            say("%s", row);
        }

        say("");
        say("=== C. THE ACTUAL RESPONSE CURVE ===");
        say("What the three bands add up to. A DJ isolator should cut one range and leave the");
        say("others where they were; overlapping shelves and a bell need not behave that way.");
        say("");
        double[] probe = {40, 250, 1000, 3000, 4000, 6000, 8000, 10000, 13000, 18000};
        StringBuilder head = new StringBuilder("  setting     ");
        for (double f : probe) {
            head.append(String.format("%7.0f", f));
        }
        say("%s", head);
        for (String[] cfg : new String[][]{
                {"flat", "0.5", "0.5", "0.5"},
                {"LOW kill", "0.0", "0.5", "0.5"},
                {"MID kill", "0.5", "0.0", "0.5"},
                {"HI kill", "0.5", "0.5", "0.0"},
                {"LOW +6", "1.0", "0.5", "0.5"},
                {"MID +6", "0.5", "1.0", "0.5"},
                {"HI +6", "0.5", "0.5", "1.0"},
                {"L+M+H kill", "0.0", "0.0", "0.0"}}) {
            float lo = Float.parseFloat(cfg[1]), md = Float.parseFloat(cfg[2]),
                    hi = Float.parseFloat(cfg[3]);
            StringBuilder row = new StringBuilder(String.format("  %-12s", cfg[0]));
            for (double f : probe) {
                row.append(String.format("%+7.1f", gainDbAt(lo, md, hi, f)));
            }
            say("%s", row);
        }
        say("");
        say("  (dB relative to input. 'LOW kill' should be very negative at 40-70 Hz and");
        say("   near 0 everywhere else; anything else is bleed between the bands.)");

        say("");
        say("=== D. THE CROSSOVER ON ITS OWN: does low + high = input? ===");
        say("A Linkwitz-Riley pair must sum to unity at every frequency. Any deviation here is");
        say("the split itself, not the band gains on top of it.");
        say("");
        say("     Hz     |low|   |high|   |low+high|");
        for (double f : new double[]{40, 125, 250, 500, 1000, 2000, 4000, 8000, 13000, 18000}) {
            for (double corner : new double[]{4000}) {
                Crossover x = new Crossover();
                x.set(FS, corner);
                int n = (int) FS;
                double pl = 0, ph = 0, ps = 0;
                for (int i = 0; i < n; i++) {
                    double in = Math.sin(2 * Math.PI * f * i / FS);
                    x.split(in);
                    if (i > n / 2) {
                        pl = Math.max(pl, Math.abs(x.low()));
                        ph = Math.max(ph, Math.abs(x.high()));
                        ps = Math.max(ps, Math.abs(x.low() + x.high()));
                    }
                }
                say("  %7.0f   %6.3f   %6.3f   %6.3f", f, pl, ph, ps);
            }
        }

        Path f = Path.of("build", "diagnostics-eq.txt");
        Files.createDirectories(f.getParent());
        Files.write(f, out);
        for (String l : out) System.out.println(l);
    }
}
