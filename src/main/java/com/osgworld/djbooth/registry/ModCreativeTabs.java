package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DJBooth.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DJBOOTH = TABS.register("djbooth",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.djbooth"))
                    .icon(() -> new ItemStack(ModItems.CDJ.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.CDJ.get());
                        output.accept(ModItems.MIXER.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
