package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.net.HotCuePayload;
import net.minecraft.server.level.ServerPlayer;
import dev.architectury.networking.NetworkManager;

/** Sets / jumps / clears a hot cue on the target CDJ, with range + existence guards. */
public final class ServerHotCueHandler {
    private static final double MAX_DIST_SQR = 64.0;

    private ServerHotCueHandler() {}

    public static void handle(HotCuePayload msg, NetworkManager.PacketContext ctx) {
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
            if (!(player.level().getBlockEntity(msg.pos()) instanceof CdjBlockEntity be)) {
                return;
            }
            long now = player.level().getGameTime() * 50L;
            switch (msg.action()) {
                case HotCuePayload.SET -> be.state().setHotCue(msg.index(), now);
                case HotCuePayload.JUMP -> be.state().jumpHotCue(msg.index(), now);
                case HotCuePayload.CLEAR -> be.state().clearHotCue(msg.index());
                default -> {
                    return;
                }
            }
            be.applyAndSync();
        });
    }
}
