package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.BeatFxTypes;
import com.osgworld.djbooth.mixer.ChannelSettings;
import com.osgworld.djbooth.mixer.ColorFxModes;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assembled chain, in the order the DJM-900NXS2 manual puts it: the channel meter reads before
 * the fader, and a BEAT FX comes after the fader, so lowering the fader leaves the effect ringing.
 */
class ChannelStripTest {

    private static final int FS = 48000;
    private static final int FRAMES = 1024;

    /** Flat tone controls; only the fader gain and the BEAT FX vary between tests. */
    private static ChannelSettings settings(float gain, boolean echoOn) {
        return new ChannelSettings(0.5f, 0.5f, 0.5f, 0.5f, 0f, 0.5f, false,
                ColorFxModes.FILTER, 0.5f,
                BeatFxTypes.ECHO, echoOn, 0.25f, 0.5f, BeatFxTypes.BANDS_ALL, 0.5f, gain);
    }

    private static ChannelStrip strip(ChannelSettings cfg) {
        ChannelStrip s = new ChannelStrip();
        s.setup(FS, 2, false);
        s.setSettings(cfg);
        return s;
    }

    /** Block {@code n} of a steady 440 Hz tone on both sides, float interleaved. */
    private static ByteBuffer block(int n, double amp) {
        ByteBuffer b = ByteBuffer.allocateDirect(FRAMES * 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < FRAMES; i++) {
            long t = (long) n * FRAMES + i;
            float v = (float) (amp * Math.sin(2 * Math.PI * 440.0 * t / FS));
            b.putFloat(v);
            b.putFloat(v);
        }
        b.clear();
        return b;
    }

    /** Run block {@code n} through the strip and return the left channel. */
    private static float[] run(ChannelStrip s, int n, double amp) {
        ByteBuffer b = block(n, amp);
        s.processInPlace(b);
        float[] left = new float[FRAMES];
        ByteBuffer v = b.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < FRAMES; i++) {
            left[i] = v.getFloat(i * 8);
        }
        return left;
    }

    private static double rms(float[] x) {
        double sum = 0;
        for (float v : x) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum / x.length);
    }

    private static double maxStep(float[] x) {
        double m = 0;
        for (int i = 1; i < x.length; i++) {
            m = Math.max(m, Math.abs(x[i] - x[i - 1]));
        }
        return m;
    }

    @Test
    void closingTheFaderLeavesTheEffectsTailRinging() {
        // Manual (ECHO): "lowering the faders ... results in an echo sound and consequently a fade".
        ChannelStrip s = strip(settings(1.0f, true));
        double playing = 0;
        for (int n = 0; n < 12; n++) {
            playing = rms(run(s, n, 0.5)); // about a quarter of a second of ECHO in full flow
        }
        assertTrue(playing > 0.1, "the setup should be audible before the fader moves: " + playing);

        s.setSettings(settings(0.0f, true)); // the DJ pulls the channel fader down; the deck plays on
        double tail = 0;
        for (int n = 12; n < 24; n++) {      // roughly 0.25 s to 0.5 s after the move
            float[] out = run(s, n, 0.5);
            if (n >= 16) {
                tail = Math.max(tail, rms(out));
            }
        }
        assertTrue(tail > 0.05 * playing,
                "the echo should still be sounding with the fader down, was " + tail + " against " + playing);
    }

    @Test
    void withNoEffectClosingTheFaderIsSilence() {
        // The control case: the same fader move with BEAT FX off leaves nothing, so the tail above
        // really is the effect and not leakage past the fader.
        ChannelStrip s = strip(settings(1.0f, false));
        for (int n = 0; n < 12; n++) {
            run(s, n, 0.5);
        }
        s.setSettings(settings(0.0f, false));
        double after = 0;
        for (int n = 12; n < 24; n++) {
            float[] out = run(s, n, 0.5);
            if (n >= 14) {
                after = Math.max(after, rms(out));
            }
        }
        assertTrue(after < 1e-3, "a closed fader should be silent without an effect, was " + after);
    }

    @Test
    void theFaderScalesTheSoundLinearlyWhenNoEffectIsOn() {
        double full = settledRms(strip(settings(1.0f, false)));
        double half = settledRms(strip(settings(0.5f, false)));
        assertEquals(0.5, half / full, 0.02, "half a fader should be half the level");
    }

    private static double settledRms(ChannelStrip s) {
        double last = 0;
        for (int n = 0; n < 10; n++) {
            last = rms(run(s, n, 0.3));
        }
        return last;
    }

    @Test
    void theChannelMeterReadsBeforeTheFader() {
        // Manual: the level indicator shows the signal "before passing through the channel faders".
        float open = peakAfter(strip(settings(1.0f, false)));
        float closed = peakAfter(strip(settings(0.0f, false)));
        assertTrue(open > 0.2f, "the meter should be reading something: " + open);
        assertEquals(open, closed, 1e-6f, "moving the fader must not move the meter");
    }

    private static float peakAfter(ChannelStrip s) {
        for (int n = 0; n < 6; n++) {
            run(s, n, 0.5);
        }
        return Math.max(s.peakLeft(), s.peakRight());
    }

    @Test
    void aFaderMoveGlidesInsteadOfClicking() {
        ChannelStrip s = strip(settings(1.0f, false));
        float[] steady = null;
        for (int n = 0; n < 6; n++) {
            steady = run(s, n, 0.5);
        }
        double baseline = maxStep(steady);

        s.setSettings(settings(0.0f, false));
        double worst = 0;
        for (int n = 6; n < 12; n++) {
            worst = Math.max(worst, maxStep(run(s, n, 0.5)));
        }
        // A step to zero from a sine near its peak would jump by about the amplitude (0.5); a
        // glide over a few milliseconds keeps every step close to the signal's own slope.
        assertTrue(worst < 2.5 * baseline,
                "the fader move clicked: biggest step " + worst + " against the tone's own " + baseline);
    }

    @Test
    void theFirstBlockStartsAtTheFadersPositionInsteadOfFadingIn() {
        // A deck that loads with its fader down must be silent from the first sample, not swell in
        // from full over the first few milliseconds.
        ChannelStrip s = strip(settings(0.0f, false));
        float[] first = run(s, 0, 0.5);
        assertTrue(rms(first) < 1e-3, "a closed fader should be silent from the start: " + rms(first));
    }
}
