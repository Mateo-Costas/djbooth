package com.osgworld.djbooth.client;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.client.screen.CdjScreen;
import com.osgworld.djbooth.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only setup: binds menu types to their screens. */
@EventBusSubscriber(modid = DJBooth.MODID, value = Dist.CLIENT)
public final class DJBoothClient {
    private DJBoothClient() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CDJ.get(), CdjScreen::new);
    }
}
