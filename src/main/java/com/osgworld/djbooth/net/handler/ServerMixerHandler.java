package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import com.osgworld.djbooth.net.MixerPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies a channel value change to the target mixer. */
public final class ServerMixerHandler {
    private static final double MAX_DIST_SQR = 64.0;

    private ServerMixerHandler() {}

    public static void handle(MixerPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(msg.pos())) {
                return;
            }
            if (player.distanceToSqr(msg.pos().getCenter()) > MAX_DIST_SQR) {
                return;
            }
            if (!(player.level().getBlockEntity(msg.pos()) instanceof MixerBlockEntity be)) {
                return;
            }
            switch (msg.channel()) {
                case MixerPayload.FADER_A -> be.setFaderA(msg.value());
                case MixerPayload.FADER_B -> be.setFaderB(msg.value());
                case MixerPayload.CROSSFADER -> be.setCrossfader(msg.value());
                case MixerPayload.MASTER -> be.setMaster(msg.value());
                case MixerPayload.EQ_LOW_A -> be.setEqLowA(msg.value());
                case MixerPayload.EQ_MID_A -> be.setEqMidA(msg.value());
                case MixerPayload.EQ_HI_A -> be.setEqHiA(msg.value());
                case MixerPayload.FILTER_A -> be.setFilterA(msg.value());
                case MixerPayload.EQ_LOW_B -> be.setEqLowB(msg.value());
                case MixerPayload.EQ_MID_B -> be.setEqMidB(msg.value());
                case MixerPayload.EQ_HI_B -> be.setEqHiB(msg.value());
                case MixerPayload.FILTER_B -> be.setFilterB(msg.value());
                case MixerPayload.FX_ECHO_A -> be.setEchoA(msg.value());
                case MixerPayload.FX_ECHO_B -> be.setEchoB(msg.value());
                case MixerPayload.ISOLATOR -> be.setIsolator(msg.value() > 0.5f);
                case MixerPayload.FADER_CURVE -> be.setFaderSharp(msg.value() > 0.5f);
                default -> { return; }
            }
            be.applyAndSync();
        });
    }
}
