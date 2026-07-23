package com.osgworld.djbooth.fabric;

import com.osgworld.djbooth.DJBooth;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point: hands off to the common initializer. */
public final class DJBoothFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        DJBooth.init();
    }
}
