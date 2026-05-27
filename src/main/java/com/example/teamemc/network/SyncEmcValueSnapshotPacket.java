package com.example.teamemc.network;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.client.ClientEmcState;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncEmcValueSnapshotPacket(Map<ResourceLocation, Long> values) implements CustomPacketPayload {
    public static final Type<SyncEmcValueSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "sync_emc_value_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEmcValueSnapshotPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SyncEmcValueSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
                    int count = buffer.readVarInt();
                    Map<ResourceLocation, Long> values = new HashMap<>(count);
                    for (int index = 0; index < count; index++) {
                        ResourceLocation itemId = buffer.readResourceLocation();
                        long emc = buffer.readVarLong();
                        if (emc > 0L) {
                            values.put(itemId, emc);
                        }
                    }

                    return new SyncEmcValueSnapshotPacket(values);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SyncEmcValueSnapshotPacket packet) {
                    buffer.writeVarInt(packet.values().size());
                    for (Map.Entry<ResourceLocation, Long> entry : packet.values().entrySet()) {
                        buffer.writeResourceLocation(entry.getKey());
                        buffer.writeVarLong(entry.getValue());
                    }
                }
            };

    public SyncEmcValueSnapshotPacket {
        values = Map.copyOf(values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncEmcValueSnapshotPacket packet, IPayloadContext context) {
        ClientEmcState.updateServerEmcSnapshot(packet.values());
    }
}
