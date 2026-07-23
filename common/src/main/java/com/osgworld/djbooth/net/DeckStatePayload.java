package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C: full deck state broadcast so open screens update live. */
public record DeckStatePayload(BlockPos pos, int playState, double rate,
                               long offsetMs, long startEpochMs, String url)
        implements CustomPacketPayload {

    public static final Type<DeckStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "deck_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckStatePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DeckStatePayload::pos,
            ByteBufCodecs.VAR_INT, DeckStatePayload::playState,
            ByteBufCodecs.DOUBLE, DeckStatePayload::rate,
            ByteBufCodecs.VAR_LONG, DeckStatePayload::offsetMs,
            ByteBufCodecs.VAR_LONG, DeckStatePayload::startEpochMs,
            ByteBufCodecs.STRING_UTF8, DeckStatePayload::url,
            DeckStatePayload::new);

    @Override
    public Type<DeckStatePayload> type() {
        return TYPE;
    }
}
