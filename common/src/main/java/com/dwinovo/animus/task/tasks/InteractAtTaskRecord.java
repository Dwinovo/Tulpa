package com.dwinovo.animus.task.tasks;

import com.dwinovo.animus.task.TaskRecord;
import net.minecraft.core.BlockPos;

/**
 * Typed descriptor for {@code interact_at} — the point-aimed half of the native
 * crosshair interaction (the BLOCK and AIR columns of vanilla's
 * {@code startAttack}/{@code startUseItem}; the ENTITY column is {@code interact_entity}).
 *
 * <p>Aim at a world point and press a mouse button; the native raytrace resolves whatever
 * is actually under the aim:
 * <ul>
 *   <li>{@link Button#LEFT} (attack): break the block hit (held until gone); air = nothing.</li>
 *   <li>{@link Button#RIGHT} (use): activate the block hit (lever / door / modded machine), or
 *       — when the aim is clear air — use the held item in that direction (throw an ender
 *       pearl, eat, draw a bow).</li>
 * </ul>
 * {@code aim} null = use the body's CURRENT facing (in-air use with no target, e.g. eating).
 * {@code holdTicks}: 0 = a single press; &gt;0 = hold that many ticks (modded crank / bow draw);
 * -1 = hold until the action self-completes or the task times out.
 */
public final class InteractAtTaskRecord extends TaskRecord {

    public static final String TOOL_NAME = "interact_at";

    public enum Button { LEFT, RIGHT }   // left = attack, right = use

    public final Button button;
    public final BlockPos aim;     // null → current facing (in-air use)
    public final int holdTicks;

    public InteractAtTaskRecord(String toolCallId, long deadlineGameTime,
                                Button button, BlockPos aim, int holdTicks) {
        super(TOOL_NAME, toolCallId, deadlineGameTime);
        this.button = button;
        this.aim = aim != null ? aim.immutable() : null;
        this.holdTicks = holdTicks;
    }

    @Override
    public String describe() {
        return TOOL_NAME + " " + (button == Button.LEFT ? "left" : "right")
                + (aim != null ? " @" + aim.getX() + "," + aim.getY() + "," + aim.getZ() : " (forward)")
                + (holdTicks != 0 ? " hold=" + holdTicks : "");
    }
}
