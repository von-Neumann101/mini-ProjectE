package com.example.teamemc.network;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.menu.TransmutationMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestWithdrawPacket(ResourceLocation itemId, int count) implements CustomPacketPayload {
    public static final Type<RequestWithdrawPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "request_withdraw"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWithdrawPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestWithdrawPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new RequestWithdrawPacket(buffer.readResourceLocation(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RequestWithdrawPacket packet) {
                    buffer.writeResourceLocation(packet.itemId());
                    buffer.writeVarInt(packet.count());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestWithdrawPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.containerMenu instanceof TransmutationMenu menu) {
                menu.withdrawItem(serverPlayer, packet.itemId(), packet.count());
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.teamemc.withdraw.invalid"), false);
            }
        }
    }
}
