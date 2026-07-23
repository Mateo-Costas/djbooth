package com.osgworld.djbooth.neoforge;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.client.DJBoothClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** NeoForge entry point: hands off to the common initializer (and client setup on the client). */
@Mod(DJBooth.MODID)
public final class DJBoothNeoForge {
    public DJBoothNeoForge() {
        DJBooth.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DJBoothClient.init();
        }
    }
}
