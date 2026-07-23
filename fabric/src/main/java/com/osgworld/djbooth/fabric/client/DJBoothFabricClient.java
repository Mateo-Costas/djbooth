package com.osgworld.djbooth.fabric.client;

import com.osgworld.djbooth.client.DJBoothClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point: hands off to the common client initializer. */
public final class DJBoothFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DJBoothClient.init();
    }
}
