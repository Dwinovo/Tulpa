package com.dwinovo.animus.client.screen.tabs;

import com.dwinovo.animus.client.data.ClientPlayerAnimusState;
import com.dwinovo.animus.client.screen.AnimusManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Storage tab — read-only 9 × 6 grid of the player's virtual chest. Items
 * land here via mining; the player can see what's been collected but can't
 * yet take items back to their inventory (that needs a server-synced
 * {@code AbstractContainerMenu}, deferred to a follow-up commit).
 *
 * <h2>Tooltips</h2>
 * Hovering a slot uses vanilla {@code setTooltipForNextFrame(font, stack,
 * mx, my)} so we get item names, durability bars, enchantments — same
 * rendering as inventory slots.
 */
public final class StorageTab extends Tab {

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PADDING = 1;
    private static final int COLS = 9;
    private static final int ROWS = 6;

    private int gridX, gridY;

    public StorageTab(AnimusManagerScreen parent) {
        super(parent);
    }

    @Override
    public Component title() {
        return Component.literal("Storage");
    }

    @Override
    public void onEnter(int x, int y, int width, int height) {
        int gridW = COLS * SLOT_SIZE;
        this.gridX = x + (width - gridW) / 2;
        this.gridY = y + 24;  // leave room for header label
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        ClientPlayerAnimusState state = ClientPlayerAnimusState.instance();

        // Header
        g.text(font, Component.literal("Shared Animus Storage (read-only)"),
                gridX, gridY - 14, 0xFFAAAAAA);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = row * COLS + col;
                int sx = gridX + col * SLOT_SIZE;
                int sy = gridY + row * SLOT_SIZE;
                ItemStack stack = state.storageSlot(slot);

                // Slot background.
                g.fill(sx, sy, sx + SLOT_SIZE - SLOT_PADDING, sy + SLOT_SIZE - SLOT_PADDING, 0x60000000);
                g.outline(sx, sy, SLOT_SIZE - SLOT_PADDING, SLOT_SIZE - SLOT_PADDING, 0x44FFFFFF);

                if (!stack.isEmpty()) {
                    g.item(stack, sx + 1, sy + 1);
                    g.itemDecorations(font, stack, sx + 1, sy + 1);
                }

                // Hover tooltip.
                if (mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE
                        && !stack.isEmpty()) {
                    g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }

        // Slot count footer
        int used = 0;
        for (int i = 0; i < state.storageSnapshot().length; i++) {
            if (!state.storageSlot(i).isEmpty()) used++;
        }
        g.text(font,
                Component.literal(used + " / " + (COLS * ROWS) + " slots used"),
                gridX, gridY + ROWS * SLOT_SIZE + 4, 0xFF888888);
    }
}
