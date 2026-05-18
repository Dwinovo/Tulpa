package com.dwinovo.animus.client.screen.tabs;

import com.dwinovo.animus.client.data.ClientPlayerAnimusState;
import com.dwinovo.animus.client.screen.AnimusManagerScreen;
import com.dwinovo.animus.client.screen.SimpleButton;
import com.dwinovo.animus.network.payload.OpenStorageMenuPayload;
import com.dwinovo.animus.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Storage tab — 9 × 6 read-only preview of the player's virtual chest,
 * plus an "Open inventory" button that opens the storage as a standard
 * vanilla chest GUI (server-synced via {@code MenuType.GENERIC_9x6}) so
 * the player can actually move items between storage and their personal
 * inventory.
 *
 * <h2>Why preview + open button instead of in-place chest GUI</h2>
 * Embedding a live menu inside a tab requires re-implementing slot
 * synchronisation manually — vanilla's {@code AbstractContainerScreen}
 * assumes it's the top-level screen. The two-stage flow (preview here,
 * full chest UI behind a button) gets us both: glance-able preview when
 * skimming the manager, full interaction when needed.
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

        // "Open inventory" button below the grid. Closes the manager + sends
        // OpenStorageMenuPayload; server opens vanilla chest UI synced from
        // the player's PlayerAnimusStorage.
        int buttonY = gridY + ROWS * SLOT_SIZE + 18;
        int buttonW = 140;
        int buttonX = x + (width - buttonW) / 2;
        SimpleButton openBtn = new SimpleButton(buttonX, buttonY, buttonW, 18,
                Component.literal("Open inventory"),
                b -> {
                    Services.NETWORK.sendToServer(OpenStorageMenuPayload.instance());
                    // Don't close the manager ourselves — server's openMenu
                    // sends a ClientboundOpenScreenPacket which replaces the
                    // current screen with ChestScreen automatically.
                });
        parent.registerTabWidget(openBtn);
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
