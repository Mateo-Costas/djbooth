package com.osgworld.djbooth;

import com.osgworld.djbooth.booth.BoothCommands;
import com.osgworld.djbooth.registry.ModBlockEntities;
import com.osgworld.djbooth.registry.ModBlocks;
import com.osgworld.djbooth.registry.ModCreativeTabs;
import com.osgworld.djbooth.registry.ModItems;
import com.osgworld.djbooth.registry.ModMenus;
import com.osgworld.djbooth.registry.ModPayloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (loader-agnostic) mod entry point. Called by the Fabric and NeoForge initializers. */
public final class DJBooth {
    public static final String MODID = "djbooth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private DJBooth() {}

    public static void init() {
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModMenus.register();
        ModCreativeTabs.register();
        ModPayloads.register();
        BoothCommands.register();
    }
}
