package com.dwinovo.animus.task.tasks;

import com.dwinovo.animus.entity.AnimusEntity;
import com.dwinovo.animus.task.LlmTaskGoal;
import com.dwinovo.animus.task.TaskResult;
import com.dwinovo.animus.task.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;

import java.util.HashMap;
import java.util.Map;

/**
 * Goal for {@code locate_stronghold}: one server-side
 * {@link ServerLevel#findNearestMapStructure} against the
 * {@code EYE_OF_ENDER_LOCATED} structure tag (which resolves to the stronghold)
 * — the exact lookup an eye of ender performs, minus the thrown eye. Returns
 * coordinates, an 8-point compass direction, and horizontal distance so the LLM
 * can {@code move_to} there, scan for the {@code end_portal_frame} blocks, and
 * fill them with eyes via {@code use_item}.
 *
 * <p>Single synchronous lookup — done in one {@code onTick}; vanilla makes the
 * same call on the tick an eye is thrown, so it's a safe server-tick cost.
 */
public final class LocateStrongholdTaskGoal extends LlmTaskGoal<LocateStrongholdTaskRecord> {

    /** Search radius in CHUNKS (vanilla eye of ender uses 100). */
    private static final int SEARCH_RADIUS_CHUNKS = 100;

    private BlockPos found;            // null = none in range
    private String failReason = "not on a server level";

    public LocateStrongholdTaskGoal(AnimusEntity entity) {
        super(entity, LocateStrongholdTaskRecord.TOOL_NAME, LocateStrongholdTaskRecord.class);
    }

    @Override
    protected void onStart(LocateStrongholdTaskRecord r) {
        this.found = null;
    }

    @Override
    protected void onTick(LocateStrongholdTaskRecord r) {
        if (!(entity.level() instanceof ServerLevel sl)) {
            failReason = "not on a server level";
            r.setState(TaskState.FAILED);
            return;
        }
        // Same lookup an eye of ender makes (EnderEyeItem.use). skipKnown=false so
        // it returns the nearest even if its chunks are already generated.
        found = sl.findNearestMapStructure(
                StructureTags.EYE_OF_ENDER_LOCATED, entity.blockPosition(), SEARCH_RADIUS_CHUNKS, false);
        // SUCCESS whether or not one was found — "none in range" is a valid,
        // actionable answer (move further and retry), not a failure.
        r.setState(TaskState.SUCCESS);
    }

    @Override
    protected TaskResult buildResult(LocateStrongholdTaskRecord r, TaskState finalState) {
        Map<String, Object> data = new HashMap<>();
        if (finalState == TaskState.SUCCESS && found != null) {
            BlockPos me = entity.blockPosition();
            int dx = found.getX() - me.getX();
            int dz = found.getZ() - me.getZ();
            int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
            String dir = compass(dx, dz);
            data.put("found", true);
            data.put("x", found.getX());
            data.put("y", found.getY());
            data.put("z", found.getZ());
            data.put("direction", dir);
            data.put("horizontal_distance", dist);
            return TaskResult.ok("nearest stronghold at " + found.getX() + "," + found.getY()
                    + "," + found.getZ() + " (" + dir + ", ~" + dist + " blocks). Travel there with "
                    + "move_to, scan_blocks for the end_portal_frame ring, then use_item an "
                    + "ender_eye on each of the 12 frames — the 12th activates the End portal.", data);
        }
        data.put("found", false);
        return switch (finalState) {
            case SUCCESS -> TaskResult.ok("no stronghold found within ~"
                    + (SEARCH_RADIUS_CHUNKS * 16) + " blocks — travel further and try again", data);
            case TIMEOUT -> TaskResult.timeout("stronghold search timed out");
            case CANCELLED -> TaskResult.cancelled("locate_stronghold interrupted");
            case FAILED -> TaskResult.fail(failReason, data);
            default -> TaskResult.fail("unexpected state: " + finalState, data);
        };
    }

    /** 8-point compass label from a block delta (+X east, +Z south). */
    private static String compass(int dx, int dz) {
        if (dx == 0 && dz == 0) return "here";
        String ns = dz < 0 ? "north" : (dz > 0 ? "south" : "");
        String ew = dx < 0 ? "west" : (dx > 0 ? "east" : "");
        if (ns.isEmpty()) return ew;
        if (ew.isEmpty()) return ns;
        // Drop the minor axis when one dominates, else a diagonal.
        if (Math.abs(dz) >= 2 * Math.abs(dx)) return ns;
        if (Math.abs(dx) >= 2 * Math.abs(dz)) return ew;
        return ns + "-" + ew;
    }
}
