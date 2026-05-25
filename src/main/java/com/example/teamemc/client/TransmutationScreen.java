package com.example.teamemc.client;

import com.example.teamemc.menu.TransmutationMenu;
import com.example.teamemc.network.RequestConvertPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class TransmutationScreen extends AbstractContainerScreen<TransmutationMenu> {
    private static final int SLOT_COLOR = 0xFF15191F;
    private static final int SLOT_BORDER_COLOR = 0xFF59616D;
    private static final Component CONVERT_BUTTON = Component.translatable("gui.teamemc.convert");

    public TransmutationScreen(TransmutationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(
                        CONVERT_BUTTON,
                        button -> PacketDistributor.sendToServer(RequestConvertPacket.INSTANCE)
                )
                .bounds(this.leftPos + 108, this.topPos + 18, 58, 20)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF20242A);
        guiGraphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, 0xFF303640);
        this.drawSlot(guiGraphics, 80, 20);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.drawSlot(guiGraphics, 8 + column * 18, 84 + row * 18);
            }
        }

        for (int column = 0; column < 9; column++) {
            this.drawSlot(guiGraphics, 8 + column * 18, 142);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xE6E6E6, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xB8C0CC, false);
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        int left = this.leftPos + x - 1;
        int top = this.topPos + y - 1;
        guiGraphics.fill(left, top, left + 18, top + 18, SLOT_BORDER_COLOR);
        guiGraphics.fill(left + 1, top + 1, left + 17, top + 17, SLOT_COLOR);
    }
}
