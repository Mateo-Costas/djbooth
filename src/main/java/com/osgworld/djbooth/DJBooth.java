package com.osgworld.djbooth;

import com.osgworld.djbooth.registry.ModBlockEntities;
import com.osgworld.djbooth.registry.ModBlocks;
import com.osgworld.djbooth.registry.ModCreativeTabs;
import com.osgworld.djbooth.registry.ModItems;
import com.osgworld.djbooth.registry.ModMenus;
import com.osgworld.djbooth.registry.ModPayloads;
import com.osgworld.djbooth.booth.BoothCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DJ Booth mod entry point. Wires all registries onto the mod event bus. */
@Mod(DJBooth.MODID)
public final class DJBooth {
    public static final String MODID = "djbooth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public DJBooth(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModPayloads::register);
        NeoForge.EVENT_BUS.addListener(BoothCommands::onRegisterCommands);
    }
}
