package com.dwinovo.numen.agent.tool.tools;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.task.TaskRecord;
import com.dwinovo.numen.task.tasks.LocateBiomeTaskRecord;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code locate_biome} tool — the LLM-facing face of vanilla's
 * {@code /locate biome}, sliced across ticks like {@code locate_structure}
 * (the Nature's Compass model: pure climate-noise sampling, no chunk loads).
 * Exists because wandering-and-scanning for a warped forest was the pearl
 * phase's biggest time sink, the same hole locate_structure plugged for
 * fortresses.
 */
public final class LocateBiomeTool implements NumenTool {

    private static final long TIMEOUT_TICKS = 30 * 20;
    private static final int MAX_ARG_LENGTH = 128;

    @Override
    public String name() {
        return LocateBiomeTaskRecord.TOOL_NAME;
    }

    @Override
    public String description() {
        return "Find the nearest biome of a given type and get its coordinates, "
                + "compass direction and distance — no walking needed. `biome` is "
                + "a biome id — minecraft:warped_forest (endermen for pearls), "
                + "minecraft:soul_sand_valley, minecraft:desert, minecraft:plains, "
                + "minecraft:dark_forest — or a #tag for families like "
                + "#minecraft:is_forest or #minecraft:is_ocean. Searches YOUR "
                + "CURRENT dimension only, ~6400 blocks out. Biome edges are "
                + "fuzzy: the answer is accurate to ~64 blocks, so move_to the "
                + "x/z (pick a sensible y) and confirm with scan_blocks or "
                + "scan_nearby_entities when you arrive.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("biome", Map.of("type", "string",
                "description", "Biome id (e.g. minecraft:warped_forest) or #tag (e.g. #minecraft:is_forest)."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("biome"));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public long defaultTimeoutTicks() {
        return TIMEOUT_TICKS;
    }

    @Override
    public TaskRecord toTaskRecord(String toolCallId, JsonObject args, long currentGameTime) {
        if (!args.has("biome") || args.get("biome").isJsonNull()) {
            throw new IllegalArgumentException("missing required argument: biome");
        }
        String biome = args.get("biome").getAsString().trim();
        if (biome.isEmpty() || biome.length() > MAX_ARG_LENGTH) {
            throw new IllegalArgumentException("invalid biome argument");
        }
        return new LocateBiomeTaskRecord(toolCallId, currentGameTime + TIMEOUT_TICKS, biome);
    }
}
