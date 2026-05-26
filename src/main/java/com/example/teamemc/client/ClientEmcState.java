package com.example.teamemc.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ClientEmcState {
    private static long balance;
    private static List<ResourceLocation> learnedItems = List.of();
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
        clearStatus();
    }
}
