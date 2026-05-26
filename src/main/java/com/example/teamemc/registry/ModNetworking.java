package com.example.teamemc.registry;

import com.example.teamemc.data.TeamEmcSavedData;
import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.network.GuiStatusPacket;
import com.example.teamemc.network.RequestConvertPacket;
import com.example.teamemc.network.RequestWithdrawPacket;
import com.example.teamemc.network.SyncEmcDataPacket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String NETWORK_VERSION = "1";

    private ModNetworking() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(RequestConvertPacket.TYPE, RequestConvertPacket.STREAM_CODEC, RequestConvertPacket::handle);
        registrar.playToServer(RequestWithdrawPacket.TYPE, RequestWithdrawPacket.STREAM_CODEC, RequestWithdrawPacket::handle);
        registrar.playToClient(GuiStatusPacket.TYPE, GuiStatusPacket.STREAM_CODEC, GuiStatusPacket::handle);
        registrar.playToClient(SyncEmcDataPacket.TYPE, SyncEmcDataPacket.STREAM_CODEC, SyncEmcDataPacket::handle);
    }

    public static void sendEmcData(ServerPlayer player) {
        TeamEmcSavedData data = TeamEmcSavedData.get(player.getServer());
        EmcValueManager.ensureDerived(player.getServer());
        PacketDistributor.sendToPlayer(player, new SyncEmcDataPacket(
                data.getBalance(player),
                data.getLearnedItems(player).stream()
                        .filter(ModNetworking::isDisplayableLearnedItem)
                        .sorted()
                        .toList()
        ));
    }

    public static void sendGuiStatus(ServerPlayer player, String translationKey, boolean error, String... args) {
        PacketDistributor.sendToPlayer(player, new GuiStatusPacket(translationKey, java.util.List.of(args), error));
    }

    private static boolean isDisplayableLearnedItem(ResourceLocation itemId) {
        if (!BuiltInRegistries.ITEM.containsKey(itemId) || EmcValueManager.isBlockedModItem(itemId)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item != Items.AIR && EmcValueManager.hasEmc(item);
    }
}
