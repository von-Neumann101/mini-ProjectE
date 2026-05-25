package com.example.teamemc;

import com.example.teamemc.client.ClientSetup;
import com.example.teamemc.command.TeamEmcCommand;
import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.registry.ModBlocks;
import com.example.teamemc.registry.ModCreativeTabs;
import com.example.teamemc.registry.ModItems;
import com.example.teamemc.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TeamEmcMod.MOD_ID)
public final class TeamEmcMod {
    public static final String MOD_ID = "teamemc";

    public TeamEmcMod(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.register(modEventBus);
        }

        NeoForge.EVENT_BUS.addListener(TeamEmcCommand::register);
        NeoForge.EVENT_BUS.addListener(EmcValueManager::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(EmcValueManager::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(EmcValueManager::onServerStarted);
    }
}
