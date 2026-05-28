package com.example.teamemc.registry;

import com.example.teamemc.data.TeamEmcSavedData;
import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.network.GuiStatusPacket;
import com.example.teamemc.network.RequestConvertCarriedPacket;
import com.example.teamemc.network.RequestOpenPortableTablePacket;
import com.example.teamemc.network.RequestWithdrawPacket;
import com.example.teamemc.network.SyncEmcDataPacket;
import com.example.teamemc.network.SyncEmcValueSnapshotPacket;
import com.mojang.logging.LogUtils;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

public final class ModNetworking {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NETWORK_VERSION = "1";

    private ModNetworking() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(RequestConvertCarriedPacket.TYPE, RequestConvertCarriedPacket.STREAM_CODEC, RequestConvertCarriedPacket::handle);
        registrar.playToServer(RequestOpenPortableTablePacket.TYPE, RequestOpenPortableTablePacket.STREAM_CODEC, RequestOpenPortableTablePacket::handle);
        registrar.playToServer(RequestWithdrawPacket.TYPE, RequestWithdrawPacket.STREAM_CODEC, RequestWithdrawPacket::handle);
        registrar.playToClient(GuiStatusPacket.TYPE, GuiStatusPacket.STREAM_CODEC, GuiStatusPacket::handle);
        registrar.playToClient(SyncEmcDataPacket.TYPE, SyncEmcDataPacket.STREAM_CODEC, SyncEmcDataPacket::handle);
        registrar.playToClient(SyncEmcValueSnapshotPacket.TYPE, SyncEmcValueSnapshotPacket.STREAM_CODEC, SyncEmcValueSnapshotPacket::handle);
    }

    public static void sendEmcData(ServerPlayer player) {
        TeamEmcSavedData data = TeamEmcSavedData.get(player.getServer());
        EmcValueManager.ensureDerived(player.getServer());
        long balance = data.getBalance(player);
        List<ResourceLocation> savedLearnedItems = data.getLearnedItems(player).stream()
                .sorted()
                .toList();
        List<ResourceLocation> learnedItems = savedLearnedItems.stream()
                .filter(ModNetworking::isDisplayableLearnedItem)
                .toList();

        LOGGER.info(
                "Sending Team EMC data to {}: balance={}, savedLearnedItems={}, sentLearnedItems={}, preview={}",
                player.getGameProfile().getName(),
                balance,
                savedLearnedItems.size(),
                learnedItems.size(),
                previewItemIds(learnedItems, 5)
        );
        PacketDistributor.sendToPlayer(player, new SyncEmcDataPacket(balance, learnedItems));
    }

    public static void sendEmcValueSnapshot(ServerPlayer player) {
        EmcValueManager.ensureDerived(player.getServer());
        PacketDistributor.sendToPlayer(player, new SyncEmcValueSnapshotPacket(EmcValueManager.getServerEmcSnapshot()));
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            sendEmcValueSnapshot(serverPlayer);
        }
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

    private static String previewItemIds(List<ResourceLocation> itemIds, int limit) {
        String preview = itemIds.stream()
                .limit(limit)
                .map(ResourceLocation::toString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("<empty>");
        return itemIds.size() > limit ? preview + ", ..." : preview;
    }
}
