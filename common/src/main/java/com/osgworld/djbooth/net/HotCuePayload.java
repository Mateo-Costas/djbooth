package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: a hot-cue pad on the CDJ at {@code pos} was used. */
public record HotCuePayload(BlockPos pos, int index, int action) implements CustomPacketPayload {
    public static final int JUMP = 0, SET = 1, CLEAR = 2;

    public static final Type<HotCuePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "hot_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HotCuePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HotCuePayload::pos,
            ByteBufCodecs.VAR_INT, HotCuePayload::index,
            ByteBufCodecs.VAR_INT, HotCuePayload::action,
            HotCuePayload::new);

    @Override
    public Type<HotCuePayload> type() {
        return TYPE;
    }
}
