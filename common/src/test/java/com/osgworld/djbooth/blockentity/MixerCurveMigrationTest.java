package com.osgworld.djbooth.blockentity;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The crossfader's three curves changed shape, so a stored index means something different in a
 * save from before. An old world must come back sounding like what the player had chosen.
 */
class MixerCurveMigrationTest {

    @Test
    void aSaveWithNoCurveGetsTheSmoothBlend() {
        assertEquals(MixerBlockEntity.XF_CURVE_SLOW, MixerBlockEntity.readCrossFaderCurve(new CompoundTag()));
    }

    @Test
    void oldGentleCurvesBecomeTheSmoothBlend() {
        for (int old : new int[]{0, 1}) { // the old sqrt and linear shapes
            CompoundTag tag = new CompoundTag();
            tag.putInt("CrossFaderCurve", old);
            assertEquals(MixerBlockEntity.XF_CURVE_SLOW, MixerBlockEntity.readCrossFaderCurve(tag),
                    "old curve " + old);
        }
    }

    @Test
    void theOldCuttingCurveBecomesTheNewCut() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CrossFaderCurve", 2); // the old cube, which the player picked to cut
        assertEquals(MixerBlockEntity.XF_CURVE_FAST, MixerBlockEntity.readCrossFaderCurve(tag));
    }

    @Test
    void aCurrentSaveIsTakenAtItsWord() {
        for (int curve = 0; curve < 3; curve++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("CrossFaderCurve", curve);
            tag.putInt("XfCurveV", 2);
            assertEquals(curve, MixerBlockEntity.readCrossFaderCurve(tag), "curve " + curve);
        }
    }
}
