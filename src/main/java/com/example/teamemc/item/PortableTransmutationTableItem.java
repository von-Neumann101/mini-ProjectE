package com.example.teamemc.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PortableTransmutationTableItem extends Item {
    public PortableTransmutationTableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (!level.isClientSide()) {
            player.displayClientMessage(Component.literal("Portable Transmutation Table GUI is not implemented yet."), true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
