package com.example.teamemc.network;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.client.ClientEmcState;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncEmcDataPacket(long balance, List<ResourceLocation> learnedItems) implements CustomPacketPayload {
    public static final Type<SyncEmcDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "sync_emc_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEmcDataPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SyncEmcDataPacket decode(RegistryFriendlyByteBuf buffer) {
                    long balance = buffer.readVarLong();
                    int learnedItemCount = buffer.readVarInt();
                    List<ResourceLocation> learnedItems = new ArrayList<>(learnedItemCount);
                    for (int index = 0; index < learnedItemCount; index++) {
                        learnedItems.add(buffer.readResourceLocation());
                    }

                    return new SyncEmcDataPacket(balance, learnedItems);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SyncEmcDataPacket packet) {
                    buffer.writeVarLong(packet.balance());
                    buffer.writeVarInt(packet.learnedItems().size());
                    for (ResourceLocation learnedItem : packet.learnedItems()) {
                        buffer.writeResourceLocation(learnedItem);
                    }
                }
            };

    public SyncEmcDataPacket {
        learnedItems = List.copyOf(learnedItems);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncEmcDataPacket packet, IPayloadContext context) {
        ClientEmcState.updateFromPacket(packet.balance(), packet.learnedItems());
    }
}
