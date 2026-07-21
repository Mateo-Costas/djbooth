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

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        faderA = tag.contains("FaderA") ? tag.getFloat("FaderA") : 1.0f;
        faderB = tag.contains("FaderB") ? tag.getFloat("FaderB") : 1.0f;
        crossfader = tag.contains("Crossfader") ? tag.getFloat("Crossfader") : 0.5f;
        master = tag.contains("Master") ? tag.getFloat("Master") : 1.0f;
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
