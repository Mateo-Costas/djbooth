package com.osgworld.djbooth.net.handler;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import com.osgworld.djbooth.net.TransportPayload;
import net.minecraft.server.level.ServerPlayer;
import dev.architectury.networking.NetworkManager;

/** Applies a transport action to the target CDJ, with range + existence guards. */
public final class ServerTransportHandler {
    private static final double MAX_DIST_SQR = 64.0; // 8 blocks
    private static final long JUMP_MS = 4000; // beat-jump step (no BPM analysis, so a fixed hop)
    private static final long SEARCH_MS = 15000; // SEARCH scan step, a bigger hop than beat-jump

    private ServerTransportHandler() {}

    public static void handle(TransportPayload msg, NetworkManager.PacketContext ctx) {
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
            long now = player.level().getGameTime() * 50L; // ticks -> ms
            switch (msg.action()) {
                case TransportPayload.PLAY -> be.state().press(PlayState.PLAY, now);
                case TransportPayload.PAUSE -> be.state().press(PlayState.PAUSE, now);
                case TransportPayload.CUE -> be.state().cue(now);
                case TransportPayload.SET_CUE -> be.state().setCueHere(now);
                case TransportPayload.DIRECTION -> {
                    // Stepping into or out of reverse re-anchors the clock, otherwise the
                    // position jumps by however long the deck has been playing.
                    long p = be.state().positionMsAt(now);
                    be.state().setDirection(be.state().getDirection() + 1);
                    be.state().jumpTo(p, now);
                    // SLIP REV keeps the untouched timeline running underneath.
                    if (be.state().getDirection() == DeckState.DIR_SLIP_REV) {
                        be.state().setSlip(true);
                        be.state().beginSlip(now);
                    } else {
                        be.state().endSlip(now);
                    }
                }
                case TransportPayload.JOG_MODE ->
                        be.state().setJogMode(be.state().getJogMode() + 1);
                case TransportPayload.SLIP -> {
                    boolean next = !be.state().isSlip();
                    be.state().setSlip(next);
                    if (next) {
                        be.state().beginSlip(now);
                    } else {
                        be.state().endSlip(now);
                    }
                }
                case TransportPayload.QUANTIZE ->
                        be.state().setQuantize(!be.state().isQuantize());
                case TransportPayload.TEMPO_RESET -> be.state().resetTempo(now);
                case TransportPayload.MASTER_TEMPO ->
                        be.state().setMasterTempo(!be.state().isMasterTempo());
                case TransportPayload.BEAT_SYNC -> syncToOtherDeck(player, be, now);
                case TransportPayload.KEY_SYNC -> keySyncToOtherDeck(player, be);
                case TransportPayload.TRACK_START -> be.state().jumpTo(0, now);
                case TransportPayload.SEARCH_BACK ->
                        be.state().jumpTo(be.state().positionMsAt(now) - SEARCH_MS, now);
                case TransportPayload.SEARCH_FWD ->
                        be.state().jumpTo(be.state().positionMsAt(now) + SEARCH_MS, now);
                case TransportPayload.MEMORY -> be.state().memorise(now);
                case TransportPayload.CALL_PREV -> be.state().callMemory(-1, now);
                case TransportPayload.CALL_NEXT -> be.state().callMemory(1, now);
                case TransportPayload.MEMORY_DELETE -> be.state().deleteMemory(now);
                case TransportPayload.LOOP_TOGGLE -> {
                    long p = be.state().positionMsAt(now);
                    if (be.state().isLoopOn()) {
                        be.state().setLoop(0, 0, false);
                    } else {
                        be.state().setLoop(p, p + 4000, true); // simple 4s loop from here
                    }
                }
                case TransportPayload.LOOP_IN -> be.state().loopIn(now);
                case TransportPayload.LOOP_OUT -> be.state().loopOut(now);
                case TransportPayload.LOOP_EXIT -> be.state().loopExit();
                case TransportPayload.RELOOP -> be.state().reloop(now);
                case TransportPayload.LOOP_HALVE -> be.state().resizeLoop(0.5);
                case TransportPayload.LOOP_DOUBLE -> be.state().resizeLoop(2.0);
                case TransportPayload.JUMP_BACK ->
                        be.state().jumpTo(be.state().positionMsAt(now) - JUMP_MS, now);
                case TransportPayload.JUMP_FWD ->
                        be.state().jumpTo(be.state().positionMsAt(now) + JUMP_MS, now);
                default -> {
                    return;
                }
            }
            be.applyAndSync();
        });
    }

    /** BEAT SYNC: pull this deck's tempo onto the other deck in the same booth. */
    private static void syncToOtherDeck(net.minecraft.world.entity.player.Player player,
                                        CdjBlockEntity deck, long now) {
        var refs = com.osgworld.djbooth.booth.BoothRefs.scan(player.level(), deck.getBlockPos());
        net.minecraft.core.BlockPos otherPos = deck.getBlockPos().equals(refs.deckA())
                ? refs.deckB() : refs.deckA();
        if (otherPos == null
                || !(player.level().getBlockEntity(otherPos) instanceof CdjBlockEntity other)) {
            return;
        }
        deck.state().syncTo(other.state().getBpm(), now);
    }

    /** KEY SYNC: shift this deck into the other deck's key. Needs both keys to be known. */
    private static void keySyncToOtherDeck(net.minecraft.world.entity.player.Player player,
                                           CdjBlockEntity deck) {
        CdjBlockEntity other = otherDeck(player, deck);
        if (other == null) {
            return;
        }
        // Match what the other deck is actually sounding in, not its original key: if that deck is
        // itself key-shifted, syncing to its untouched key would leave the two apart.
        deck.state().keySyncTo(other.state().soundingKey());
    }

    /** The other CDJ in the same booth, or null. */
    private static CdjBlockEntity otherDeck(net.minecraft.world.entity.player.Player player,
                                            CdjBlockEntity deck) {
        var refs = com.osgworld.djbooth.booth.BoothRefs.scan(player.level(), deck.getBlockPos());
        net.minecraft.core.BlockPos otherPos = deck.getBlockPos().equals(refs.deckA())
                ? refs.deckB() : refs.deckA();
        return otherPos != null
                && player.level().getBlockEntity(otherPos) instanceof CdjBlockEntity other
                ? other : null;
    }
}
