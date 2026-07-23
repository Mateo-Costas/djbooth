package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.menu.BoothMenu;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(DJBooth.MODID, Registries.MENU);

    // Extended menu carries the opened block's position (written by BoothInteraction) to the client.
    public static final RegistrySupplier<MenuType<BoothMenu>> BOOTH = MENUS.register("booth",
            () -> MenuRegistry.ofExtended((id, inv, buf) -> new BoothMenu(id, inv, buf)));

    private ModMenus() {}

    public static void register() {
        MENUS.register();
    }
}
