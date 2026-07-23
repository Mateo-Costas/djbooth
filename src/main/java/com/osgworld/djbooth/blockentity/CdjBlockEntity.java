package com.osgworld.djbooth.blockentity;

import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
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

/** Holds one deck's transport state and keeps it persisted + synced to clients. */
public class CdjBlockEntity extends BlockEntity {
    private final DeckState state = new DeckState();

    /** Client-side set of loaded decks, so the audio engine can find them each tick. */
    public static final java.util.Set<CdjBlockEntity> CLIENT_DECKS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public CdjBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CDJ.get(), pos, blockState);
    }

    public DeckState state() {
        return state;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT_DECKS.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_DECKS.remove(this);
    }

    /** Server-side: persist the current state and push it to tracking clients. */
    public void applyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Url", state.getTrackUrl());
        tag.putString("Play", state.getPlayState().name());
        tag.putDouble("Rate", state.getRate());
        tag.putLong("Cue", state.getCuePointMs());
        tag.putLong("Offset", state.getOffsetMs());
        tag.putLong("Start", state.getStartEpochMs());
        tag.putBoolean("LoopOn", state.isLoopOn());
        tag.putLong("LoopIn", state.getLoopInMs());
        tag.putLong("LoopOut", state.getLoopOutMs());
        long[] hc = new long[DeckState.HOT_CUES];
        for (int i = 0; i < hc.length; i++) {
            hc[i] = state.getHotCue(i);
        }
        tag.putLongArray("HotCues", hc);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        state.setTrackUrl(tag.getString("Url"));
        state.setPlayState(tag.getString("Play").isEmpty()
                ? PlayState.STOP : PlayState.valueOf(tag.getString("Play")));
        state.setRate(tag.contains("Rate") ? tag.getDouble("Rate") : 1.0);
        state.setCuePointMs(tag.getLong("Cue"));
        state.setOffsetMs(tag.getLong("Offset"));
        state.setStartEpochMs(tag.getLong("Start"));
        state.setLoop(tag.getLong("LoopIn"), tag.getLong("LoopOut"), tag.getBoolean("LoopOn"));
        long[] hc = tag.getLongArray("HotCues");
        for (int i = 0; i < DeckState.HOT_CUES && i < hc.length; i++) {
            state.loadHotCue(i, hc[i]);
        }
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
