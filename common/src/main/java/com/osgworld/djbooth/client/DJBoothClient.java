package com.osgworld.djbooth.client;

import com.osgworld.djbooth.client.audio.DeckAudioManager;
import com.osgworld.djbooth.client.dmx.DmxDemo;
import dev.architectury.event.events.client.ClientTickEvent;

/**
 * Common client setup: wires the per-tick handlers. The booth menu screen is registered by each
 * loader at its own correct time (see the Fabric/NeoForge client entry points), because the timing
 * of screen-factory registration differs between loaders.
 */
public final class DJBoothClient {
    private DJBoothClient() {}

    public static void init() {
        ClientTickEvent.CLIENT_POST.register(minecraft -> {
            // Per-deck audio sync (no-op until WaterMedia is installed) + DMX demo sweep.
            DeckAudioManager.onClientTick();
            DmxDemo.onClientTick();
        });
    }
}
