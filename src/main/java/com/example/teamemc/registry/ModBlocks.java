package com.example.teamemc.registry;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.block.TransmutationTableBlock;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TeamEmcMod.MOD_ID);

    public static final DeferredBlock<TransmutationTableBlock> TRANSMUTATION_TABLE = BLOCKS.register(
            "transmutation_table",
            () -> new TransmutationTableBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.5F)
                            .requiresCorrectToolForDrops()
            )
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
