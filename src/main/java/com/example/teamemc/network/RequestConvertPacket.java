package com.example.teamemc.network;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.menu.TransmutationMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestConvertPacket() implements CustomPacketPayload {
    public static final RequestConvertPacket INSTANCE = new RequestConvertPacket();
    public static final Type<RequestConvertPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "request_convert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestConvertPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestConvertPacket decode(RegistryFriendlyByteBuf buffer) {
                    return INSTANCE;
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RequestConvertPacket packet) {
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestConvertPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof TransmutationMenu menu) {
            menu.convertInput(serverPlayer);
        }
    }
}
