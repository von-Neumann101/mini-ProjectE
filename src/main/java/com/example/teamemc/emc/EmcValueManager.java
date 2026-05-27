package com.example.teamemc.emc;

import com.example.teamemc.TeamEmcMod;
import com.example.teamemc.registry.ModNetworking;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

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
    private static final boolean USE_RECIPE_DERIVATION = false;
    private static final int MAX_DERIVATION_ITERATIONS = 64;

    private static volatile Map<Item, Long> baseValues = Map.of();
    private static volatile Map<Item, Long> manualBaseValues = Map.of();
    private static volatile Map<Item, String> baseValueSources = Map.of();
    private static volatile EmcStats stats = new EmcStats(
            0,
            0,
            0,
            0,
            0,
            false,
            0,
            0,
            USE_RECIPE_DERIVATION,
            0,
            List.of(),
            false
    );

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
            server.getPlayerList().getPlayers().forEach(ModNetworking::sendEmcValueSnapshot);
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ensureDerived(event.getServer());
    }

    public static void ensureDerived(MinecraftServer server) {
        if (!USE_RECIPE_DERIVATION) {
            markDerivationCompleteWithoutChanges();
            return;
        }

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

    public static boolean isInEmcMap(Item item) {
        return baseValues.containsKey(item);
    }

    public static Optional<String> getBaseItemEmcSource(Item item) {
        return Optional.ofNullable(baseValueSources.get(item));
    }

    public static Map<ResourceLocation, Long> getServerEmcSnapshot() {
        Map<ResourceLocation, Long> snapshot = new HashMap<>();
        for (Map.Entry<Item, Long> entry : baseValues.entrySet()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(entry.getKey());
            if (itemId != null && entry.getValue() > 0L && !isBlockedModItem(itemId)) {
                snapshot.put(itemId, entry.getValue());
            }
        }

        return Map.copyOf(snapshot);
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
        LoadResult loadResult = loadBaseValuesFromResources(preparedValues);
        Map<Item, Long> manualValues = Map.copyOf(loadResult.values());
        manualBaseValues = manualValues;
        baseValues = manualValues;
        baseValueSources = Map.copyOf(loadResult.sources());
        stats = new EmcStats(
                manualValues.size(),
                0,
                manualValues.size(),
                0,
                0,
                !USE_RECIPE_DERIVATION,
                loadResult.fileCount(),
                loadResult.skippedEntryCount(),
                USE_RECIPE_DERIVATION,
                loadResult.importedItemCount(),
                loadResult.fileNames(),
                loadResult.projecteImportedPresent()
        );
        if (USE_RECIPE_DERIVATION) {
            LOGGER.info(
                    "Loaded {} manual EMC values from {} file(s), skipped {} invalid entrie(s). Recipe derivation will run after tags update.",
                    manualValues.size(),
                    loadResult.fileCount(),
                    loadResult.skippedEntryCount()
            );
        } else {
            LOGGER.info(
                    "Loaded {} manual EMC values from {} file(s), skipped {} invalid entrie(s). Recipe derivation is disabled.",
                    manualValues.size(),
                    loadResult.fileCount(),
                    loadResult.skippedEntryCount()
            );
        }
    }

    public static void deriveValuesFromRecipes(MinecraftServer server) {
        Map<Item, Long> manualValues = manualBaseValues;
        if (!USE_RECIPE_DERIVATION) {
            baseValues = Map.copyOf(manualValues);
            stats = new EmcStats(
                    manualValues.size(),
                    0,
                    manualValues.size(),
                    0,
                    0,
                    true,
                    stats.emcFileCount(),
                    stats.skippedEntryCount(),
                    USE_RECIPE_DERIVATION,
                    stats.importedItemCount(),
                    stats.emcFileNames(),
                    stats.projecteImportedPresent()
            );
            LOGGER.info("Recipe derivation is disabled. Using {} base EMC values only.", manualValues.size());
            return;
        }

        DerivationResult result = deriveCraftingValues(manualValues, server.getRecipeManager(), server.registryAccess());

        baseValues = Map.copyOf(result.values());
        stats = new EmcStats(
                manualValues.size(),
                Math.max(0, result.values().size() - manualValues.size()),
                result.values().size(),
                result.iterations(),
                result.changes(),
                true,
                stats.emcFileCount(),
                stats.skippedEntryCount(),
                USE_RECIPE_DERIVATION,
                stats.importedItemCount(),
                stats.emcFileNames(),
                stats.projecteImportedPresent()
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

    public static LoadResult loadBaseValuesFromResources(Map<ResourceLocation, JsonElement> preparedValues) {
        Map<Item, Long> loadedValues = new HashMap<>();
        Map<Item, String> loadedSources = new HashMap<>();
        Set<Item> importedItems = new HashSet<>();
        Set<String> fileNames = new HashSet<>();
        int fileCount = 0;
        int skippedEntryCount = 0;
        boolean projecteImportedPresent = false;

        for (Map.Entry<ResourceLocation, JsonElement> fileEntry : preparedValues.entrySet()) {
            fileCount++;
            String fileName = fileEntry.getKey().toString() + ".json";
            fileNames.add(fileName);
            if (fileEntry.getKey().getPath().equals("projecte_imported")) {
                projecteImportedPresent = true;
            }

            if (!fileEntry.getValue().isJsonObject()) {
                skippedEntryCount++;
                LOGGER.warn("Ignoring EMC file {} because it is not a JSON object map.", fileEntry.getKey());
                continue;
            }

            JsonObject valuesObject = GsonHelper.convertToJsonObject(fileEntry.getValue(), fileEntry.getKey().toString());
            for (Map.Entry<String, JsonElement> valueEntry : valuesObject.entrySet()) {
                ResourceLocation itemId = ResourceLocation.tryParse(valueEntry.getKey());
                if (itemId == null || isBlockedModItem(itemId) || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                    skippedEntryCount++;
                    continue;
                }

                Item item = BuiltInRegistries.ITEM.get(itemId);
                OptionalLong value = readPositiveLong(valueEntry.getValue());
                if (item == null || value.isEmpty()) {
                    skippedEntryCount++;
                    continue;
                }

                Long existingValue = loadedValues.get(item);
                if (existingValue == null || value.getAsLong() < existingValue) {
                    loadedValues.put(item, value.getAsLong());
                    loadedSources.put(item, fileName);
                }

                if (fileEntry.getKey().getPath().equals("projecte_imported")) {
                    importedItems.add(item);
                }
            }
        }

        if (fileCount == 0) {
            LOGGER.warn("No EMC JSON files found under data/*/{}.", EMC_DIRECTORY);
        }

        return new LoadResult(
                loadedValues,
                loadedSources,
                fileCount,
                skippedEntryCount,
                fileNames.stream().sorted().toList(),
                importedItems.size(),
                projecteImportedPresent
        );
    }

    private static OptionalLong readPositiveLong(JsonElement element) {
        if (!GsonHelper.isNumberValue(element)) {
            return OptionalLong.empty();
        }

        try {
            BigInteger value = element.getAsBigInteger();
            if (value.signum() <= 0 || value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                return OptionalLong.empty();
            }

            return OptionalLong.of(value.longValueExact());
        } catch (ArithmeticException | NumberFormatException | UnsupportedOperationException exception) {
            return OptionalLong.empty();
        }
    }

    private static void markDerivationCompleteWithoutChanges() {
        if (stats.derivationComplete()) {
            return;
        }

        Map<Item, Long> manualValues = manualBaseValues;
        baseValues = Map.copyOf(manualValues);
        stats = new EmcStats(
                manualValues.size(),
                0,
                manualValues.size(),
                0,
                0,
                true,
                stats.emcFileCount(),
                stats.skippedEntryCount(),
                USE_RECIPE_DERIVATION,
                stats.importedItemCount(),
                stats.emcFileNames(),
                stats.projecteImportedPresent()
        );
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
            boolean derivationComplete,
            int emcFileCount,
            int skippedEntryCount,
            boolean recipeDerivationEnabled,
            int importedItemCount,
            List<String> emcFileNames,
            boolean projecteImportedPresent
    ) {
        public EmcStats {
            emcFileNames = List.copyOf(emcFileNames);
        }
    }

    public record LoadResult(
            Map<Item, Long> values,
            Map<Item, String> sources,
            int fileCount,
            int skippedEntryCount,
            List<String> fileNames,
            int importedItemCount,
            boolean projecteImportedPresent
    ) {
        public LoadResult {
            values = Map.copyOf(values);
            sources = Map.copyOf(sources);
            fileNames = List.copyOf(fileNames);
        }
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
