package com.example.teamemc.client;

import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.menu.TransmutationMenu;
import com.example.teamemc.network.RequestConvertCarriedPacket;
import com.example.teamemc.network.RequestWithdrawPacket;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class TransmutationScreen extends AbstractContainerScreen<TransmutationMenu> {
    private static final int SLOT_COLOR = 0xFF15191F;
    private static final int SLOT_BORDER_COLOR = 0xFF59616D;
    private static final Component SEARCH_HINT = Component.translatable("gui.teamemc.search");

    private static final int LEARNED_GRID_X = 184;
    private static final int LEARNED_SEARCH_Y = 18;
    private static final int LEARNED_SEARCH_WIDTH = 216;
    private static final int LEARNED_SEARCH_HEIGHT = 16;
    private static final int LEARNED_GRID_Y = 40;
    private static final int LEARNED_GRID_COLUMNS = 12;
    private static final int LEARNED_GRID_ROWS = 6;
    private static final int LEARNED_CELL_SIZE = 18;
    private static final int LEARNED_VISIBLE_ITEM_COUNT = LEARNED_GRID_COLUMNS * LEARNED_GRID_ROWS;
    private static final int SCROLLBAR_X = LEARNED_GRID_X + LEARNED_GRID_COLUMNS * LEARNED_CELL_SIZE + 5;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int STATUS_X = 8;
    private static final int STATUS_Y = 200;

    private int scrollOffsetRows;
    private EditBox searchBox;
    private boolean sentCarriedConversionThisClick;

    public TransmutationScreen(TransmutationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 416;
        this.imageHeight = 216;
        this.inventoryLabelY = 72;
        ClientEmcState.clearStatus();
    }

    @Override
    protected void init() {
        super.init();
        this.searchBox = this.addRenderableWidget(new EditBox(
                this.font,
                this.leftPos + LEARNED_GRID_X,
                this.topPos + LEARNED_SEARCH_Y,
                LEARNED_SEARCH_WIDTH,
                LEARNED_SEARCH_HEIGHT,
                SEARCH_HINT
        ));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(SEARCH_HINT);
        this.searchBox.setResponder(value -> {
            this.scrollOffsetRows = 0;
            this.clampScrollOffset(this.getDisplayableLearnedItems().size());
        });
        this.clampScrollOffset(this.getDisplayableLearnedItems().size());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        this.renderLearnedItemTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused() && keyCode != 256) {
            this.searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox.charTyped(codePoint, modifiers);
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.sentCarriedConversionThisClick = false;
        if (button == 0 && !this.menu.getCarried().isEmpty() && this.isInsideLearnedGridArea(mouseX, mouseY)) {
            PacketDistributor.sendToServer(RequestConvertCarriedPacket.INSTANCE);
            this.sentCarriedConversionThisClick = true;
            return true;
        }

        if (button == 0 && this.menu.getCarried().isEmpty()) {
            ResourceLocation hoveredItemId = this.getHoveredLearnedItemId((int) mouseX, (int) mouseY);
            if (hoveredItemId != null) {
                PacketDistributor.sendToServer(new RequestWithdrawPacket(hoveredItemId, hasShiftDown() ? 64 : 1));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.sentCarriedConversionThisClick) {
            this.sentCarriedConversionThisClick = false;
            return true;
        }

        if (button == 0 && !this.menu.getCarried().isEmpty() && this.isInsideLearnedGridArea(mouseX, mouseY)) {
            PacketDistributor.sendToServer(RequestConvertCarriedPacket.INSTANCE);
            return true;
        }

        this.sentCarriedConversionThisClick = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isInsideLearnedScrollArea(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (scrollY < 0.0D) {
            this.scrollOffsetRows++;
        } else if (scrollY > 0.0D) {
            this.scrollOffsetRows--;
        } else {
            return false;
        }

        this.clampScrollOffset(this.getDisplayableLearnedItems().size());
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF20242A);
        guiGraphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, 0xFF303640);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.drawSlot(guiGraphics, 8 + column * 18, 84 + row * 18);
            }
        }

        for (int column = 0; column < 9; column++) {
            this.drawSlot(guiGraphics, 8 + column * 18, 142);
        }

        this.renderLearnedItemGrid(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xE6E6E6, false);
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.teamemc.learned_items"),
                LEARNED_GRID_X,
                6,
                0xE6E6E6,
                false
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.teamemc.balance", ClientEmcState.getBalance()),
                8,
                46,
                0xE6E6E6,
                false
        );
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.teamemc.learned_count", ClientEmcState.getLearnedCount()),
                8,
                58,
                0xE6E6E6,
                false
        );
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xB8C0CC, false);
        this.renderStatus(guiGraphics);
    }

    private void renderStatus(GuiGraphics guiGraphics) {
        if (ClientEmcState.hasStatus()) {
            int color = ClientEmcState.isStatusError() ? 0xFF7777 : 0x93E6A1;
            String statusText = ClientEmcState.getStatus().getString();
            int maxWidth = this.imageWidth - STATUS_X - 8;
            if (this.font.width(statusText) > maxWidth) {
                String ellipsis = "...";
                statusText = this.font.plainSubstrByWidth(statusText, Math.max(0, maxWidth - this.font.width(ellipsis))) + ellipsis;
            }
            guiGraphics.drawString(this.font, statusText, STATUS_X, STATUS_Y, color, false);
        }
    }

    private void renderLearnedItemGrid(GuiGraphics guiGraphics) {
        List<ItemStack> learnedItems = this.getDisplayableLearnedItems();
        this.clampScrollOffset(learnedItems.size());

        for (int row = 0; row < LEARNED_GRID_ROWS; row++) {
            for (int column = 0; column < LEARNED_GRID_COLUMNS; column++) {
                this.drawSlot(guiGraphics, LEARNED_GRID_X + column * LEARNED_CELL_SIZE, LEARNED_GRID_Y + row * LEARNED_CELL_SIZE);
            }
        }

        int firstItemIndex = this.scrollOffsetRows * LEARNED_GRID_COLUMNS;
        int lastItemIndex = Math.min(firstItemIndex + LEARNED_VISIBLE_ITEM_COUNT, learnedItems.size());
        for (int itemIndex = firstItemIndex; itemIndex < lastItemIndex; itemIndex++) {
            int pageIndex = itemIndex - firstItemIndex;
            int column = pageIndex % LEARNED_GRID_COLUMNS;
            int row = pageIndex / LEARNED_GRID_COLUMNS;
            int x = this.leftPos + LEARNED_GRID_X + column * LEARNED_CELL_SIZE + 1;
            int y = this.topPos + LEARNED_GRID_Y + row * LEARNED_CELL_SIZE + 1;
            guiGraphics.renderItem(learnedItems.get(itemIndex), x, y);
        }

        this.renderScrollbar(guiGraphics, learnedItems.size());
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int itemCount) {
        int totalRows = this.getTotalRows(itemCount);
        int maxScrollRows = this.getMaxScrollRows(itemCount);
        if (totalRows <= LEARNED_GRID_ROWS) {
            return;
        }

        int trackX = this.leftPos + SCROLLBAR_X;
        int trackY = this.topPos + LEARNED_GRID_Y;
        int trackHeight = LEARNED_GRID_ROWS * LEARNED_CELL_SIZE;
        int thumbHeight = Math.max(12, trackHeight * LEARNED_GRID_ROWS / totalRows);
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = trackY + (maxScrollRows == 0 ? 0 : this.scrollOffsetRows * thumbTravel / maxScrollRows);

        guiGraphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, 0xFF15191F);
        guiGraphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF8A93A1);
    }

    private void renderLearnedItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.menu.getCarried().isEmpty()) {
            return;
        }

        ItemStack hoveredStack = this.getHoveredLearnedItem(mouseX, mouseY);
        if (!hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    private ItemStack getHoveredLearnedItem(int mouseX, int mouseY) {
        ResourceLocation hoveredItemId = this.getHoveredLearnedItemId(mouseX, mouseY);
        return hoveredItemId == null ? ItemStack.EMPTY : createDisplayStack(hoveredItemId);
    }

    private ResourceLocation getHoveredLearnedItemId(int mouseX, int mouseY) {
        List<ItemStack> learnedItems = this.getDisplayableLearnedItems();
        this.clampScrollOffset(learnedItems.size());
        int firstItemIndex = this.scrollOffsetRows * LEARNED_GRID_COLUMNS;
        int lastItemIndex = Math.min(firstItemIndex + LEARNED_VISIBLE_ITEM_COUNT, learnedItems.size());

        for (int itemIndex = firstItemIndex; itemIndex < lastItemIndex; itemIndex++) {
            int pageIndex = itemIndex - firstItemIndex;
            int column = pageIndex % LEARNED_GRID_COLUMNS;
            int row = pageIndex / LEARNED_GRID_COLUMNS;
            int x = this.leftPos + LEARNED_GRID_X + column * LEARNED_CELL_SIZE;
            int y = this.topPos + LEARNED_GRID_Y + row * LEARNED_CELL_SIZE;
            if (mouseX >= x && mouseX < x + LEARNED_CELL_SIZE && mouseY >= y && mouseY < y + LEARNED_CELL_SIZE) {
                ItemStack stack = learnedItems.get(itemIndex);
                return BuiltInRegistries.ITEM.getKey(stack.getItem());
            }
        }

        return null;
    }

    private boolean isInsideLearnedGridArea(double mouseX, double mouseY) {
        int left = this.leftPos + LEARNED_GRID_X;
        int top = this.topPos + LEARNED_GRID_Y;
        int right = left + LEARNED_GRID_COLUMNS * LEARNED_CELL_SIZE;
        int bottom = top + LEARNED_GRID_ROWS * LEARNED_CELL_SIZE;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private boolean isInsideLearnedScrollArea(double mouseX, double mouseY) {
        int left = this.leftPos + LEARNED_GRID_X;
        int top = this.topPos + LEARNED_GRID_Y;
        int right = this.leftPos + SCROLLBAR_X + SCROLLBAR_WIDTH;
        int bottom = top + LEARNED_GRID_ROWS * LEARNED_CELL_SIZE;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private List<ItemStack> getDisplayableLearnedItems() {
        String searchQuery = this.getSearchQuery();
        return ClientEmcState.getLearnedItems().stream()
                .distinct()
                .filter(TransmutationScreen::isDisplayableItemId)
                .filter(itemId -> this.matchesSearch(itemId, searchQuery))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(TransmutationScreen::createDisplayStack)
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    private String getSearchQuery() {
        if (this.searchBox == null) {
            return "";
        }

        return this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(ResourceLocation itemId, String searchQuery) {
        if (searchQuery.isEmpty()) {
            return true;
        }

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), 1);
        String idText = itemId.toString().toLowerCase(Locale.ROOT);
        String nameText = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return idText.contains(searchQuery) || nameText.contains(searchQuery);
    }

    private static boolean isDisplayableItemId(ResourceLocation itemId) {
        if (!BuiltInRegistries.ITEM.containsKey(itemId) || EmcValueManager.isBlockedModItem(itemId)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item != Items.AIR && EmcValueManager.hasEmc(item);
    }

    private static ItemStack createDisplayStack(ResourceLocation itemId) {
        if (!isDisplayableItemId(itemId)) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(BuiltInRegistries.ITEM.get(itemId), 1);
    }

    private void clampScrollOffset(int learnedItemCount) {
        int maxScrollRows = this.getMaxScrollRows(learnedItemCount);
        if (this.scrollOffsetRows > maxScrollRows) {
            this.scrollOffsetRows = maxScrollRows;
        }
        if (this.scrollOffsetRows < 0) {
            this.scrollOffsetRows = 0;
        }
    }

    private int getMaxScrollRows(int itemCount) {
        return Math.max(0, this.getTotalRows(itemCount) - LEARNED_GRID_ROWS);
    }

    private int getTotalRows(int itemCount) {
        return (itemCount + LEARNED_GRID_COLUMNS - 1) / LEARNED_GRID_COLUMNS;
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        int left = this.leftPos + x - 1;
        int top = this.topPos + y - 1;
        guiGraphics.fill(left, top, left + 18, top + 18, SLOT_BORDER_COLOR);
        guiGraphics.fill(left + 1, top + 1, left + 17, top + 17, SLOT_COLOR);
    }
}
