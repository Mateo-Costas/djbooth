package com.osgworld.djbooth.client;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.client.audio.DeckAudioManager;
import com.osgworld.djbooth.client.dmx.DmxDemo;
import com.osgworld.djbooth.client.screen.BoothScreen;
import com.osgworld.djbooth.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only setup: binds menu types to their screens and wires client game-bus handlers. */
@EventBusSubscriber(modid = DJBooth.MODID, value = Dist.CLIENT)
public final class DJBoothClient {
    private DJBoothClient() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BOOTH.get(), BoothScreen::new);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // DMX demo command + tick live on the game bus; register them once setup runs.
        NeoForge.EVENT_BUS.addListener(DmxDemo::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DmxDemo::onClientTick);
        // Per-deck audio sync (no-op until WaterMedia is installed).
        NeoForge.EVENT_BUS.addListener(DeckAudioManager::onClientTick);
    }
}
