package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(DJBooth.MODID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> DJBOOTH = TABS.register("djbooth",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.djbooth"))
                    .icon(() -> new ItemStack(ModItems.CDJ.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.CDJ.get());
                        output.accept(ModItems.MIXER.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register() {
        TABS.register();
    }
}
