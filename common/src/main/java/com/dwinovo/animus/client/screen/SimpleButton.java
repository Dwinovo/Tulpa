package com.dwinovo.animus.client.screen;

import com.dwinovo.animus.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The Animus button: a vanilla GUI sprite (nine-slice, so it stretches to any width
 * with a crisp border), drawn with {@code blitSprite} like vanilla widgets — idle /
 * highlighted / disabled states from three sprites under
 * {@code textures/gui/sprites/button*.png}. Label is flat (shadowless) + coloured.
 */
public final class SimpleButton extends Button {

    private static final Identifier IDLE = sprite("button");
    private static final Identifier HOVER = sprite("button_highlighted");
    private static final Identifier DISABLED = sprite("button_disabled");

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, name);
    }

    public SimpleButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        super(x, y, width, height, message, onPress,
                defaultNarrationSupplier -> defaultNarrationSupplier.get());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hovered = active && isHoveredOrFocused();
        Identifier sprite = !active ? DISABLED : (hovered ? HOVER : IDLE);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);

        Font font = Minecraft.getInstance().font;
        int color = active ? UiTheme.current().text() : 0xFF6E5E48;
        int tw = font.width(getMessage());
        Nb.text(g, font, getMessage(), x + (w - tw) / 2, y + (h - 8) / 2, color);   // flat, coloured
    }
}
