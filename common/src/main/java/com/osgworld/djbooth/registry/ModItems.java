package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(DJBooth.MODID, Registries.ITEM);

    public static final RegistrySupplier<BlockItem> CDJ = ITEMS.register("cdj",
            () -> new BlockItem(ModBlocks.CDJ.get(), new Item.Properties()));
    public static final RegistrySupplier<BlockItem> MIXER = ITEMS.register("mixer",
            () -> new BlockItem(ModBlocks.MIXER.get(), new Item.Properties()));

    private ModItems() {}

    public static void register() {
        ITEMS.register();
    }
}
