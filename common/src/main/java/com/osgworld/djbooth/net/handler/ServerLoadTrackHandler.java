package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.deck.PlayState;
import com.osgworld.djbooth.net.LoadTrackPayload;
import net.minecraft.server.level.ServerPlayer;
import dev.architectury.networking.NetworkManager;

/** Loads a track URL onto a deck and parks it at the start, with range + existence guards. */
public final class ServerLoadTrackHandler {
    private static final double MAX_DIST_SQR = 64.0; // 8 blocks
    private static final int MAX_URL = 1024;

    private ServerLoadTrackHandler() {}

    public static void handle(LoadTrackPayload msg, NetworkManager.PacketContext ctx) {
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
            String url = msg.url() == null ? "" : msg.url().trim();
            if (url.length() > MAX_URL) {
                url = url.substring(0, MAX_URL);
            }
            be.state().setTrackUrl(url);
            be.state().setPlayState(PlayState.STOP);
            be.state().setOffsetMs(0);
            be.state().setStartEpochMs(0);
            be.applyAndSync();
        });
    }
}
