package com.example.teamemc.command;

import com.example.teamemc.data.TeamEmcSavedData;
import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.menu.TransmutationMenu;
import com.example.teamemc.registry.ModNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.List;
import java.util.OptionalLong;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class TeamEmcCommand {
    private static final int OP_PERMISSION_LEVEL = 2;
    private static final int LEARNED_PREVIEW_LIMIT = 10;
    private static final int SYNC_DEBUG_PREVIEW_LIMIT = 20;

    private TeamEmcCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("teamemc")
                .then(Commands.literal("balance")
                        .executes(TeamEmcCommand::balance))
                .then(Commands.literal("learned")
                        .executes(TeamEmcCommand::learned))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(OP_PERMISSION_LEVEL))
                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                .executes(TeamEmcCommand::set)))
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(OP_PERMISSION_LEVEL))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                .executes(TeamEmcCommand::add)))
                .then(Commands.literal("spend")
                        .requires(source -> source.hasPermission(OP_PERMISSION_LEVEL))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                .executes(TeamEmcCommand::spend)))
                .then(Commands.literal("learn")
                        .requires(source -> source.hasPermission(OP_PERMISSION_LEVEL))
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .executes(TeamEmcCommand::learn)))
                .then(Commands.literal("emc")
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .executes(TeamEmcCommand::emc)))
                .then(Commands.literal("emc_debug")
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .executes(TeamEmcCommand::emcDebug)))
                .then(Commands.literal("emc_held")
                        .executes(TeamEmcCommand::emcHeld))
                .then(Commands.literal("sync_debug")
                        .executes(TeamEmcCommand::syncDebug))
                .then(Commands.literal("emc_stats")
                        .executes(TeamEmcCommand::emcStats)));
    }

    private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        TeamEmcSavedData data = TeamEmcSavedData.get(source.getServer());
        String accountKey = data.getAccountKey(player);
        long balance = data.getBalance(player);

        source.sendSuccess(() -> Component.literal("Team EMC account " + accountKey + " balance: " + balance), false);
        return 1;
    }

    private static int learned(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        TeamEmcSavedData data = TeamEmcSavedData.get(source.getServer());
        List<ResourceLocation> learnedItems = data.getLearnedItems(player).stream()
                .sorted()
                .toList();

        source.sendSuccess(
                () -> Component.literal("Team EMC account " + data.getAccountKey(player) + " learned items: " + learnedItems.size()),
                false
        );

        if (!learnedItems.isEmpty()) {
            String preview = learnedItems.stream()
                    .limit(LEARNED_PREVIEW_LIMIT)
                    .map(ResourceLocation::toString)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            if (learnedItems.size() > LEARNED_PREVIEW_LIMIT) {
                preview += ", ...";
            }

            String finalPreview = preview;
            source.sendSuccess(() -> Component.literal("Learned preview: " + finalPreview), false);
        }

        return learnedItems.size();
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        long amount = LongArgumentType.getLong(context, "amount");
        TeamEmcSavedData data = TeamEmcSavedData.get(source.getServer());

        data.setBalance(player, amount);
        ModNetworking.sendEmcData(player);
        source.sendSuccess(() -> Component.literal("Set Team EMC balance to " + amount + "."), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        long amount = LongArgumentType.getLong(context, "amount");
        TeamEmcSavedData data = TeamEmcSavedData.get(source.getServer());

        if (!data.addEmc(player, amount)) {
            source.sendFailure(Component.literal("Could not add EMC. The balance would overflow."));
            return 0;
        }

        ModNetworking.sendEmcData(player);
        source.sendSuccess(() -> Component.literal("Added " + amount + " EMC. New balance: " + data.getBalance(player)), true);
        return 1;
    }

    private static int spend(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        long amount = LongArgumentType.getLong(context, "amount");
        TeamEmcSavedData data = TeamEmcSavedData.get(source.getServer());

        if (!data.trySpendEmc(player, amount)) {
            source.sendFailure(Component.literal("Not enough EMC to spend " + amount + ". Current balance: " + data.getBalance(player)));
            return 0;
        }

        ModNetworking.sendEmcData(player);
        source.sendSuccess(() -> Component.literal("Spent " + amount + " EMC. New balance: " + data.getBalance(player)), true);
        return 1;
    }

    private static int learn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Item item = ItemArgument.getItem(context, "item").getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        TeamEmcSavedData data = TeamEmcSavedData.get(source.getServer());

        EmcValueManager.ensureDerived(source.getServer());
        if (itemId == null || EmcValueManager.isBlockedModItem(itemId) || !EmcValueManager.hasEmc(item)) {
            source.sendFailure(Component.literal("Item " + itemId + " has no EMC and cannot be learned."));
            return 0;
        }

        data.learn(player, item);
        ModNetworking.sendEmcData(player);
        source.sendSuccess(() -> Component.literal("Learned item " + itemId + " for account " + data.getAccountKey(player) + "."), true);
        return 1;
    }

    private static int emc(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        EmcValueManager.ensureDerived(source.getServer());

        Item item = ItemArgument.getItem(context, "item").getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        long baseEmc = EmcValueManager.getBaseItemEmc(item);

        if (baseEmc <= 0L) {
            source.sendFailure(Component.literal("Item " + itemId + " has no EMC."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Item " + itemId + " base EMC: " + baseEmc), false);
        return 1;
    }

    private static int emcHeld(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        EmcValueManager.ensureDerived(source.getServer());

        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();

        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Your main hand is empty."));
            return 0;
        }

        Item item = stack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (!EmcValueManager.hasEmc(item)) {
            source.sendFailure(Component.literal("Item " + itemId + " has no EMC."));
            return 0;
        }

        OptionalLong singleEmc = EmcValueManager.getSingleItemEmc(stack);
        if (singleEmc.isEmpty()) {
            if (EmcValueManager.isDamageable(stack)) {
                long remainingDurability = EmcValueManager.getRemainingDurability(stack);
                long maxDurability = EmcValueManager.getMaxDurability(stack);
                source.sendFailure(Component.literal(
                        "Could not calculate held stack EMC. Durability is invalid or too low. Remaining: "
                                + remainingDurability + "/" + maxDurability + "."
                ));
            } else {
                source.sendFailure(Component.literal("Could not calculate held stack EMC."));
            }
            return 0;
        }

        OptionalLong stackEmc = EmcValueManager.getStackEmc(stack);
        if (stackEmc.isEmpty()) {
            source.sendFailure(Component.literal("Could not calculate held stack EMC because the total value would overflow."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Held stack " + stack.getCount() + "x " + itemId
                        + " EMC: " + stackEmc.getAsLong()
                        + " (" + singleEmc.getAsLong() + " each)"
        ), false);
        return 1;
    }

    private static int emcDebug(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        EmcValueManager.ensureDerived(source.getServer());

        Item item = ItemArgument.getItem(context, "item").getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        ItemStack stack = new ItemStack(item, 1);
        long baseEmc = EmcValueManager.getBaseItemEmc(item);
        OptionalLong stackEmc = EmcValueManager.getStackEmc(stack);
        boolean existsInRegistry = itemId != null && BuiltInRegistries.ITEM.containsKey(itemId);
        boolean blocked = itemId != null && EmcValueManager.isBlockedModItem(itemId);
        boolean inEmcMap = EmcValueManager.isInEmcMap(item);
        String sourceName = EmcValueManager.getBaseItemEmcSource(item).orElse("not in EMC map");

        source.sendSuccess(() -> Component.literal(
                "Team EMC debug: item=" + itemId
                        + ", serverRegistryExists=" + existsInRegistry
                        + ", blocked=" + blocked
                        + ", inEmcMap=" + inEmcMap
                        + ", source=" + sourceName
        ), false);
        source.sendSuccess(() -> Component.literal(
                "Team EMC debug values: baseEmc=" + baseEmc
                        + ", stackEmc=" + (stackEmc.isPresent() ? Long.toString(stackEmc.getAsLong()) : "none")
                        + ", hasEmc(Item)=" + EmcValueManager.hasEmc(item)
                        + ", hasEmc(ItemStack)=" + EmcValueManager.hasEmc(stack)
        ), false);
        return baseEmc > 0L ? 1 : 0;
    }

    private static int emcStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        EmcValueManager.ensureDerived(source.getServer());

        EmcValueManager.EmcStats stats = EmcValueManager.getStats();
        String runtimeSide = source.getServer().isDedicatedServer() ? "dedicated server" : "integrated server";
        String fileNames = stats.emcFileNames().isEmpty() ? "<none>" : String.join(", ", stats.emcFileNames());

        source.sendSuccess(() -> Component.literal(
                "Team EMC stats: side=" + runtimeSide
                        + ", manual=" + stats.manualBaseCount()
                        + ", derived=" + stats.derivedCount()
                        + ", total=" + stats.totalCount()
                        + ", files=" + stats.emcFileCount()
                        + ", skipped=" + stats.skippedEntryCount()
                        + ", recipeDerivation=" + stats.recipeDerivationEnabled()
                        + ", projecteImported=" + stats.importedItemCount()
                        + ", projecteImportedFile=" + stats.projecteImportedPresent()
                        + ", iterations=" + stats.lastDerivationIterations()
                        + ", addOrUpdate=" + stats.lastDerivationChanges()
                        + ", complete=" + stats.derivationComplete()
        ), false);
        source.sendSuccess(() -> Component.literal("Team EMC files: " + fileNames), false);
        return stats.totalCount();
    }

    private static int syncDebug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        TeamEmcSavedData data = TeamEmcSavedData.get(source.getServer());
        Team team = player.getTeam();
        String teamName = team == null ? "<none>" : team.getName();
        List<ResourceLocation> learnedItems = data.getLearnedItems(player).stream()
                .sorted()
                .toList();
        boolean transmutationMenuOpen = player.containerMenu instanceof TransmutationMenu;

        source.sendSuccess(() -> Component.literal(
                "Team EMC sync debug: account=" + data.getAccountKey(player)
                        + ", scoreboardTeam=" + teamName
                        + ", balance=" + data.getBalance(player)
                        + ", learnedItems=" + learnedItems.size()
                        + ", transmutationMenuOpen=" + transmutationMenuOpen
        ), false);
        source.sendSuccess(() -> Component.literal("Team EMC learned preview: "
                + previewItemIds(learnedItems, SYNC_DEBUG_PREVIEW_LIMIT)), false);

        if (transmutationMenuOpen) {
            ModNetworking.sendEmcData(player);
            source.sendSuccess(() -> Component.literal("Sent SyncEmcDataPacket to current player."), false);
        }

        return learnedItems.size();
    }

    private static String previewItemIds(List<ResourceLocation> itemIds, int limit) {
        String preview = itemIds.stream()
                .limit(limit)
                .map(ResourceLocation::toString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("<empty>");
        return itemIds.size() > limit ? preview + ", ..." : preview;
    }
}
