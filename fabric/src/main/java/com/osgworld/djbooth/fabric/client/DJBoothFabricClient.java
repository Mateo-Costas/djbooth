package com.osgworld.djbooth.fabric.client;

import com.osgworld.djbooth.client.DJBoothClient;
import com.osgworld.djbooth.client.screen.BoothScreen;
import com.osgworld.djbooth.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/** Fabric client entry point: common tick setup + the booth menu screen (registries are ready here). */
public final class DJBoothFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DJBoothClient.init();
        MenuScreens.register(ModMenus.BOOTH.get(), BoothScreen::new);
    }
}
