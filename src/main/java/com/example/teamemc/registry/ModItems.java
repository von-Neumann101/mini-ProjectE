package com.example.teamemc.registry;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.item.PortableTransmutationTableItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeamEmcMod.MOD_ID);

    public static final DeferredItem<BlockItem> TRANSMUTATION_TABLE = ITEMS.registerSimpleBlockItem(
            "transmutation_table",
            ModBlocks.TRANSMUTATION_TABLE
    );

    public static final DeferredItem<PortableTransmutationTableItem> PORTABLE_TRANSMUTATION_TABLE = ITEMS.register(
            "portable_transmutation_table",
            () -> new PortableTransmutationTableItem(new Item.Properties())
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
