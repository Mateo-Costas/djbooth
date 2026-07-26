package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: the tempo measured for a deck, from tapping along to its track.
 *
 * <p>The deck needs a tempo of its own before QUANTIZE can snap to a beat grid or BEAT SYNC can
 * pull it onto the other deck, and nothing analyses the audio, so the tap is where it comes from.
 */
public record DeckBpmPayload(BlockPos pos, float bpm) implements CustomPacketPayload {
    public static final Type<DeckBpmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "deck_bpm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckBpmPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DeckBpmPayload::pos,
                    ByteBufCodecs.FLOAT, DeckBpmPayload::bpm,
                    DeckBpmPayload::new);

    @Override
    public Type<DeckBpmPayload> type() {
        return TYPE;
    }
}
