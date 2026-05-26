package com.example.teamemc.network;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.menu.TransmutationMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestConvertCarriedPacket() implements CustomPacketPayload {
    public static final RequestConvertCarriedPacket INSTANCE = new RequestConvertCarriedPacket();
    public static final Type<RequestConvertCarriedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "request_convert_carried"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestConvertCarriedPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestConvertCarriedPacket decode(RegistryFriendlyByteBuf buffer) {
                    return INSTANCE;
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RequestConvertCarriedPacket packet) {
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestConvertCarriedPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof TransmutationMenu menu) {
            menu.convertStackFromCursor(serverPlayer);
        }
    }
}
