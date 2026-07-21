package com.osgworld.djbooth.menu;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.registry.ModBlocks;
import com.osgworld.djbooth.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Container menu for a single CDJ deck. No item slots; carries transport state only. */
public class CdjMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Level level;
    private final ContainerLevelAccess access;

    /** Client-side constructor: decode the target position from the buffer. */
    public CdjMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    /** Server-side constructor. */
    public CdjMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.CDJ.get(), id);
        this.pos = pos;
        this.level = inv.player.level();
        this.access = ContainerLevelAccess.create(level, pos);
    }

    public BlockPos pos() {
        return pos;
    }

    public CdjBlockEntity blockEntity() {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof CdjBlockEntity cdj ? cdj : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CDJ.get());
    }
}
