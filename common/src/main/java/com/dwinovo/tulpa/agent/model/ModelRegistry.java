package com.dwinovo.tulpa.agent.model;

import com.dwinovo.tulpa.Constants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The litellm-style model registry, loaded once from the bundled {@code /tulpa_models.json}: per provider
 * a base URL + a list of known models, each with its context window (tokens). Single source of truth for
 * the settings dropdowns AND the per-model auto-compaction threshold. Every provider also accepts a custom
 * model id (the settings "custom" escape hatch); {@code custom} providers are free-form (any
 * OpenAI-compatible endpoint).
 */
public final class ModelRegistry {

    public record Model(String id, int ctx, boolean reasoning) {}
    public record Provider(String id, String name, String baseUrl, boolean custom, List<Model> models) {}

    /** Fallback context window for an unknown model (e.g. a custom one). */
    public static final int DEFAULT_CTX = 64_000;

    private static final List<Provider> PROVIDERS = load();

    private ModelRegistry() {}

    private static List<Provider> load() {
        List<Provider> out = new ArrayList<>();
        try (var in = ModelRegistry.class.getResourceAsStream("/tulpa_models.json")) {
            if (in == null) {
                Constants.LOG.error("[tulpa] tulpa_models.json not found on the classpath");
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var pe : root.getAsJsonArray("providers")) {
                JsonObject p = pe.getAsJsonObject();
                List<Model> models = new ArrayList<>();
                if (p.has("models")) {
                    for (var me : p.getAsJsonArray("models")) {
                        JsonObject m = me.getAsJsonObject();
                        models.add(new Model(
                                m.get("id").getAsString(),
                                m.has("ctx") ? m.get("ctx").getAsInt() : DEFAULT_CTX,
                                m.has("reasoning") && m.get("reasoning").getAsBoolean()));
                    }
                }
                out.add(new Provider(
                        p.get("id").getAsString(),
                        p.get("name").getAsString(),
                        p.has("baseUrl") ? p.get("baseUrl").getAsString() : "",
                        p.has("custom") && p.get("custom").getAsBoolean(),
                        List.copyOf(models)));
            }
        } catch (Exception e) {
            Constants.LOG.error("[tulpa] failed to load tulpa_models.json", e);
        }
        return List.copyOf(out);
    }

    public static List<Provider> providers() { return PROVIDERS; }

    /** Provider by id, or the first one (or null if the registry is empty). */
    public static Provider provider(String id) {
        for (Provider p : PROVIDERS) {
            if (p.id().equals(id)) return p;
        }
        return PROVIDERS.isEmpty() ? null : PROVIDERS.get(0);
    }

    /** Context window for a (provider, model) pair, or {@link #DEFAULT_CTX} if unknown / custom. */
    public static int contextWindow(String providerId, String modelId) {
        Provider p = provider(providerId);
        if (p != null && modelId != null) {
            for (Model m : p.models()) {
                if (m.id().equals(modelId)) return m.ctx();
            }
        }
        return DEFAULT_CTX;
    }
}
