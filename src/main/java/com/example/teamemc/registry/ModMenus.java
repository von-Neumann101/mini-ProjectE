package com.example.teamemc.registry;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.menu.TransmutationMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TeamEmcMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TransmutationMenu>> TRANSMUTATION_MENU =
            MENUS.register("transmutation_menu", () -> new MenuType<>(TransmutationMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
