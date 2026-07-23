package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: load a streaming URL onto a deck from the booth GUI. */
public record LoadTrackPayload(BlockPos pos, String url) implements CustomPacketPayload {

    public static final Type<LoadTrackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "load_track"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadTrackPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LoadTrackPayload::pos,
            ByteBufCodecs.STRING_UTF8, LoadTrackPayload::url,
            LoadTrackPayload::new);

    @Override
    public Type<LoadTrackPayload> type() {
        return TYPE;
    }
}
