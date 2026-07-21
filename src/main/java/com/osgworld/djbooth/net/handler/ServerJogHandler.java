package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.net.JogNudgePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies a jog/tempo change (and optional scrub) to the target CDJ. */
public final class ServerJogHandler {
    private static final double MAX_DIST_SQR = 64.0;

    private ServerJogHandler() {}

    public static void handle(JogNudgePayload msg, IPayloadContext ctx) {
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
            if (!(player.level().getBlockEntity(msg.pos()) instanceof CdjBlockEntity be)) {
                return;
            }
            long now = player.level().getGameTime() * 50L;
            if (msg.scrubToMs() >= 0) {
                // Scrub: jump to target, keep play state, then apply rate from here.
                be.state().setOffsetMs(msg.scrubToMs());
                be.state().setStartEpochMs(now);
                be.state().setRate(msg.rate());
            } else {
                // Tempo change without teleporting.
                be.state().setRateAt(msg.rate(), now);
            }
            be.applyAndSync();
        });
    }
}
