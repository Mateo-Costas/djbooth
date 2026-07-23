package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: jog wheel interaction. {@code rate} is the new absolute tempo multiplier.
 * {@code scrubToMs} = -1 means "no scrub, just set rate"; otherwise seek to that position.
 */
public record JogNudgePayload(BlockPos pos, double rate, long scrubToMs) implements CustomPacketPayload {
    public static final Type<JogNudgePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "jog_nudge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JogNudgePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, JogNudgePayload::pos,
            ByteBufCodecs.DOUBLE, JogNudgePayload::rate,
            ByteBufCodecs.VAR_LONG, JogNudgePayload::scrubToMs,
            JogNudgePayload::new);

    @Override
    public Type<JogNudgePayload> type() {
        return TYPE;
    }
}
