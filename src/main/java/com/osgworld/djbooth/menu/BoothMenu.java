package com.osgworld.djbooth.menu;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import com.osgworld.djbooth.booth.BoothRefs;
import com.osgworld.djbooth.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Container menu for the whole booth: up to two decks + a mixer.
 * No item slots — it only carries the block positions so the screen can read/write state.
 */
public class BoothMenu extends AbstractContainerMenu {
    private final BoothRefs refs;
    private final Level level;

    /** Client-side constructor: decode the booth block positions. */
    public BoothMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, BoothRefs.read(buf));
    }

    /** Server-side constructor. */
    public BoothMenu(int id, Inventory inv, BoothRefs refs) {
        super(ModMenus.BOOTH.get(), id);
        this.refs = refs;
        this.level = inv.player.level();
    }

    public BoothRefs refs() {
        return refs;
    }

    @Nullable
    public CdjBlockEntity deck(@Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof CdjBlockEntity cdj ? cdj : null;
    }

    @Nullable
    public MixerBlockEntity mixer() {
        if (refs.mixer() == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(refs.mixer());
        return be instanceof MixerBlockEntity m ? m : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockPos anchor = refs.anchor();
        return anchor != null && player.distanceToSqr(anchor.getCenter()) <= 64.0;
    }
}
