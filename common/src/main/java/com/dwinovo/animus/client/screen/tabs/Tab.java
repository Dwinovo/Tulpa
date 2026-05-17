package com.dwinovo.animus.client.screen.tabs;

import com.dwinovo.animus.client.screen.AnimusManagerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One sub-tab of {@link AnimusManagerScreen}. Lifecycle:
 *
 * <ol>
 *   <li>{@link #onEnter} — called when the tab becomes active. Add widgets
 *       to {@link #parent} via {@code parent.addRenderableWidget(...)}.</li>
 *   <li>{@link #render} — called every frame from
 *       {@link AnimusManagerScreen#extractRenderState}. Use it for
 *       non-widget content (text labels, panel backgrounds, hover
 *       tooltips).</li>
 *   <li>{@link #tick} — called every client tick. Use for animations,
 *       polling for state changes from the agent loop.</li>
 *   <li>Tabs aren't notified on exit — switching just re-{@code init}s the
 *       parent screen, which clears all widgets and re-adds those of the
 *       new active tab.</li>
 * </ol>
 */
public abstract class Tab {

    protected final AnimusManagerScreen parent;

    protected Tab(AnimusManagerScreen parent) {
        this.parent = parent;
    }

    /** Tab title shown in the top tab bar. */
    public abstract Component title();

    /** Called when this tab becomes active. Add widgets here. */
    public abstract void onEnter(int contentX, int contentY, int contentWidth, int contentHeight);

    /** Per-frame non-widget rendering inside the content area. */
    public abstract void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

    /** Per-tick poll hook (default no-op). */
    public void tick() {}
}
