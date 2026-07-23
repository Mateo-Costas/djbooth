package com.osgworld.djbooth.booth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.deck.PlayState;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Server command to load a track onto a deck: {@code /djbooth track <url>} points the nearest
 * deck at a streaming URL and parks it at the start. WaterMedia on each client then streams it
 * (see DeckAudioManager). Until a real track-picker GUI exists, this is how a track gets set.
 */
public final class BoothCommands {
    private BoothCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("djbooth")
                .then(Commands.literal("track")
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String url = StringArgumentType.getString(ctx, "url");
                                    return setNearestDeck(player, url);
                                }))));
    }

    private static int setNearestDeck(ServerPlayer player, String url) {
        Level level = player.level();
        BoothRefs refs = BoothRefs.scan(level, player.blockPosition());
        BlockPos target = nearest(player.blockPosition(), refs.deckA(), refs.deckB());
        if (target == null) {
            player.sendSystemMessage(Component.literal(
                    "No CDJ deck nearby. Stand next to a deck and try again."));
            return 0;
        }
        if (!(level.getBlockEntity(target) instanceof CdjBlockEntity deck)) {
            return 0;
        }
        deck.state().setTrackUrl(url);
        deck.state().setPlayState(PlayState.STOP);
        deck.state().setOffsetMs(0);
        deck.state().setStartEpochMs(0);
        deck.applyAndSync();
        player.sendSystemMessage(Component.literal("Loaded track onto deck " + target.toShortString()));
        return 1;
    }

    private static BlockPos nearest(BlockPos from, BlockPos a, BlockPos b) {
        if (a == null) return b;
        if (b == null) return a;
        return from.distSqr(a) <= from.distSqr(b) ? a : b;
    }
}
