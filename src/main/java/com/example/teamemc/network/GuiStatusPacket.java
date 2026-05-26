package com.example.teamemc.network;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.client.ClientEmcState;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GuiStatusPacket(String translationKey, List<String> args, boolean error) implements CustomPacketPayload {
    public static final Type<GuiStatusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "gui_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuiStatusPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public GuiStatusPacket decode(RegistryFriendlyByteBuf buffer) {
                    String translationKey = buffer.readUtf();
                    int argCount = buffer.readVarInt();
                    List<String> args = new ArrayList<>(Math.max(0, argCount));
                    for (int index = 0; index < argCount; index++) {
                        args.add(buffer.readUtf());
                    }

                    return new GuiStatusPacket(translationKey, args, buffer.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, GuiStatusPacket packet) {
                    buffer.writeUtf(packet.translationKey());
                    buffer.writeVarInt(packet.args().size());
                    for (String arg : packet.args()) {
                        buffer.writeUtf(arg);
                    }
                    buffer.writeBoolean(packet.error());
                }
            };

    public GuiStatusPacket {
        args = args.stream()
                .map(arg -> arg == null ? "" : arg)
                .toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GuiStatusPacket packet, IPayloadContext context) {
        ClientEmcState.setStatus(
                Component.translatable(packet.translationKey(), packet.args().toArray()),
                packet.error()
        );
    }

}
