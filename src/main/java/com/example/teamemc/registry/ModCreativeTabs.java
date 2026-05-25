package com.example.teamemc.registry;

import com.example.teamemc.TeamEmcMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TeamEmcMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TEAM_EMC_TAB =
            CREATIVE_TABS.register("team_emc", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.teamemc"))
                    .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                    .icon(() -> new ItemStack(ModItems.TRANSMUTATION_TABLE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TRANSMUTATION_TABLE.get());
                        output.accept(ModItems.PORTABLE_TRANSMUTATION_TABLE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
