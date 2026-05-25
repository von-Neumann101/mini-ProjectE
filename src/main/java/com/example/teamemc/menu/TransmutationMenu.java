package com.example.teamemc.menu;

import com.example.teamemc.registry.ModMenus;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TransmutationMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    private static final int INPUT_SLOT_COUNT = 1;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_START = INPUT_SLOT + INPUT_SLOT_COUNT;
    private static final int HOTBAR_START = PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = HOTBAR_START + HOTBAR_SLOT_COUNT;

    private static final int INPUT_SLOT_X = 80;
    private static final int INPUT_SLOT_Y = 20;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final SimpleContainer inputContainer = new SimpleContainer(INPUT_SLOT_COUNT);

    public TransmutationMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.TRANSMUTATION_MENU.get(), containerId);

        this.addSlot(new TransmutationInputSlot(this.inputContainer, 0, INPUT_SLOT_X, INPUT_SLOT_Y));
        this.addPlayerInventorySlots(playerInventory);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18,
                        PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(
                    playerInventory,
                    column,
                    PLAYER_INVENTORY_X + column * 18,
                    HOTBAR_Y
            ));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = this.slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack originalStack = sourceStack.copy();

        if (index == INPUT_SLOT) {
            if (!this.moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!TransmutationInputSlot.canPlaceStack(sourceStack)) {
                return ItemStack.EMPTY;
            }

            if (!this.moveItemStackTo(sourceStack, INPUT_SLOT, INPUT_SLOT + INPUT_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        if (sourceStack.getCount() == originalStack.getCount()) {
            return ItemStack.EMPTY;
        }

        sourceSlot.onTake(player, sourceStack);
        return originalStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (!player.level().isClientSide()) {
            this.clearContainer(player, this.inputContainer);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
