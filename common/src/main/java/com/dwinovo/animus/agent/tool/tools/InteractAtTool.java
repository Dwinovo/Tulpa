package com.dwinovo.animus.agent.tool.tools;

import com.dwinovo.animus.agent.tool.AnimusTool;
import com.dwinovo.animus.task.TaskRecord;
import com.dwinovo.animus.task.tasks.InteractAtTaskRecord;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code interact_at} — the point-aimed native interaction (the BLOCK + AIR columns of
 * vanilla's mouse input). Travel to the aim, look at it, press one mouse button on whatever
 * the crosshair raytrace resolves. Entities are handled by {@code interact_entity}.
 */
public final class InteractAtTool implements AnimusTool {

    private static final long TIMEOUT_TICKS = 30 * 20;   // covers walking to the aim

    @Override
    public String name() {
        return InteractAtTaskRecord.TOOL_NAME;
    }

    @Override
    public String description() {
        return "Aim at a world point and press one mouse button — the native crosshair "
                + "interaction for BLOCKS and the AIR (entities use interact_entity). Auto-paths "
                + "to within reach like move_to, then a raytrace resolves what's under the aim.\n"
                + "• button=right (use): activate the block at x,y,z (lever/button/door/bed/"
                + "crafting table/modded machine GUI) with whatever is in hand. If x,y,z point at "
                + "CLEAR AIR (or are omitted), the held item is used in that direction instead — "
                + "throw an ender_pearl/snowball toward x,y,z, or eat/drink (omit x,y,z). Equip "
                + "the item first.\n"
                + "• button=left (attack): break the block at x,y,z (native timed; the right tool "
                + "must be in inventory). Left-click on air does nothing.\n"
                + "hold_ticks: 0 = a single press; >0 = HOLD that many ticks (a modded machine that "
                + "needs continuous right-click, or a bow draw); -1 = hold until it finishes or the "
                + "task times out. Throwing/aiming a pearl: aim a little ABOVE the destination "
                + "(thrown items arc). For breaking/placing routine blocks prefer break_block/"
                + "place_block.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("button", Map.of("type", "string", "enum", List.of("left", "right"),
                "description", "right = use/activate/throw, left = attack/break."));
        properties.put("x", Map.of("type", List.of("integer", "null"),
                "description", "Aim X. Null (with y,z null) = use the held item straight ahead (eat/drink)."));
        properties.put("y", Map.of("type", List.of("integer", "null"),
                "description", "Aim Y. Null when aiming forward."));
        properties.put("z", Map.of("type", List.of("integer", "null"),
                "description", "Aim Z. Null when aiming forward."));
        properties.put("hold_ticks", Map.of("type", List.of("integer", "null"),
                "description", "0/null = single press; >0 = hold that many ticks; -1 = hold until done/timeout."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("button", "x", "y", "z", "hold_ticks"));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public long defaultTimeoutTicks() {
        return TIMEOUT_TICKS;
    }

    @Override
    public TaskRecord toTaskRecord(String toolCallId, JsonObject args, long currentGameTime) {
        InteractAtTaskRecord.Button button = readButton(args);
        Integer x = optionalInt(args, "x");
        Integer y = optionalInt(args, "y");
        Integer z = optionalInt(args, "z");
        int holdTicks = optionalIntOr(args, "hold_ticks", 0);

        BlockPos aim = null;
        if (x != null || y != null || z != null) {
            if (x == null || y == null || z == null) {
                throw new IllegalArgumentException(
                        "an aim point needs all of x, y, z (or leave all null to use the held item straight ahead).");
            }
            aim = new BlockPos(x, y, z);
        }
        return new InteractAtTaskRecord(toolCallId, currentGameTime + TIMEOUT_TICKS, button, aim, holdTicks);
    }

    private static InteractAtTaskRecord.Button readButton(JsonObject args) {
        if (!args.has("button") || args.get("button").isJsonNull()) {
            throw new IllegalArgumentException("missing required argument: button");
        }
        return switch (args.get("button").getAsString()) {
            case "left" -> InteractAtTaskRecord.Button.LEFT;
            case "right" -> InteractAtTaskRecord.Button.RIGHT;
            default -> throw new IllegalArgumentException(
                    "button must be 'left' or 'right', got: " + args.get("button").getAsString());
        };
    }

    private static Integer optionalInt(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            return null;
        }
        try {
            return args.get(key).getAsInt();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("argument '" + key + "' must be an integer or null: " + ex.getMessage());
        }
    }

    private static int optionalIntOr(JsonObject args, String key, int fallback) {
        Integer v = optionalInt(args, key);
        return v != null ? v : fallback;
    }
}
