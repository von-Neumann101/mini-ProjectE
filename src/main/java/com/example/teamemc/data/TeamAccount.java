package com.example.teamemc.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public final class TeamAccount {
    private long emcBalance;
    private final Set<ResourceLocation> learnedItems = new HashSet<>();

    public TeamAccount() {
        this(0L);
    }

    public TeamAccount(long emcBalance) {
        this.emcBalance = Math.max(0L, emcBalance);
    }

    public long getEmcBalance() {
        return emcBalance;
    }

    void setEmcBalance(long emcBalance) {
        this.emcBalance = Math.max(0L, emcBalance);
    }

    boolean learn(ResourceLocation itemId) {
        return learnedItems.add(itemId);
    }

    boolean isLearned(ResourceLocation itemId) {
        return learnedItems.contains(itemId);
    }

    Set<ResourceLocation> getLearnedItems() {
        return Collections.unmodifiableSet(learnedItems);
    }
}
