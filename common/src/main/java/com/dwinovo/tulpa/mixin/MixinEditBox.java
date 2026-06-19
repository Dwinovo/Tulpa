package com.dwinovo.tulpa.mixin;

import com.dwinovo.tulpa.client.screen.ShadowlessText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.5 has no {@code EditBox.setTextShadow} toggle (it arrived in 1.21.6), so the
 * mod's text fields would render with the vanilla drop shadow — clashing with the
 * flat Cottage UI. A box opts in via {@link ShadowlessText}; this redirects the two
 * text draws in {@code EditBox.renderWidget} (the typed {@link FormattedCharSequence}
 * and the {@link Component} hint) to the shadow-flag overload, passing {@code false}
 * ONLY for marked boxes — vanilla and other mods' fields are untouched.
 */
@Mixin(EditBox.class)
public abstract class MixinEditBox implements ShadowlessText {

    @Unique private boolean tulpa$noTextShadow;

    @Override
    public void tulpa$setNoTextShadow(boolean noShadow) {
        this.tulpa$noTextShadow = noShadow;
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int tulpa$textNoShadow(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        return g.drawString(font, text, x, y, color, !this.tulpa$noTextShadow);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int tulpa$hintNoShadow(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        return g.drawString(font, text, x, y, color, !this.tulpa$noTextShadow);
    }
}
