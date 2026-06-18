package com.dwinovo.animus.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A small screen-driven provider picker: a collapsed box showing the current
 * provider, expanding to a list on click. Not a self-contained widget — the host
 * screen calls {@link #render} LAST (so the open list draws over other controls)
 * and routes clicks through {@link #mouseClicked} FIRST. Shared by the standalone
 * {@link SettingsScreen} and the {@link AnimusScreen} Settings tab.
 */
public final class ProviderDropdown {

    private static final int ROW = 16;
    private int x, y, w, h = 18;
    private boolean open;
    private String selectedId;

    public ProviderDropdown(String selectedId) {
        this.selectedId = LlmProviders.normalize(selectedId);
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public String selectedId() { return selectedId; }
    public boolean isOpen() { return open; }
    public void close() { open = false; }

    private static final net.minecraft.resources.Identifier FRAME =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    com.dwinovo.animus.Constants.MOD_ID, "button");

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        UiTheme th = UiTheme.current();
        var pipe = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
        g.blitSprite(pipe, FRAME, x, y, w, h);                       // parchment box (clickable)
        int ty = y + (h - 8) / 2;
        Nb.text(g, font, LlmProviders.byId(selectedId).displayName(), x + 6, ty, th.text());
        Nb.text(g, font, open ? "▴" : "▾", x + w - 12, ty, th.textDim());

        if (open) {
            int oy = y + h - 2;
            int n = LlmProviders.ALL.size();
            g.blitSprite(pipe, FRAME, x, oy, w, n * ROW + 4);        // parchment list frame
            for (int i = 0; i < n; i++) {
                LlmProviders.Option o = LlmProviders.ALL.get(i);
                int ry = oy + 2 + i * ROW;
                boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW;
                if (hover) g.fill(x + 2, ry, x + w - 2, ry + ROW, 0x33000000);
                boolean sel = o.id().equals(selectedId);
                Nb.text(g, font, o.displayName(), x + 6, ry + (ROW - 8) / 2, sel ? th.cta() : th.text());
            }
        }
    }

    /** Returns true if the click was consumed (selection made, or box/list interaction). */
    public boolean mouseClicked(double mx, double my) {
        if (open) {
            int oy = y + h;
            for (int i = 0; i < LlmProviders.ALL.size(); i++) {
                int ry = oy + i * ROW;
                if (mx >= x && mx < x + w && my >= ry && my < ry + ROW) {
                    selectedId = LlmProviders.ALL.get(i).id();
                    open = false;
                    return true;
                }
            }
            open = false;                 // any click while open closes the list (and is consumed)
            return true;
        }
        if (inBox(mx, my)) { open = true; return true; }
        return false;
    }

    private boolean inBox(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
