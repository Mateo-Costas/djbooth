package com.osgworld.djbooth.client;

import com.osgworld.djbooth.client.audio.DeckAudioManager;
import com.osgworld.djbooth.client.dmx.DmxDemo;
import com.osgworld.djbooth.client.screen.BoothScreen;
import com.osgworld.djbooth.registry.ModMenus;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.menu.MenuRegistry;

/** Common client setup: binds the booth menu to its screen and wires the client tick handlers. */
public final class DJBoothClient {
    private DJBoothClient() {}

    public static void init() {
        MenuRegistry.registerScreenFactory(ModMenus.BOOTH.get(), BoothScreen::new);
        ClientTickEvent.CLIENT_POST.register(minecraft -> {
            // Per-deck audio sync (no-op until WaterMedia is installed) + DMX demo sweep.
            DeckAudioManager.onClientTick();
            DmxDemo.onClientTick();
        });
    }
}
