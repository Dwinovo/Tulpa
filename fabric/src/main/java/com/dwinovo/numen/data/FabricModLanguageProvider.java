package com.dwinovo.numen.data;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

/**
 * Fabric-side translation provider. One instance per locale; both feed the
 * shared {@link ModLanguageData} catalogue so the English and Simplified
 * Chinese JSONs stay in sync key-by-key.
 */
public final class FabricModLanguageProvider extends FabricLanguageProvider {

    private final String locale;

    public FabricModLanguageProvider(FabricDataOutput output, String locale) {
        super(output, locale);   // 1.20.4: no HolderLookup.Provider future
        this.locale = locale;
    }

    @Override
    public void generateTranslations(TranslationBuilder builder) {   // 1.20.4: no registries arg
        ModLanguageData.addTranslations(locale, builder::add);
    }
}
