package com.example.teamemc.emc;

import com.example.teamemc.TeamEmcMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

public final class EmcValueManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String EMC_DIRECTORY = "emc";
    private static final ResourceLocation BASE_VALUES_ID = ResourceLocation.fromNamespaceAndPath(TeamEmcMod.MOD_ID, "base_values");
    private static final int MAX_DERIVATION_ITERATIONS = 64;

    private static volatile Map<Item, Long> baseValues = Map.of();
    private static volatile Map<Item, Long> manualBaseValues = Map.of();
    private static volatile EmcStats stats = new EmcStats(0, 0, 0, 0, 0, false);

    private EmcValueManager() {
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ReloadListener());
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData() || event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ensureDerived(server);
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ensureDerived(event.getServer());
    }

    public static void ensureDerived(MinecraftServer server) {
        if (!stats.derivationComplete()) {
            deriveValuesFromRecipes(server);
        }
    }

    public static long getBaseItemEmc(Item item) {
        return baseValues.getOrDefault(item, 0L);
    }

    public static EmcStats getStats() {
        return stats;
    }

    public static OptionalLong getSingleItemEmc(ItemStack stack) {
        if (stack.isEmpty()) {
            return OptionalLong.empty();
        }

        long baseEmc = getBaseItemEmc(stack.getItem());
        if (baseEmc <= 0L) {
            return OptionalLong.empty();
        }

        if (!isDamageable(stack)) {
            return OptionalLong.of(baseEmc);
        }

        long maxDurability = getMaxDurability(stack);
        long remainingDurability = getRemainingDurability(stack);
        if (maxDurability <= 0L || remainingDurability <= 0L) {
            return OptionalLong.empty();
        }

        OptionalLong multiplied = EmcMath.multiplyExact(baseEmc, remainingDurability);
        if (multiplied.isEmpty()) {
            return OptionalLong.empty();
        }

        long actualEmc = multiplied.getAsLong() / maxDurability;
        return actualEmc > 0L ? OptionalLong.of(actualEmc) : OptionalLong.empty();
    }

    public static OptionalLong getStackEmc(ItemStack stack) {
        OptionalLong singleEmc = getSingleItemEmc(stack);
        if (singleEmc.isEmpty()) {
            return OptionalLong.empty();
        }

        return EmcMath.multiplyExact(singleEmc.getAsLong(), stack.getCount());
    }

    public static boolean hasEmc(Item item) {
        return getBaseItemEmc(item) > 0L;
    }

    public static boolean hasEmc(ItemStack stack) {
        return !stack.isEmpty() && hasEmc(stack.getItem());
    }

    public static boolean isDamageable(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem();
    }

    public static long getRemainingDurability(ItemStack stack) {
        if (!isDamageable(stack)) {
            return 0L;
        }

        return (long) stack.getMaxDamage() - stack.getDamageValue();
    }

    public static long getMaxDurability(ItemStack stack) {
        if (!isDamageable(stack)) {
            return 0L;
        }

        return stack.getMaxDamage();
    }

    public static void reload(Map<ResourceLocation, JsonElement> preparedValues) {
        Map<Item, Long> manualValues = Map.copyOf(loadBaseValuesFromResources(preparedValues));
        manualBaseValues = manualValues;
        baseValues = manualValues;
        stats = new EmcStats(manualValues.size(), 0, manualValues.size(), 0, 0, false);
        LOGGER.info("Loaded {} manual EMC values. Recipe derivation will run after tags update.", manualValues.size());
    }

    public static void deriveValuesFromRecipes(MinecraftServer server) {
        Map<Item, Long> manualValues = manualBaseValues;
        DerivationResult result = deriveCraftingValues(manualValues, server.getRecipeManager(), server.registryAccess());

        baseValues = Map.copyOf(result.values());
        stats = new EmcStats(
                manualValues.size(),
                Math.max(0, result.values().size() - manualValues.size()),
                result.values().size(),
                result.iterations(),
                result.changes(),
                true
        );

        LOGGER.info(
                "Loaded {} manual EMC values, derived {} recipe EMC values, final total {} after {} iteration(s) and {} add/update(s).",
                stats.manualBaseCount(),
                stats.derivedCount(),
                stats.totalCount(),
                stats.lastDerivationIterations(),
                stats.lastDerivationChanges()
        );
    }

    public static Map<Item, Long> loadBaseValuesFromResources(Map<ResourceLocation, JsonElement> preparedValues) {
        Map<Item, Long> loadedValues = new HashMap<>();
        JsonElement baseValuesElement = preparedValues.get(BASE_VALUES_ID);
        if (baseValuesElement == null) {
            LOGGER.warn("No EMC base value file found for {}.", BASE_VALUES_ID);
            return loadedValues;
        }

        JsonObject baseValuesObject = GsonHelper.convertToJsonObject(baseValuesElement, BASE_VALUES_ID.toString());
        for (Map.Entry<String, JsonElement> entry : baseValuesObject.entrySet()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
            if (itemId == null) {
                LOGGER.warn("Ignoring invalid EMC item id '{}'.", entry.getKey());
                continue;
            }

            if (isBlockedModItem(itemId)) {
                LOGGER.warn("Ignoring EMC value for blocked Team EMC item {}.", itemId);
                continue;
            }

            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                LOGGER.warn("Ignoring EMC value for unknown item {}.", itemId);
                continue;
            }

            if (!GsonHelper.isNumberValue(entry.getValue())) {
                LOGGER.warn("Ignoring non-numeric EMC value for {}.", itemId);
                continue;
            }

            long value = entry.getValue().getAsLong();
            if (value <= 0L) {
                LOGGER.warn("Ignoring non-positive EMC value {} for {}.", value, itemId);
                continue;
            }

            loadedValues.put(item, value);
        }

        return loadedValues;
    }

    private static DerivationResult deriveCraftingValues(
            Map<Item, Long> manualValues,
            RecipeManager recipeManager,
            HolderLookup.Provider registries
    ) {
        Map<Item, Long> values = new HashMap<>(manualValues);
        List<RecipeHolder<CraftingRecipe>> recipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        int changes = 0;
        int iterations = 0;

        for (int iteration = 0; iteration < MAX_DERIVATION_ITERATIONS; iteration++) {
            int changesBeforeRound = changes;

            for (RecipeHolder<CraftingRecipe> recipeHolder : recipes) {
                OptionalLong candidate = getCraftingRecipeCandidate(recipeHolder.value(), values, registries);
                if (candidate.isEmpty()) {
                    continue;
                }

                Item outputItem = recipeHolder.value().getResultItem(registries).getItem();
                Long existingValue = values.get(outputItem);
                if (existingValue == null || candidate.getAsLong() < existingValue) {
                    // We intentionally allow recipe derivation to lower a manual JSON value.
                    // This keeps all known crafting paths on the same "minimum positive cost wins" rule.
                    values.put(outputItem, candidate.getAsLong());
                    changes++;
                }
            }

            iterations++;
            if (changes == changesBeforeRound) {
                break;
            }
        }

        return new DerivationResult(values, iterations, changes);
    }

    private static OptionalLong getCraftingRecipeCandidate(
            CraftingRecipe recipe,
            Map<Item, Long> values,
            HolderLookup.Provider registries
    ) {
        if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) {
            return OptionalLong.empty();
        }

        ItemStack output = recipe.getResultItem(registries);
        if (output.isEmpty() || output.getCount() <= 0 || isBlockedModItem(output.getItem()) || !output.isComponentsPatchEmpty()) {
            return OptionalLong.empty();
        }

        long inputSum = 0L;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }

            OptionalLong ingredientEmc = getIngredientEmc(ingredient, values);
            if (ingredientEmc.isEmpty()) {
                return OptionalLong.empty();
            }

            OptionalLong newInputSum = EmcMath.addExact(inputSum, ingredientEmc.getAsLong());
            if (newInputSum.isEmpty()) {
                return OptionalLong.empty();
            }

            inputSum = newInputSum.getAsLong();
        }

        long candidate = inputSum / output.getCount();
        return candidate > 0L ? OptionalLong.of(candidate) : OptionalLong.empty();
    }

    private static OptionalLong getIngredientEmc(Ingredient ingredient, Map<Item, Long> values) {
        long bestValue = Long.MAX_VALUE;
        boolean found = false;

        for (ItemStack candidateStack : ingredient.getItems()) {
            if (candidateStack.isEmpty() || isBlockedModItem(candidateStack.getItem())) {
                continue;
            }

            Long candidateValue = values.get(candidateStack.getItem());
            if (candidateValue != null && candidateValue > 0L && candidateValue < bestValue) {
                bestValue = candidateValue;
                found = true;
            }
        }

        return found ? OptionalLong.of(bestValue) : OptionalLong.empty();
    }

    private static boolean isBlockedModItem(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return itemId != null && isBlockedModItem(itemId);
    }

    public static boolean isBlockedModItem(ResourceLocation itemId) {
        return itemId.getNamespace().equals(TeamEmcMod.MOD_ID)
                && (itemId.getPath().equals("transmutation_table")
                || itemId.getPath().equals("portable_transmutation_table"));
    }

    public record EmcStats(
            int manualBaseCount,
            int derivedCount,
            int totalCount,
            int lastDerivationIterations,
            int lastDerivationChanges,
            boolean derivationComplete
    ) {
    }

    private record DerivationResult(Map<Item, Long> values, int iterations, int changes) {
    }

    private static final class ReloadListener extends SimpleJsonResourceReloadListener {
        private ReloadListener() {
            super(GSON, EMC_DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> preparedValues, ResourceManager resourceManager, ProfilerFiller profiler) {
            EmcValueManager.reload(preparedValues);
        }
    }
}
