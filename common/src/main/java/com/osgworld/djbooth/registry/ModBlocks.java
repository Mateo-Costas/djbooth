package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.block.CdjBlock;
import com.osgworld.djbooth.block.MixerBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(DJBooth.MODID, Registries.BLOCK);

    public static final RegistrySupplier<CdjBlock> CDJ = BLOCKS.register("cdj",
            () -> new CdjBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

    public static final RegistrySupplier<MixerBlock> MIXER = BLOCKS.register("mixer",
            () -> new MixerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

    private ModBlocks() {}

    public static void register() {
        BLOCKS.register();
    }
}
