package com.dwinovo.animus.pathing.exec;

import com.dwinovo.animus.entity.AnimusPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Progressive, tick-by-tick block breaking — a real player's dig, not an instant
 * pop. Shared by both path-obstruction clearing ({@code PlayerPathExecutor}) and
 * auto-mine ({@code MineCompanionTask}) so breaking always reads the same way and
 * stays aligned with Baritone, which holds {@code CLICK_LEFT} over the block's
 * real hardness time: face the block, swing the arm, push the crack-overlay
 * stages, and break it once enough ticks have elapsed.
 *
 * <p>Duration is the true vanilla dig time of the CURRENTLY-HELD tool
 * ({@link net.minecraft.world.level.block.state.BlockState#getDestroyProgress})
 * — callers that want a specific tool (auto-mine switches to the best one) do so
 * before the first {@link #dig} tick of a block.
 */
public final class BlockDigger {

    private final AnimusPlayer player;
    private BlockPos pos;
    private int ticks;
    private int total;
    private int swingCd;
    private int lastStage = -1;

    public BlockDigger(AnimusPlayer player) {
        this.player = player;
    }

    /** The block currently being dug, or {@code null} when idle. */
    public BlockPos current() {
        return pos;
    }

    /**
     * Advance the dig of {@code target} by one tick (restarting cleanly if the
     * target changed). Halts, faces the block, swings, pushes crack stages, and
     * breaks it on time.
     *
     * @return {@code true} on the tick the block breaks.
     */
    public boolean dig(BlockPos target) {
        Level level = player.level();
        if (pos == null || !pos.equals(target)) start(target);
        InputDriver.halt(player);
        InputDriver.lookAt(player, Vec3.atCenterOf(pos));
        if (swingCd-- <= 0) {
            player.swing(InteractionHand.MAIN_HAND);
            swingCd = 5;                          // vanilla swings ~every 6 ticks while mining
        }
        ticks++;
        int stage = Math.min(9, (int) ((ticks / (float) total) * 10.0f));
        if (stage != lastStage) {                 // a real player re-broadcasts only on stage change
            level.destroyBlockProgress(player.getId(), pos, stage);
            lastStage = stage;
        }
        if (ticks >= total) {
            BlockPos done = pos;
            player.gameMode.destroyBlock(done);
            cancel();
            return level.getBlockState(done).isAir();
        }
        return false;
    }

    private void start(BlockPos target) {
        cancel();
        pos = target.immutable();
        ticks = 0;
        swingCd = 0;
        lastStage = -1;
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        // Hold the best tool BEFORE timing the dig, so duration matches the pathing
        // cost model (NavContext costs every break with the best hotbar tool —
        // Baritone switchToBestToolFor). getDestroyProgress is then the per-tick
        // fraction with that tool (enchant/haste/water/airborne folded in), so the
        // dig takes ceil(1 / fraction) ticks.
        switchToBestTool(state);
        float perTick = state.getDestroyProgress(player, level, pos);
        total = perTick <= 0.0f ? 1 : Math.max(1, (int) Math.ceil(1.0f / perTick));
    }

    /** Select the hotbar slot whose item mines {@code state} fastest
     *  (Baritone ToolSet.getBestSlot / switchToBestToolFor). */
    private void switchToBestTool(BlockState state) {
        Inventory inv = player.getInventory();
        int best = inv.getSelectedSlot();
        float bestSpeed = inv.getItem(best).getDestroySpeed(state);
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            float s = inv.getItem(i).getDestroySpeed(state);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = i;
            }
        }
        inv.setSelectedSlot(best);
    }

    /** Abandon any in-progress dig and clear its crack overlay. */
    public void cancel() {
        if (pos != null) {
            player.level().destroyBlockProgress(player.getId(), pos, -1);
            pos = null;
        }
    }
}
