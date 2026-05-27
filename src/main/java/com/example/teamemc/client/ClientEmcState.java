package com.example.teamemc.client;

import com.example.teamemc.emc.EmcMath;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ClientEmcState {
    private static long balance;
    private static List<ResourceLocation> learnedItems = List.of();
    private static Map<ResourceLocation, Long> serverEmcValues = Map.of();
    private static boolean hasServerEmcSnapshot;
    private static Component status = Component.empty();
    private static boolean hasStatus;
    private static boolean statusError;

    private ClientEmcState() {
    }

    public static long getBalance() {
        return balance;
    }

    public static List<ResourceLocation> getLearnedItems() {
        return learnedItems;
    }

    public static int getLearnedCount() {
        return learnedItems.size();
    }

    public static boolean hasServerEmcSnapshot() {
        return hasServerEmcSnapshot;
    }

    public static OptionalLong getServerStackEmc(ItemStack stack) {
        if (stack.isEmpty()) {
            return OptionalLong.empty();
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return OptionalLong.empty();
        }

        Long baseEmc = serverEmcValues.get(itemId);
        if (baseEmc == null || baseEmc <= 0L) {
            return OptionalLong.empty();
        }

        long singleEmc = baseEmc;
        if (stack.isDamageableItem()) {
            long maxDurability = stack.getMaxDamage();
            long remainingDurability = (long) stack.getMaxDamage() - stack.getDamageValue();
            if (maxDurability <= 0L || remainingDurability <= 0L) {
                return OptionalLong.empty();
            }

            OptionalLong multiplied = EmcMath.multiplyExact(baseEmc, remainingDurability);
            if (multiplied.isEmpty()) {
                return OptionalLong.empty();
            }

            singleEmc = multiplied.getAsLong() / maxDurability;
            if (singleEmc <= 0L) {
                return OptionalLong.empty();
            }
        }

        return EmcMath.multiplyExact(singleEmc, stack.getCount());
    }

    public static Component getStatus() {
        return status;
    }

    public static boolean hasStatus() {
        return hasStatus;
    }

    public static boolean isStatusError() {
        return statusError;
    }

    public static void updateFromPacket(long newBalance, List<ResourceLocation> newLearnedItems) {
        balance = Math.max(0L, newBalance);
        learnedItems = List.copyOf(newLearnedItems);
    }

    public static void updateServerEmcSnapshot(Map<ResourceLocation, Long> values) {
        serverEmcValues = Map.copyOf(values);
        hasServerEmcSnapshot = true;
    }

    public static void clearServerEmcSnapshot() {
        serverEmcValues = Map.of();
        hasServerEmcSnapshot = false;
    }

    public static void setStatus(Component message, boolean error) {
        status = message == null ? Component.empty() : message;
        hasStatus = true;
        statusError = error;
    }

    public static void clearStatus() {
        status = Component.empty();
        hasStatus = false;
        statusError = false;
    }

    public static void clear() {
        balance = 0L;
        learnedItems = List.of();
        clearServerEmcSnapshot();
        clearStatus();
    }
}
