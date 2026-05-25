package com.example.teamemc.registry;

import com.example.teamemc.data.TeamEmcSavedData;
import com.example.teamemc.network.RequestConvertPacket;
import com.example.teamemc.network.SyncEmcDataPacket;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
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
        registrar.playToClient(SyncEmcDataPacket.TYPE, SyncEmcDataPacket.STREAM_CODEC, SyncEmcDataPacket::handle);
    }

    public static void sendEmcData(ServerPlayer player) {
        TeamEmcSavedData data = TeamEmcSavedData.get(player.getServer());
        PacketDistributor.sendToPlayer(player, new SyncEmcDataPacket(
                data.getBalance(player),
                List.copyOf(data.getLearnedItems(player))
        ));
    }
}
