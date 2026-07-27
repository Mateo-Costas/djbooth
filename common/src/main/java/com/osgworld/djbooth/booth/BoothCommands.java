package com.osgworld.djbooth.booth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.client.dmx.DmxBridge;
import com.osgworld.djbooth.client.dmx.DmxDemo;
import com.osgworld.djbooth.deck.PlayState;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Server commands: {@code /soundsystem track <url>} points the nearest deck at a streaming URL and
 * parks it at the start (WaterMedia on each client then streams it); {@code /soundsystem dmxtest}
 * runs the DMX rainbow smoke test.
 */
public final class BoothCommands {
    private BoothCommands() {}

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                // Not the mod id: nobody wants to type an underscore mid-command.
                dispatcher.register(Commands.literal("soundsystem")
                        .then(Commands.literal("track")
                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String url = StringArgumentType.getString(ctx, "url");
                                            return setNearestDeck(player, url);
                                        })))
                        .then(Commands.literal("dmxtest")
                                .executes(ctx -> dmxTest(ctx.getSource())))));
    }

    /**
     * Run the DMX smoke test.
     *
     * <p>The bridge sends its packets over UDP to localhost, because the lighting software runs on
     * the same machine as the player. That means this only does anything useful when the server
     * <em>is</em> the player's machine — on a dedicated server the packets would leave from the
     * server box and never reach anyone's lights, so say so rather than reporting a success that
     * didn't happen.
     */
    private static int dmxTest(CommandSourceStack source) {
        if (source.getServer().isDedicatedServer()) {
            source.sendFailure(Component.literal(
                    "Soundsystem DJ: the DMX bridge sends to localhost, so this test only works in "
                            + "single player or on a LAN world hosted by the machine running the "
                            + "lights."));
            return 0;
        }
        int n = DmxDemo.trigger();
        source.sendSuccess(() -> Component.literal(
                "Soundsystem DJ: sending DMX test to fixtures 1-" + n
                        + " on udp/" + DmxBridge.PORT + " for ~6s"), false);
        return 1;
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
