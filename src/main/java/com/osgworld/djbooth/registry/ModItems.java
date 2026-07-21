package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DJBooth.MODID);

    public static final DeferredItem<BlockItem> CDJ = ITEMS.registerSimpleBlockItem(ModBlocks.CDJ);
    public static final DeferredItem<BlockItem> MIXER = ITEMS.registerSimpleBlockItem(ModBlocks.MIXER);

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
