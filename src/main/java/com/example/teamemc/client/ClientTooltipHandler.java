package com.example.teamemc.client;

import com.example.teamemc.emc.EmcValueManager;

import java.util.OptionalLong;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class ClientTooltipHandler {
    private ClientTooltipHandler() {
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || EmcValueManager.isBlockedModItem(itemId)) {
            return;
        }

        OptionalLong stackEmc = EmcValueManager.getStackEmc(stack);
        if (stackEmc.isPresent() && stackEmc.getAsLong() > 0L) {
            event.getToolTip().add(Component.translatable("tooltip.teamemc.emc", stackEmc.getAsLong()));
        }
    }
}
