package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DJBooth.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CdjBlockEntity>> CDJ =
            BLOCK_ENTITIES.register("cdj",
                    () -> BlockEntityType.Builder.of(CdjBlockEntity::new, ModBlocks.CDJ.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MixerBlockEntity>> MIXER =
            BLOCK_ENTITIES.register("mixer",
                    () -> BlockEntityType.Builder.of(MixerBlockEntity::new, ModBlocks.MIXER.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
