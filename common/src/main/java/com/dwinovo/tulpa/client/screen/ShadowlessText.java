package com.dwinovo.tulpa.client.screen;

/**
 * Duck-typed onto vanilla {@link net.minecraft.client.gui.components.EditBox} by
 * {@code MixinEditBox}: marks a box whose text should render WITHOUT the vanilla
 * drop shadow. 1.21.5 has no {@code EditBox.setTextShadow(false)} (added in 1.21.6),
 * so the mod's flat Cottage-style text fields opt in through this marker and the
 * mixin redirects only their {@code renderWidget} text draws to the no-shadow path.
 */
public interface ShadowlessText {
    void tulpa$setNoTextShadow(boolean noShadow);
}
