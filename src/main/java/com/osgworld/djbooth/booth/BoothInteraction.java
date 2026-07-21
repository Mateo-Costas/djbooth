package com.osgworld.djbooth.booth;

import com.osgworld.djbooth.menu.BoothMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Opens the combined booth GUI, scanning around the clicked block for its decks + mixer. */
public final class BoothInteraction {
    private BoothInteraction() {}

    public static void open(ServerPlayer player, BlockPos clicked) {
        BoothRefs refs = BoothRefs.scan(player.level(), clicked);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new BoothMenu(id, inv, refs),
                Component.translatable("gui.djbooth.booth")), refs::write);
    }
}
