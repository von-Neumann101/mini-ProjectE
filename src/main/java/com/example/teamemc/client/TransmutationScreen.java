package com.example.teamemc.client;

import com.example.teamemc.emc.EmcValueManager;
import com.example.teamemc.menu.TransmutationMenu;
import com.example.teamemc.network.RequestConvertPacket;
import com.example.teamemc.network.RequestWithdrawPacket;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final Component CONVERT_BUTTON = Component.translatable("gui.teamemc.convert");
    private static final Component PREVIOUS_BUTTON = Component.translatable("gui.teamemc.previous");
    private static final Component NEXT_BUTTON = Component.translatable("gui.teamemc.next");
    private static final Component SEARCH_HINT = Component.translatable("gui.teamemc.search");

    private static final int LEARNED_GRID_X = 184;
    private static final int LEARNED_SEARCH_Y = 18;
    private static final int LEARNED_SEARCH_WIDTH = 108;
    private static final int LEARNED_SEARCH_HEIGHT = 16;
    private static final int LEARNED_GRID_Y = 40;
    private static final int LEARNED_GRID_COLUMNS = 5;
    private static final int LEARNED_GRID_ROWS = 3;
    private static final int LEARNED_PAGE_SIZE = LEARNED_GRID_COLUMNS * LEARNED_GRID_ROWS;
    private static final int PAGE_LABEL_Y = 98;
    private static final int PAGE_BUTTON_Y = 138;
    private static final int PAGE_BUTTON_WIDTH = 52;
    private static final int PAGE_BUTTON_HEIGHT = 20;
    private static final int STATUS_X = 8;
    private static final int STATUS_Y = 168;

    private int learnedPage;
    private EditBox searchBox;
    private Button previousPageButton;
    private Button nextPageButton;

    public TransmutationScreen(TransmutationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 304;
        this.imageHeight = 184;
        this.inventoryLabelY = 72;
        ClientEmcState.clearStatus();
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
            this.learnedPage = 0;
            this.updatePageButtons(this.getDisplayableLearnedItems().size());
        });
        this.previousPageButton = this.addRenderableWidget(Button.builder(
                        PREVIOUS_BUTTON,
                        button -> {
                            if (this.learnedPage > 0) {
                                this.learnedPage--;
                            }
                            this.updatePageButtons(this.getDisplayableLearnedItems().size());
                        }
                )
                .bounds(this.leftPos + LEARNED_GRID_X, this.topPos + PAGE_BUTTON_Y, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT)
                .build());
        this.nextPageButton = this.addRenderableWidget(Button.builder(
                        NEXT_BUTTON,
                        button -> {
                            int pageCount = this.getPageCount(this.getDisplayableLearnedItems().size());
                            if (this.learnedPage + 1 < pageCount) {
                                this.learnedPage++;
                            }
                            this.updatePageButtons(this.getDisplayableLearnedItems().size());
                        }
                )
                .bounds(
                        this.leftPos + LEARNED_GRID_X + PAGE_BUTTON_WIDTH + 4,
                        this.topPos + PAGE_BUTTON_Y,
                        PAGE_BUTTON_WIDTH,
                        PAGE_BUTTON_HEIGHT
                )
                .build());
        this.updatePageButtons(this.getDisplayableLearnedItems().size());
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
        int pageCount = this.getPageCount(this.getDisplayableLearnedItems().size());
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("gui.teamemc.page", this.learnedPage + 1, pageCount),
                LEARNED_GRID_X + LEARNED_GRID_COLUMNS * 9,
                PAGE_LABEL_Y,
                0xB8C0CC
        );
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xB8C0CC, false);
        if (ClientEmcState.hasStatus()) {
            int color = ClientEmcState.isStatusError() ? 0xFF7777 : 0x93E6A1;
            guiGraphics.drawString(this.font, ClientEmcState.getStatus(), STATUS_X, STATUS_Y, color, false);
        }
    }

    private void renderLearnedItemGrid(GuiGraphics guiGraphics) {
        List<ItemStack> learnedItems = this.getDisplayableLearnedItems();
        this.updatePageButtons(learnedItems.size());

        for (int row = 0; row < LEARNED_GRID_ROWS; row++) {
            for (int column = 0; column < LEARNED_GRID_COLUMNS; column++) {
                this.drawSlot(guiGraphics, LEARNED_GRID_X + column * 18, LEARNED_GRID_Y + row * 18);
            }
        }

        int firstItemIndex = this.learnedPage * LEARNED_PAGE_SIZE;
        int lastItemIndex = Math.min(firstItemIndex + LEARNED_PAGE_SIZE, learnedItems.size());
        for (int itemIndex = firstItemIndex; itemIndex < lastItemIndex; itemIndex++) {
            int pageIndex = itemIndex - firstItemIndex;
            int column = pageIndex % LEARNED_GRID_COLUMNS;
            int row = pageIndex / LEARNED_GRID_COLUMNS;
            int x = this.leftPos + LEARNED_GRID_X + column * 18 + 1;
            int y = this.topPos + LEARNED_GRID_Y + row * 18 + 1;
            guiGraphics.renderItem(learnedItems.get(itemIndex), x, y);
        }
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
        int firstItemIndex = this.learnedPage * LEARNED_PAGE_SIZE;
        int lastItemIndex = Math.min(firstItemIndex + LEARNED_PAGE_SIZE, learnedItems.size());

        for (int itemIndex = firstItemIndex; itemIndex < lastItemIndex; itemIndex++) {
            int pageIndex = itemIndex - firstItemIndex;
            int column = pageIndex % LEARNED_GRID_COLUMNS;
            int row = pageIndex / LEARNED_GRID_COLUMNS;
            int x = this.leftPos + LEARNED_GRID_X + column * 18;
            int y = this.topPos + LEARNED_GRID_Y + row * 18;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                ItemStack stack = learnedItems.get(itemIndex);
                return BuiltInRegistries.ITEM.getKey(stack.getItem());
            }
        }

        return null;
    }

    private List<ItemStack> getDisplayableLearnedItems() {
        String searchQuery = this.getSearchQuery();
        return ClientEmcState.getLearnedItems().stream()
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(TransmutationScreen::createDisplayStack)
                .filter(stack -> !stack.isEmpty())
                .filter(stack -> this.matchesSearch(stack, searchQuery))
                .toList();
    }

    private String getSearchQuery() {
        if (this.searchBox == null) {
            return "";
        }

        return this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(ItemStack stack, String searchQuery) {
        if (searchQuery.isEmpty()) {
            return true;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String idText = itemId == null ? "" : itemId.toString().toLowerCase(Locale.ROOT);
        String nameText = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return idText.contains(searchQuery) || nameText.contains(searchQuery);
    }

    private static ItemStack createDisplayStack(ResourceLocation itemId) {
        if (!BuiltInRegistries.ITEM.containsKey(itemId) || EmcValueManager.isBlockedModItem(itemId)) {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, 1);
    }

    private void updatePageButtons(int learnedItemCount) {
        int pageCount = this.getPageCount(learnedItemCount);
        if (this.learnedPage >= pageCount) {
            this.learnedPage = pageCount - 1;
        }
        if (this.learnedPage < 0) {
            this.learnedPage = 0;
        }

        if (this.previousPageButton != null) {
            this.previousPageButton.active = this.learnedPage > 0;
        }
        if (this.nextPageButton != null) {
            this.nextPageButton.active = this.learnedPage + 1 < pageCount;
        }
    }

    private int getPageCount(int itemCount) {
        return Math.max(1, (itemCount + LEARNED_PAGE_SIZE - 1) / LEARNED_PAGE_SIZE);
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        int left = this.leftPos + x - 1;
        int top = this.topPos + y - 1;
        guiGraphics.fill(left, top, left + 18, top + 18, SLOT_BORDER_COLOR);
        guiGraphics.fill(left + 1, top + 1, left + 17, top + 17, SLOT_COLOR);
    }
}
