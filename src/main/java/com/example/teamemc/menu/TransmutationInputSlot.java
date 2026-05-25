package com.example.teamemc.menu;

import com.example.teamemc.emc.EmcValueManager;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TransmutationInputSlot extends Slot {
    public TransmutationInputSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return canPlaceStack(stack);
    }

    public static boolean canPlaceStack(ItemStack stack) {
        return EmcValueManager.hasEmc(stack);
    }
}
