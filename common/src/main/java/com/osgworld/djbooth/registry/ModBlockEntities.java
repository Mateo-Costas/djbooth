package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(DJBooth.MODID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<CdjBlockEntity>> CDJ =
            BLOCK_ENTITIES.register("cdj",
                    () -> BlockEntityType.Builder.of(CdjBlockEntity::new, ModBlocks.CDJ.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<MixerBlockEntity>> MIXER =
            BLOCK_ENTITIES.register("mixer",
                    () -> BlockEntityType.Builder.of(MixerBlockEntity::new, ModBlocks.MIXER.get()).build(null));

    private ModBlockEntities() {}

    public static void register() {
        BLOCK_ENTITIES.register();
    }
}
