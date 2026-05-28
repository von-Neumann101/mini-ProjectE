package com.example.teamemc.network;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.item.PortableTransmutationTableItem;
import com.example.teamemc.menu.TransmutationMenu;
import com.example.teamemc.registry.ModItems;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestOpenPortableTablePacket() implements CustomPacketPayload {
    public static final RequestOpenPortableTablePacket INSTANCE = new RequestOpenPortableTablePacket();
    public static final Type<RequestOpenPortableTablePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "request_open_portable_table"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestOpenPortableTablePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestOpenPortableTablePacket decode(RegistryFriendlyByteBuf buffer) {
                    return INSTANCE;
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RequestOpenPortableTablePacket packet) {
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestOpenPortableTablePacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.containerMenu instanceof TransmutationMenu) {
                return;
            }

            if (hasPortableTransmutationTable(serverPlayer)) {
                PortableTransmutationTableItem.openTransmutationMenu(serverPlayer);
            }
        }
    }

    private static boolean hasPortableTransmutationTable(ServerPlayer player) {
        Item portableTable = ModItems.PORTABLE_TRANSMUTATION_TABLE.get();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(portableTable)) {
                return true;
            }
        }

        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(portableTable)) {
                return true;
            }
        }

        return false;
    }
}
