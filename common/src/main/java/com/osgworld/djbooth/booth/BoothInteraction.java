package com.osgworld.djbooth.booth;

import com.osgworld.djbooth.menu.BoothMenu;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** Opens the combined booth GUI, scanning around the clicked block for its decks + mixer. */
public final class BoothInteraction {
    private BoothInteraction() {}

    public static void open(ServerPlayer player, BlockPos clicked) {
        BoothRefs refs = BoothRefs.scan(player.level(), clicked);
        MenuRegistry.openExtendedMenu(player, new ExtendedMenuProvider() {
            @Override
            public void saveExtraData(FriendlyByteBuf buf) {
                refs.write(buf);
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.soundsystem_dj.booth");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BoothMenu(id, inv, refs);
            }
        });
    }
}
