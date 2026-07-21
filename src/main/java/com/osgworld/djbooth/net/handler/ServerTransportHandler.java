package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.deck.PlayState;
import com.osgworld.djbooth.net.TransportPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Applies a transport action to the target CDJ, with range + existence guards. */
public final class ServerTransportHandler {
    private static final double MAX_DIST_SQR = 64.0; // 8 blocks

    private ServerTransportHandler() {}

    public static void handle(TransportPayload msg, IPayloadContext ctx) {
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
            long now = player.level().getGameTime() * 50L; // ticks -> ms
            switch (msg.action()) {
                case TransportPayload.PLAY -> be.state().press(PlayState.PLAY, now);
                case TransportPayload.PAUSE -> be.state().press(PlayState.PAUSE, now);
                case TransportPayload.CUE -> be.state().press(PlayState.CUE, now);
                case TransportPayload.SET_CUE -> be.state().setCuePointMs(be.state().positionMsAt(now));
                case TransportPayload.LOOP_TOGGLE -> {
                    long p = be.state().positionMsAt(now);
                    if (be.state().isLoopOn()) {
                        be.state().setLoop(0, 0, false);
                    } else {
                        be.state().setLoop(p, p + 4000, true); // simple 4s loop from here
                    }
                }
                default -> {
                    return;
                }
            }
            be.applyAndSync();
        });
    }
}
