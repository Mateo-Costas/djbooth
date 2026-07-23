package com.osgworld.djbooth.blockentity;

import com.osgworld.djbooth.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Mixer state stub for Plan 01: holds fader/crossfader/master values and persists them.
 * Deck binding and mixing math land in Plan 03.
 */
public class MixerBlockEntity extends BlockEntity {
    private float faderA = 1.0f;
    private float faderB = 1.0f;
    private float crossfader = 0.5f; // 0 = full A, 1 = full B
    private float master = 1.0f;

    // Per-channel EQ + colour filter, 0..1 with 0.5 = flat/bypass. Deck A first, then deck B.
    private float eqLowA = 0.5f, eqMidA = 0.5f, eqHiA = 0.5f, filterA = 0.5f;
    private float eqLowB = 0.5f, eqMidB = 0.5f, eqHiB = 0.5f, filterB = 0.5f;

    public MixerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MIXER.get(), pos, blockState);
    }

    public float getFaderA() { return faderA; }
    public float getFaderB() { return faderB; }
    public float getCrossfader() { return crossfader; }
    public float getMaster() { return master; }

    public void setFaderA(float v) { this.faderA = clamp01(v); }
    public void setFaderB(float v) { this.faderB = clamp01(v); }
    public void setCrossfader(float v) { this.crossfader = clamp01(v); }
    public void setMaster(float v) { this.master = clamp01(v); }

    public float getEqLowA() { return eqLowA; }
    public float getEqMidA() { return eqMidA; }
    public float getEqHiA() { return eqHiA; }
    public float getFilterA() { return filterA; }
    public float getEqLowB() { return eqLowB; }
    public float getEqMidB() { return eqMidB; }
    public float getEqHiB() { return eqHiB; }
    public float getFilterB() { return filterB; }

    public void setEqLowA(float v) { this.eqLowA = clamp01(v); }
    public void setEqMidA(float v) { this.eqMidA = clamp01(v); }
    public void setEqHiA(float v) { this.eqHiA = clamp01(v); }
    public void setFilterA(float v) { this.filterA = clamp01(v); }
    public void setEqLowB(float v) { this.eqLowB = clamp01(v); }
    public void setEqMidB(float v) { this.eqMidB = clamp01(v); }
    public void setEqHiB(float v) { this.eqHiB = clamp01(v); }
    public void setFilterB(float v) { this.filterB = clamp01(v); }

    /** Deck's EQ knobs as {low, mid, high, filter}, each 0..1 (0.5 = flat). */
    public float[] eqForDeck(boolean deckA) {
        return deckA
                ? new float[]{eqLowA, eqMidA, eqHiA, filterA}
                : new float[]{eqLowB, eqMidB, eqHiB, filterB};
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    /**
     * Effective output volume (0..1) for one deck, folding in its channel fader, the
     * crossfader weight and the master. Crossfader 0 = full A, 1 = full B. This is the
     * value the per-deck audio player scales its gain by (Plan 02b).
     */
    public float volumeForDeck(boolean deckA) {
        float channel = deckA ? faderA : faderB;
        float xf = deckA ? (1.0f - crossfader) : crossfader;
        return clamp01(channel * xf * master);
    }

    public void applyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("FaderA", faderA);
        tag.putFloat("FaderB", faderB);
        tag.putFloat("Crossfader", crossfader);
        tag.putFloat("Master", master);
        tag.putFloat("EqLowA", eqLowA);
        tag.putFloat("EqMidA", eqMidA);
        tag.putFloat("EqHiA", eqHiA);
        tag.putFloat("FilterA", filterA);
        tag.putFloat("EqLowB", eqLowB);
        tag.putFloat("EqMidB", eqMidB);
        tag.putFloat("EqHiB", eqHiB);
        tag.putFloat("FilterB", filterB);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        faderA = tag.contains("FaderA") ? tag.getFloat("FaderA") : 1.0f;
        faderB = tag.contains("FaderB") ? tag.getFloat("FaderB") : 1.0f;
        crossfader = tag.contains("Crossfader") ? tag.getFloat("Crossfader") : 0.5f;
        master = tag.contains("Master") ? tag.getFloat("Master") : 1.0f;
        eqLowA = tag.contains("EqLowA") ? tag.getFloat("EqLowA") : 0.5f;
        eqMidA = tag.contains("EqMidA") ? tag.getFloat("EqMidA") : 0.5f;
        eqHiA = tag.contains("EqHiA") ? tag.getFloat("EqHiA") : 0.5f;
        filterA = tag.contains("FilterA") ? tag.getFloat("FilterA") : 0.5f;
        eqLowB = tag.contains("EqLowB") ? tag.getFloat("EqLowB") : 0.5f;
        eqMidB = tag.contains("EqMidB") ? tag.getFloat("EqMidB") : 0.5f;
        eqHiB = tag.contains("EqHiB") ? tag.getFloat("EqHiB") : 0.5f;
        filterB = tag.contains("FilterB") ? tag.getFloat("FilterB") : 0.5f;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
