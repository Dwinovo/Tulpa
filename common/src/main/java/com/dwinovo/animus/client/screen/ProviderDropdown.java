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

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        UiTheme th = UiTheme.current();
        g.fill(x, y, x + w, y + h, th.field());                     // parchment box (clickable)
        Nb.border(g, x, y, w, h, 2, open ? th.cta() : th.border());
        int ty = y + (h - 8) / 2;
        Nb.text(g, font, LlmProviders.byId(selectedId).displayName(), x + 6, ty, th.text());
        Nb.text(g, font, open ? "▴" : "▾", x + w - 12, ty, th.textDim());

        if (open) {
            int oy = y + h;
            int n = LlmProviders.ALL.size();
            g.fill(x, oy, x + w, oy + n * ROW, th.ground());
            for (int i = 0; i < n; i++) {
                LlmProviders.Option o = LlmProviders.ALL.get(i);
                int ry = oy + i * ROW;
                boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW;
                if (hover) g.fill(x, ry, x + w, ry + ROW, 0x33000000);
                boolean sel = o.id().equals(selectedId);
                Nb.text(g, font, o.displayName(), x + 6, ry + (ROW - 8) / 2, sel ? th.cta() : th.text());
            }
            Nb.border(g, x, oy, w, n * ROW, 2, th.border());
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
