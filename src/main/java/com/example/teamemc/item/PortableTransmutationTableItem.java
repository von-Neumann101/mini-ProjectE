package com.example.teamemc.item;

import com.example.teamemc.menu.TransmutationMenu;
import com.example.teamemc.registry.ModNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PortableTransmutationTableItem extends Item {
    private static final Component MENU_TITLE = Component.translatable("container.teamemc.transmutation_table");

    public PortableTransmutationTableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new TransmutationMenu(containerId, inventory),
                    MENU_TITLE
            )).ifPresent(containerId -> ModNetworking.sendEmcData(serverPlayer));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
