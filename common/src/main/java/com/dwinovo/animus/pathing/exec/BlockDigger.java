package com.dwinovo.animus.pathing.exec;

import com.dwinovo.animus.entity.AnimusPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Progressive block breaking that replicates the VANILLA CLIENT mining loop
 * ({@code MultiPlayerGameMode.continueDestroyBlock}) for a server fake player —
 * because a fake player has no client to run it and the server's
 * {@code ServerPlayerGameMode} does NOT self-complete a survival break (it waits
 * for the client's {@code STOP_DESTROY_BLOCK} packet, which never arrives). So we
 * BE the client:
 * <ul>
 *   <li><b>creative</b> ({@code abilities.instabuild}) → break instantly;</li>
 *   <li><b>survival</b> → accumulate the block's real per-tick destroy fraction
 *       {@link BlockState#getDestroyProgress} (which folds in the held tool /
 *       enchants / haste / hardness / on-ground+in-water) until it reaches 1.0,
 *       broadcasting the crack overlay as it goes.</li>
 * </ul>
 * The actual break is the NATIVE {@code gameMode.destroyBlock} (drops, durability,
 * break events). This is exactly the timing a real survival player gets — and how
 * Baritone mines too: it just holds left-click and the vanilla client accumulates
 * {@code getDestroyProgress}; it never hand-rolls a tick count.
 *
 * <p>Shared by path-obstruction clearing ({@code PlayerPathExecutor}) and
 * auto-mine ({@code MineCompanionTask}).
 */
public final class BlockDigger {

    private final AnimusPlayer player;
    private BlockPos pos;
    private float progress;       // accumulated 0..1, like MultiPlayerGameMode.destroyProgress
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
     * target changed): face it, swing, accumulate destroy progress, break on time.
     *
     * @return {@code true} on the tick the block breaks.
     */
    public boolean dig(BlockPos target) {
        Level level = player.level();
        if (pos == null || !pos.equals(target)) {
            start(target);
        }
        InputDriver.halt(player);
        InputDriver.lookAt(player, Vec3.atCenterOf(pos));
        if (swingCd-- <= 0) {
            player.swing(InteractionHand.MAIN_HAND);
            swingCd = 5;                          // vanilla swings ~every 6 ticks while mining
        }

        // Creative: instant break (the vanilla client special-cases this — it does NOT
        // wait out getDestroyProgress in creative).
        if (player.getAbilities().instabuild) {
            return finish(level);
        }

        // Survival: accumulate the real per-tick destroy fraction.
        BlockState state = level.getBlockState(pos);
        progress += state.getDestroyProgress(player, level, pos);
        int stage = Math.min(9, (int) (progress * 10.0f));
        if (stage != lastStage) {                 // re-broadcast the crack only on stage change
            level.destroyBlockProgress(player.getId(), pos, stage);
            lastStage = stage;
        }
        if (progress >= 1.0f) {
            return finish(level);
        }
        return false;
    }

    /** Native break + clear our dig state. */
    private boolean finish(Level level) {
        BlockPos done = pos;
        cancel();                                 // clears the crack overlay, resets state
        player.gameMode.destroyBlock(done);       // native: drops / durability / break events
        return level.getBlockState(done).isAir();
    }

    private void start(BlockPos target) {
        cancel();
        pos = target.immutable();
        progress = 0.0f;
        swingCd = 0;
        lastStage = -1;
        // Hold the best tool BEFORE timing the dig — getDestroyProgress reads the held
        // item, and the pathing cost model prices every break with the best hotbar tool
        // (Baritone switchToBestToolFor).
        switchToBestTool(player.level().getBlockState(pos));
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
        progress = 0.0f;
        lastStage = -1;
    }
}
