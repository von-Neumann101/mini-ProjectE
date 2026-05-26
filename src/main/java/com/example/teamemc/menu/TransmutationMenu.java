package com.example.teamemc.menu;

import com.example.teamemc.data.TeamEmcSavedData;
import com.example.teamemc.emc.EmcMath;
import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.registry.ModMenus;
import com.example.teamemc.registry.ModNetworking;

import java.util.List;
import java.util.OptionalLong;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TransmutationMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_START = 0;
    private static final int HOTBAR_START = PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = HOTBAR_START + HOTBAR_SLOT_COUNT;

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    public TransmutationMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.TRANSMUTATION_MENU.get(), containerId);

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

        if (!(player instanceof ServerPlayer serverPlayer)
                || index < PLAYER_INVENTORY_START
                || index >= PLAYER_INVENTORY_END) {
            return ItemStack.EMPTY;
        }

        boolean converted = this.convertStack(serverPlayer, sourceStack, () -> {
            sourceSlot.setByPlayer(ItemStack.EMPTY);
            sourceSlot.setChanged();
            sourceSlot.onTake(player, sourceStack);
        });
        return converted ? originalStack : ItemStack.EMPTY;
    }

    public boolean convertStackFromCursor(ServerPlayer player) {
        return this.convertStack(player, this.getCarried(), () -> this.setCarried(ItemStack.EMPTY));
    }

    private boolean convertStack(ServerPlayer player, ItemStack stack, Runnable clearSource) {
        if (stack.isEmpty()) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.convert.no_item", true);
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || EmcValueManager.isBlockedModItem(itemId) || !EmcValueManager.hasEmc(stack)) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.convert.no_emc", true);
            return false;
        }

        OptionalLong stackEmc = EmcValueManager.getStackEmc(stack);
        if (stackEmc.isEmpty() || stackEmc.getAsLong() <= 0L) {
            String translationKey = EmcValueManager.isDamageable(stack)
                    ? "message.teamemc.convert.invalid_durability"
                    : "message.teamemc.convert.failed";
            ModNetworking.sendGuiStatus(player, translationKey, true);
            return false;
        }

        long emcAmount = stackEmc.getAsLong();
        TeamEmcSavedData data = TeamEmcSavedData.get(player.getServer());
        if (!data.addEmc(player, emcAmount)) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.convert.overflow", true);
            return false;
        }

        ItemStack convertedStack = stack.copy();
        data.learn(player, stack.getItem());
        clearSource.run();
        this.broadcastChanges();
        ModNetworking.sendEmcData(player);
        ModNetworking.sendGuiStatus(
                player,
                "message.teamemc.convert.success",
                false,
                formatStackName(convertedStack),
                Long.toString(emcAmount)
        );
        return true;
    }

    public boolean withdrawItem(ServerPlayer player, ResourceLocation itemId, int requestedCount) {
        if (requestedCount <= 0 || itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId) || EmcValueManager.isBlockedModItem(itemId)) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.invalid", true);
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.invalid", true);
            return false;
        }

        int maxStackSize = item.getDefaultMaxStackSize();
        if (maxStackSize <= 0) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.invalid", true);
            return false;
        }

        int count = Math.min(requestedCount, maxStackSize);
        if (count <= 0) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.invalid", true);
            return false;
        }

        TeamEmcSavedData data = TeamEmcSavedData.get(player.getServer());
        if (!data.isLearned(player, item)) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.not_learned", true);
            return false;
        }

        EmcValueManager.ensureDerived(player.getServer());
        OptionalLong singleItemEmc = EmcValueManager.getSingleItemEmc(new ItemStack(item, 1));
        if (singleItemEmc.isEmpty() || singleItemEmc.getAsLong() <= 0L) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.no_emc", true);
            return false;
        }

        OptionalLong totalCost = EmcMath.multiplyExact(singleItemEmc.getAsLong(), count);
        if (totalCost.isEmpty() || totalCost.getAsLong() <= 0L) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.failed", true);
            return false;
        }

        if (data.getBalance(player) < totalCost.getAsLong()) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.not_enough_emc", true);
            return false;
        }

        ItemStack withdrawnStack = new ItemStack(item, count);
        if (!canFitInInventory(player.getInventory(), withdrawnStack)) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.no_space", true);
            return false;
        }

        if (!data.trySpendEmc(player, totalCost.getAsLong())) {
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.not_enough_emc", true);
            return false;
        }

        List<ItemStack> inventorySnapshot = copyInventoryItems(player.getInventory());
        if (!insertIntoInventory(player.getInventory(), withdrawnStack.copy())) {
            restoreInventoryItems(player.getInventory(), inventorySnapshot);
            data.addEmc(player, totalCost.getAsLong());
            ModNetworking.sendGuiStatus(player, "message.teamemc.withdraw.failed", true);
            ModNetworking.sendEmcData(player);
            return false;
        }

        player.getInventory().setChanged();
        this.broadcastChanges();
        ModNetworking.sendEmcData(player);
        ModNetworking.sendGuiStatus(
                player,
                "message.teamemc.withdraw.success",
                false,
                formatStackName(withdrawnStack),
                Long.toString(totalCost.getAsLong())
        );
        return true;
    }

    private static String formatStackName(ItemStack stack) {
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private static List<ItemStack> copyInventoryItems(Inventory inventory) {
        return inventory.items.stream()
                .map(ItemStack::copy)
                .toList();
    }

    private static void restoreInventoryItems(Inventory inventory, List<ItemStack> snapshot) {
        for (int slot = 0; slot < inventory.items.size() && slot < snapshot.size(); slot++) {
            inventory.items.set(slot, snapshot.get(slot).copy());
        }
        inventory.setChanged();
    }

    private static boolean canFitInInventory(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();

        for (ItemStack inventoryStack : inventory.items) {
            if (canMergeInto(inventory, inventoryStack, stack)) {
                remaining -= inventory.getMaxStackSize(inventoryStack) - inventoryStack.getCount();
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        for (ItemStack inventoryStack : inventory.items) {
            if (inventoryStack.isEmpty()) {
                remaining -= stack.getMaxStackSize();
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean insertIntoInventory(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();

        for (ItemStack inventoryStack : inventory.items) {
            if (remaining <= 0) {
                return true;
            }

            if (canMergeInto(inventory, inventoryStack, stack)) {
                int moved = Math.min(remaining, inventory.getMaxStackSize(inventoryStack) - inventoryStack.getCount());
                inventoryStack.grow(moved);
                inventoryStack.setPopTime(5);
                remaining -= moved;
            }
        }

        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (remaining <= 0) {
                return true;
            }

            if (inventory.items.get(slot).isEmpty()) {
                int moved = Math.min(remaining, stack.getMaxStackSize());
                ItemStack insertedStack = stack.copyWithCount(moved);
                insertedStack.setPopTime(5);
                inventory.items.set(slot, insertedStack);
                remaining -= moved;
            }
        }

        return remaining <= 0;
    }

    private static boolean canMergeInto(Inventory inventory, ItemStack inventoryStack, ItemStack stack) {
        return !inventoryStack.isEmpty()
                && ItemStack.isSameItemSameComponents(inventoryStack, stack)
                && inventoryStack.isStackable()
                && inventoryStack.getCount() < inventory.getMaxStackSize(inventoryStack);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
