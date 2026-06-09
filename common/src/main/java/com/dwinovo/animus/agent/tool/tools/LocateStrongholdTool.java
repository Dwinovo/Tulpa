package com.dwinovo.animus.agent.tool.tools;

import com.dwinovo.animus.agent.tool.AnimusTool;
import com.dwinovo.animus.task.TaskRecord;
import com.dwinovo.animus.task.tasks.LocateStrongholdTaskRecord;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code locate_stronghold} tool — find the nearest stronghold, which holds
 * the End portal (the only way to the End and the Ender Dragon).
 *
 * <h2>Why it exists</h2>
 * Strongholds are buried and effectively unfindable by walking. Vanilla's
 * intended locator is the eye of ender (throw, watch it fly, repeat) — but that
 * is fundamentally the structure lookup {@code findNearestMapStructure} run on
 * the {@code EYE_OF_ENDER_LOCATED} tag. This tool runs that lookup directly:
 * one call, exact coordinates, no eyes spent. The eyes are then saved for the
 * 12 portal frames, where they're actually required.
 *
 * <h2>No parameters</h2>
 * Origin is the entity's position; radius is fixed at the vanilla 100 chunks.
 */
public final class LocateStrongholdTool implements AnimusTool {

    private static final long TIMEOUT_TICKS = 5 * 20; // structure lookup is fast

    @Override
    public String name() {
        return LocateStrongholdTaskRecord.TOOL_NAME;
    }

    @Override
    public String description() {
        return "Locate the nearest stronghold — it contains the End portal, the "
                + "only route to the End and the Ender Dragon. Returns the "
                + "stronghold's coordinates, compass direction, and horizontal "
                + "distance (or reports none within range). This is the direct, "
                + "eye-free equivalent of throwing eyes of ender — save your eyes "
                + "for the 12 portal frames. After locating: move_to the spot, "
                + "scan_blocks for the end_portal_frame ring, then use_item an "
                + "ender_eye on each empty frame (the 12th activates the portal).";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("required", List.of());
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public long defaultTimeoutTicks() {
        return TIMEOUT_TICKS;
    }

    @Override
    public TaskRecord toTaskRecord(String toolCallId, JsonObject args, long currentGameTime) {
        return new LocateStrongholdTaskRecord(toolCallId, currentGameTime + TIMEOUT_TICKS);
    }
}
