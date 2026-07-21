package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: a transport button was pressed on the CDJ at {@code pos}. */
public record TransportPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int PLAY = 0, PAUSE = 1, CUE = 2, SET_CUE = 3, LOOP_TOGGLE = 4;

    public static final Type<TransportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "transport"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransportPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TransportPayload::pos,
            ByteBufCodecs.VAR_INT, TransportPayload::action,
            TransportPayload::new);

    @Override
    public Type<TransportPayload> type() {
        return TYPE;
    }
}
