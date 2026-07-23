package com.osgworld.djbooth.neoforge;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.client.DJBoothClient;
import com.osgworld.djbooth.client.screen.BoothScreen;
import com.osgworld.djbooth.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** NeoForge entry point: common init + client-side screen/tick setup at the right lifecycle events. */
@Mod(DJBooth.MODID)
public final class DJBoothNeoForge {
    public DJBoothNeoForge(IEventBus modBus) {
        DJBooth.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Screen factories go through NeoForge's own event (the reliable time to register them);
            // tick handlers run after setup once the deferred registries have populated.
            modBus.addListener(DJBoothNeoForge::onRegisterScreens);
            modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(DJBoothClient::init));
        }
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BOOTH.get(), BoothScreen::new);
    }
}
