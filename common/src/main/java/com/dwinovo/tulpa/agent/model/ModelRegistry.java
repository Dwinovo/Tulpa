package com.dwinovo.tulpa.agent.model;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.platform.Services;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The LLM "sites" registry (TouhouLittleMaid-style): per site an OpenAI-compatible base URL, optional
 * custom headers, and a list of known models with their context window (tokens). Single source of truth
 * for the settings dropdowns and the per-model auto-compaction threshold.
 *
 * <p>USER-EDITABLE: on first load the bundled {@code /tulpa_models.json} is copied to
 * {@code config/tulpa/models.json}; thereafter that file is authoritative (edit it to add your own
 * sites/models). A broken user file falls back to the bundled default.
 */
public final class ModelRegistry {

    public record Model(String id, int ctx, boolean reasoning) {}
    public record Provider(String id, String name, String baseUrl, boolean custom,
                           Map<String, String> headers, List<Model> models) {}

    /** Fallback context window for an unknown model (e.g. a custom one). */
    public static final int DEFAULT_CTX = 64_000;

    private static final List<Provider> PROVIDERS = load();

    private ModelRegistry() {}

    private static List<Provider> load() {
        String bundled = readBundled();
        String json = bundled;
        try {
            Path file = Services.PLATFORM.getConfigDir().resolve("tulpa").resolve("models.json");
            if (Files.exists(file)) {
                json = Files.readString(file, StandardCharsets.UTF_8);   // user-authoritative
            } else if (bundled != null) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, bundled, StandardCharsets.UTF_8);  // seed for editing
            }
        } catch (Exception e) {
            Constants.LOG.warn("[tulpa] couldn't read/seed config/tulpa/models.json, using bundled", e);
        }
        List<Provider> out = parse(json);
        if (out.isEmpty() && bundled != null && !bundled.equals(json)) {
            Constants.LOG.warn("[tulpa] user models.json yielded no sites, falling back to bundled");
            out = parse(bundled);
        }
        return List.copyOf(out);
    }

    private static String readBundled() {
        try (var in = ModelRegistry.class.getResourceAsStream("/tulpa_models.json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Constants.LOG.error("[tulpa] tulpa_models.json not readable", e);
            return null;
        }
    }

    private static List<Provider> parse(String json) {
        List<Provider> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(
                    new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))
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
                Map<String, String> headers = new LinkedHashMap<>();
                if (p.has("headers")) {
                    for (var h : p.getAsJsonObject("headers").entrySet()) {
                        headers.put(h.getKey(), h.getValue().getAsString());
                    }
                }
                out.add(new Provider(
                        p.get("id").getAsString(),
                        p.get("name").getAsString(),
                        p.has("baseUrl") ? p.get("baseUrl").getAsString() : "",
                        p.has("custom") && p.get("custom").getAsBoolean(),
                        Map.copyOf(headers),
                        List.copyOf(models)));
            }
        } catch (Exception e) {
            Constants.LOG.error("[tulpa] failed to parse models.json", e);
        }
        return out;
    }

    public static List<Provider> providers() { return PROVIDERS; }

    /** Provider by id, or the first one (or null if the registry is empty). */
    public static Provider provider(String id) {
        for (Provider p : PROVIDERS) {
            if (p.id().equals(id)) return p;
        }
        return PROVIDERS.isEmpty() ? null : PROVIDERS.get(0);
    }

    /** Custom request headers for a site (empty if none). */
    public static Map<String, String> headers(String providerId) {
        Provider p = provider(providerId);
        return p == null ? Map.of() : p.headers();
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
