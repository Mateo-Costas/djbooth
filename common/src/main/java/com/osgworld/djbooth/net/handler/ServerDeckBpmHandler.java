package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.net.DeckBpmPayload;
import dev.architectury.networking.NetworkManager;

/** Applies a tapped tempo to a deck. */
public final class ServerDeckBpmHandler {
    private ServerDeckBpmHandler() {}

    private static final double MAX_DIST_SQR = 64.0;

    public static void handle(DeckBpmPayload msg, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            var player = ctx.getPlayer();
            if (player == null || !player.level().isLoaded(msg.pos())) {
                return;
            }
            if (player.distanceToSqr(msg.pos().getCenter()) > MAX_DIST_SQR) {
                return;
            }
            if (!(player.level().getBlockEntity(msg.pos()) instanceof CdjBlockEntity be)) {
                return;
            }
            be.state().setBpm(msg.bpm());
            be.applyAndSync();
        });
    }
}
