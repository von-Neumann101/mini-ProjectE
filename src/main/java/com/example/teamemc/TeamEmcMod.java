package com.example.teamemc;

import com.example.teamemc.command.TeamEmcCommand;
import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.registry.ModBlocks;
import com.example.teamemc.registry.ModCreativeTabs;
import com.example.teamemc.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TeamEmcMod.MOD_ID)
public final class TeamEmcMod {
    public static final String MOD_ID = "teamemc";

    public TeamEmcMod(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(TeamEmcCommand::register);
        NeoForge.EVENT_BUS.addListener(EmcValueManager::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(EmcValueManager::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(EmcValueManager::onServerStarted);
    }
}
