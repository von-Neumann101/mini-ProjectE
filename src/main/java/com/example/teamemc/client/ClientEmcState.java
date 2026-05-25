package com.example.teamemc.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

public final class ClientEmcState {
    private static long balance;
    private static List<ResourceLocation> learnedItems = List.of();

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

    public static void updateFromPacket(long newBalance, List<ResourceLocation> newLearnedItems) {
        balance = Math.max(0L, newBalance);
        learnedItems = List.copyOf(newLearnedItems);
    }

    public static void clear() {
        balance = 0L;
        learnedItems = List.of();
    }
}
