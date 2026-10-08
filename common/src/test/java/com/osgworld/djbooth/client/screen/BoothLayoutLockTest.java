package com.osgworld.djbooth.client.screen;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes where every control sits on the booth panel.
 *
 * <p>The panel art ({@code booth.png}) is drawn from these numbers by {@code tools/gen_booth.py},
 * and the widgets are placed from them at run time. A change to any value moves a control off the
 * artwork printed under it, so it must be a decision: change the number, regenerate the panel, and
 * update this table in the same commit. A drive-by edit while improving something else fails here.
 */
class BoothLayoutLockTest {

    private static final Object[][] FROZEN = {
        {"REGION_DECK_A", 0.0083f, 0.0227f, 0.3500f, 0.9545f},
        {"REGION_MIXER", 0.3708f, 0.0227f, 0.2583f, 0.9545f},
        {"REGION_DECK_B", 0.6417f, 0.0227f, 0.3500f, 0.9545f},
        {"DECK_JOG", 0.250f, 0.400f, 0.520f, 0.520f},
        {"DECK_PLAY", 0.168f, 0.895f, 0.075f, 0.075f},
        {"DECK_CUE", 0.168f, 0.765f, 0.075f, 0.075f},
        {"DECK_LOOP", 0.270f, 0.385f, 0.095f, 0.060f},
        {"DECK_TEMPO", 0.775f, 0.700f, 0.050f, 0.240f},
        {"DECK_SCREEN", 0.166f, 0.148f, 0.619f, 0.120f},
        {"DECK_URLBAR", 0.170f, 0.055f, 0.610f, 0.058f},
        {"DECK_DIRECTION", 0.045f, 0.545f, 0.085f, 0.045f},
        {"DECK_SLIP", 0.040f, 0.205f, 0.055f, 0.026f},
        {"DECK_QUANTIZE", 0.103f, 0.205f, 0.062f, 0.026f},
        {"DECK_JOGMODE", 0.880f, 0.430f, 0.070f, 0.030f},
        {"DECK_TEMPO_RESET", 0.795f, 0.640f, 0.045f, 0.030f},
        {"DECK_MASTER_TEMPO", 0.855f, 0.640f, 0.070f, 0.030f},
        {"DECK_BEAT_SYNC", 0.855f, 0.348f, 0.062f, 0.028f},
        {"DECK_KEY_SYNC", 0.855f, 0.385f, 0.062f, 0.028f},
        {"DECK_TRACK_START", 0.045f, 0.612f, 0.085f, 0.030f},
        {"DECK_SEARCH_BACK", 0.045f, 0.652f, 0.040f, 0.030f},
        {"DECK_SEARCH_FWD", 0.090f, 0.652f, 0.040f, 0.030f},
        {"DECK_CALL_PREV", 0.560f, 0.348f, 0.038f, 0.026f},
        {"DECK_CALL_NEXT", 0.602f, 0.348f, 0.038f, 0.026f},
        {"DECK_MEM_DELETE", 0.660f, 0.348f, 0.045f, 0.026f},
        {"DECK_MEMORY", 0.712f, 0.348f, 0.052f, 0.026f},
        {"MIX_FADER_A", 0.222f, 0.673f, 0.050f, 0.111f},
        {"MIX_FADER_B", 0.352f, 0.673f, 0.050f, 0.111f},
        {"MIX_MASTER", 0.715f, 0.185f, 0.065f, 0.230f},
        {"MIX_XFADER", 0.340f, 0.905f, 0.200f, 0.045f},
        {"MIX_HI_A", 0.221f, 0.236f, 0.050f, 0.041f},
        {"MIX_MID_A", 0.221f, 0.310f, 0.050f, 0.041f},
        {"MIX_LOW_A", 0.221f, 0.383f, 0.050f, 0.041f},
        {"MIX_FILTER_A", 0.214f, 0.474f, 0.065f, 0.053f},
        {"MIX_HI_B", 0.351f, 0.236f, 0.050f, 0.041f},
        {"MIX_MID_B", 0.351f, 0.310f, 0.050f, 0.041f},
        {"MIX_LOW_B", 0.351f, 0.383f, 0.050f, 0.041f},
        {"MIX_FILTER_B", 0.344f, 0.474f, 0.065f, 0.053f},
        {"MIX_GAIN_A", 0.224f, 0.161f, 0.044f, 0.035f},
        {"MIX_GAIN_B", 0.354f, 0.161f, 0.044f, 0.035f},
        {"MIX_XF_ASSIGN_A", 0.224f, 0.833f, 0.045f, 0.025f},
        {"MIX_XF_ASSIGN_B", 0.354f, 0.833f, 0.045f, 0.025f},
        {"MIX_ECHO_A", 0.700f, 0.500f, 0.060f, 0.048f},
        {"MIX_ECHO_B", 0.700f, 0.585f, 0.060f, 0.048f},
        {"MIX_COLOR_MODES", 0.040f, 0.452f, 0.110f, 0.105f},
        {"MIX_COLOR_PARAM", 0.073f, 0.576f, 0.044f, 0.035f},
        {"FX_BEATS", 0.812f, 0.360f, 0.160f, 0.052f},
        {"FX_TAP", 0.862f, 0.478f, 0.060f, 0.032f},
        {"FX_FREQ", 0.812f, 0.542f, 0.160f, 0.026f},
        {"FX_TYPES", 0.800f, 0.590f, 0.185f, 0.075f},
        {"FX_CHANNEL", 0.862f, 0.672f, 0.060f, 0.028f},
        {"FX_DEPTH", 0.870f, 0.820f, 0.044f, 0.035f},
        {"FX_ONOFF", 0.858f, 0.898f, 0.068f, 0.038f},
        {"MIX_ISOLATOR", 0.718f, 0.712f, 0.066f, 0.030f},
        {"MIX_FADERCURVE", 0.718f, 0.766f, 0.066f, 0.030f},
        {"MIX_XFCURVE", 0.718f, 0.820f, 0.066f, 0.030f},
        {"MIX_METER_A", 0.196f, 0.205f, 0.011f, 0.250f},
        {"MIX_METER_B", 0.326f, 0.205f, 0.011f, 0.250f},
        {"MIX_BALANCE", 0.729f, 0.474f, 0.044f, 0.035f},
        {"MIX_BOOTH", 0.729f, 0.636f, 0.044f, 0.035f},
        {"MIX_CUE_A", 0.215f, 0.554f, 0.064f, 0.022f},
        {"MIX_CUE_B", 0.345f, 0.554f, 0.064f, 0.022f},
    };

    @Test
    void everyControlStaysWhereThePanelArtPutsIt() throws Exception {
        Map<String, BoothLayout.Rect> live = new HashMap<>();
        for (Field f : BoothLayout.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == BoothLayout.Rect.class) {
                live.put(f.getName(), (BoothLayout.Rect) f.get(null));
            }
        }
        for (Object[] row : FROZEN) {
            String name = (String) row[0];
            BoothLayout.Rect r = live.remove(name);
            assertTrue(r != null, name + " was removed from BoothLayout");
            assertEquals((float) row[1], r.x(), 0f, name + ".x moved");
            assertEquals((float) row[2], r.y(), 0f, name + ".y moved");
            assertEquals((float) row[3], r.w(), 0f, name + ".w moved");
            assertEquals((float) row[4], r.h(), 0f, name + ".h moved");
        }
        assertTrue(live.isEmpty(), "controls added without being frozen here: " + live.keySet());
    }
}
