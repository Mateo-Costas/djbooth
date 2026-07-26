package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import com.osgworld.djbooth.net.MixerPayload;
import net.minecraft.server.level.ServerPlayer;
import dev.architectury.networking.NetworkManager;

/** Applies a channel value change to the target mixer. */
public final class ServerMixerHandler {
    private static final double MAX_DIST_SQR = 64.0;

    private ServerMixerHandler() {}

    public static void handle(MixerPayload msg, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) {
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
                case MixerPayload.FADER_CURVE -> be.setChFaderCurve(Math.round(msg.value()));
                case MixerPayload.GAIN_A -> be.setGainA(msg.value());
                case MixerPayload.GAIN_B -> be.setGainB(msg.value());
                case MixerPayload.XF_ASSIGN_A -> be.setXfAssignA(Math.round(msg.value()));
                case MixerPayload.XF_ASSIGN_B -> be.setXfAssignB(Math.round(msg.value()));
                case MixerPayload.COLOR_MODE -> be.setColorMode(Math.round(msg.value()));
                case MixerPayload.COLOR_PARAM -> be.setColorParam(msg.value());
                case MixerPayload.BEATFX_TYPE -> be.setBeatFxType(Math.round(msg.value()));
                case MixerPayload.BEATFX_BEAT -> be.setBeatFxBeat(Math.round(msg.value()));
                case MixerPayload.BEATFX_DEPTH -> be.setBeatFxDepth(msg.value());
                case MixerPayload.BEATFX_BANDS -> be.setBeatFxBands(Math.round(msg.value()));
                case MixerPayload.BEATFX_CHANNEL -> be.setBeatFxChannel(Math.round(msg.value()));
                case MixerPayload.BEATFX_ON -> be.setBeatFxOn(msg.value() > 0.5f);
                case MixerPayload.BPM -> be.setBpm(msg.value());
                case MixerPayload.BALANCE -> be.setBalance(msg.value());
                case MixerPayload.BOOTH -> be.setBooth(msg.value());
                case MixerPayload.CUE_A -> be.setCueA(msg.value() > 0.5f);
                case MixerPayload.CUE_B -> be.setCueB(msg.value() > 0.5f);
                case MixerPayload.CROSSFADER_CURVE -> be.setCrossFaderCurve(Math.round(msg.value()));
                default -> { return; }
            }
            be.applyAndSync();
        });
    }
}
