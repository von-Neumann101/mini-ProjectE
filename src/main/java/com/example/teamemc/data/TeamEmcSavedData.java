package com.example.teamemc.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;

public final class TeamEmcSavedData extends SavedData {
    private static final String FILE_ID = "teamemc_accounts";
    private static final String TAG_ACCOUNTS = "accounts";
    private static final String TAG_KEY = "key";
    private static final String TAG_EMC_BALANCE = "emcBalance";
    private static final String TAG_LEARNED_ITEMS = "learnedItems";
    private static final SavedData.Factory<TeamEmcSavedData> FACTORY =
            new SavedData.Factory<>(TeamEmcSavedData::new, TeamEmcSavedData::load);

    private final Map<String, TeamAccount> accounts = new HashMap<>();

    public static TeamEmcSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    private static TeamEmcSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TeamEmcSavedData data = new TeamEmcSavedData();
        boolean corrected = false;

        ListTag accountTags = tag.getList(TAG_ACCOUNTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < accountTags.size(); i++) {
            CompoundTag accountTag = accountTags.getCompound(i);
            String key = accountTag.getString(TAG_KEY);
            if (key.isBlank()) {
                corrected = true;
                continue;
            }

            long balance = accountTag.getLong(TAG_EMC_BALANCE);
            if (balance < 0L) {
                balance = 0L;
                corrected = true;
            }

            TeamAccount account = new TeamAccount(balance);
            ListTag learnedTags = accountTag.getList(TAG_LEARNED_ITEMS, Tag.TAG_STRING);
            for (int learnedIndex = 0; learnedIndex < learnedTags.size(); learnedIndex++) {
                ResourceLocation itemId = ResourceLocation.tryParse(learnedTags.getString(learnedIndex));
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    account.learn(itemId);
                } else {
                    corrected = true;
                }
            }

            data.accounts.put(key, account);
        }

        if (corrected) {
            data.setDirty();
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag accountTags = new ListTag();
        accounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag accountTag = new CompoundTag();
                    accountTag.putString(TAG_KEY, entry.getKey());
                    accountTag.putLong(TAG_EMC_BALANCE, entry.getValue().getEmcBalance());

                    ListTag learnedTags = new ListTag();
                    entry.getValue().getLearnedItems().stream()
                            .sorted()
                            .map(ResourceLocation::toString)
                            .map(StringTag::valueOf)
                            .forEach(learnedTags::add);
                    accountTag.put(TAG_LEARNED_ITEMS, learnedTags);

                    accountTags.add(accountTag);
                });

        tag.put(TAG_ACCOUNTS, accountTags);
        return tag;
    }

    public TeamAccount getOrCreateAccount(ServerPlayer player) {
        return accounts.computeIfAbsent(getAccountKey(player), key -> new TeamAccount());
    }

    public String getAccountKey(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team != null) {
            return "team:" + team.getName();
        }

        return "player:" + player.getUUID();
    }

    public long getBalance(ServerPlayer player) {
        return getOrCreateAccount(player).getEmcBalance();
    }

    public void setBalance(ServerPlayer player, long amount) {
        TeamAccount account = getOrCreateAccount(player);
        long sanitizedAmount = Math.max(0L, amount);
        account.setEmcBalance(sanitizedAmount);
        setDirty();
    }

    public boolean addEmc(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return false;
        }

        TeamAccount account = getOrCreateAccount(player);
        long newBalance;
        try {
            newBalance = Math.addExact(account.getEmcBalance(), amount);
        } catch (ArithmeticException exception) {
            return false;
        }

        account.setEmcBalance(newBalance);
        setDirty();
        return true;
    }

    public boolean trySpendEmc(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return false;
        }

        TeamAccount account = getOrCreateAccount(player);
        long balance = account.getEmcBalance();
        if (balance < amount) {
            return false;
        }

        account.setEmcBalance(balance - amount);
        setDirty();
        return true;
    }

    public boolean isLearned(ServerPlayer player, Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return itemId != null && getOrCreateAccount(player).isLearned(itemId);
    }

    public void learn(ServerPlayer player, Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId != null && getOrCreateAccount(player).learn(itemId)) {
            setDirty();
        }
    }

    public Set<ResourceLocation> getLearnedItems(ServerPlayer player) {
        TeamAccount account = getOrCreateAccount(player);
        return Collections.unmodifiableSet(account.getLearnedItems());
    }
}
