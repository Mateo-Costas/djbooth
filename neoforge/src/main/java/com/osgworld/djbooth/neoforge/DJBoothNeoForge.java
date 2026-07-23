package com.osgworld.djbooth.neoforge;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.client.DJBoothClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** NeoForge entry point: hands off to the common initializer, and to the client setup on clients. */
@Mod(DJBooth.MODID)
public final class DJBoothNeoForge {
    public DJBoothNeoForge(IEventBus modBus) {
        DJBooth.init();
        // Client screen/tick setup must wait until the deferred registries have populated (NeoForge
        // registers on an event after mod construction, unlike Fabric which registers immediately),
        // otherwise the menu type isn't present yet.
        if (net.neoforged.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(DJBoothClient::init));
        }
    }
}
