package com.example.teamemc.client;

import com.example.teamemc.registry.ModMenus;
import com.example.teamemc.registry.ModItems;
import com.example.teamemc.network.RequestOpenPortableTablePacket;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    private static final KeyMapping OPEN_PORTABLE_TABLE = new KeyMapping(
            "key.teamemc.open_portable_table",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.categories.teamemc"
    );

    private ClientSetup() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::registerMenuScreens);
        modEventBus.addListener(ClientSetup::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientTooltipHandler::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientLoggingOut);
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TRANSMUTATION_MENU.get(), TransmutationScreen::new);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PORTABLE_TABLE);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_PORTABLE_TABLE.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && hasPortableTransmutationTable(minecraft.player.getInventory().items, minecraft.player.getInventory().offhand)) {
                PacketDistributor.sendToServer(RequestOpenPortableTablePacket.INSTANCE);
            }
        }
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEmcState.clear();
    }

    private static boolean hasPortableTransmutationTable(Iterable<ItemStack> mainInventory, Iterable<ItemStack> offhand) {
        Item portableTable = ModItems.PORTABLE_TRANSMUTATION_TABLE.get();
        for (ItemStack stack : mainInventory) {
            if (stack.is(portableTable)) {
                return true;
            }
        }

        for (ItemStack stack : offhand) {
            if (stack.is(portableTable)) {
                return true;
            }
        }

        return false;
    }
}
