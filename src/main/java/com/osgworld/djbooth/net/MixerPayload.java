package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: set one mixer channel value.
 * channel: 0 = fader A, 1 = fader B, 2 = crossfader, 3 = master.
 */
public record MixerPayload(BlockPos pos, int channel, float value) implements CustomPacketPayload {

    public static final int FADER_A = 0;
    public static final int FADER_B = 1;
    public static final int CROSSFADER = 2;
    public static final int MASTER = 3;
    // EQ + colour filter per deck (0..1, 0.5 = flat).
    public static final int EQ_LOW_A = 4;
    public static final int EQ_MID_A = 5;
    public static final int EQ_HI_A = 6;
    public static final int FILTER_A = 7;
    public static final int EQ_LOW_B = 8;
    public static final int EQ_MID_B = 9;
    public static final int EQ_HI_B = 10;
    public static final int FILTER_B = 11;

    public static final Type<MixerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "mixer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MixerPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MixerPayload::pos,
                    ByteBufCodecs.VAR_INT, MixerPayload::channel,
                    ByteBufCodecs.FLOAT, MixerPayload::value,
                    MixerPayload::new);

    @Override
    public Type<MixerPayload> type() {
        return TYPE;
    }
}
