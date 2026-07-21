package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.block.CdjBlock;
import com.osgworld.djbooth.block.MixerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DJBooth.MODID);

    public static final DeferredBlock<CdjBlock> CDJ = BLOCKS.register("cdj",
            () -> new CdjBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

    public static final DeferredBlock<MixerBlock> MIXER = BLOCKS.register("mixer",
            () -> new MixerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
