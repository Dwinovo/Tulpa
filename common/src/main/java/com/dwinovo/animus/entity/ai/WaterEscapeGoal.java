package com.dwinovo.animus.entity.ai;

import com.dwinovo.animus.entity.AnimusEntity;
import com.dwinovo.animus.pathing.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Reflex: when the Animus ends up swimming in deep water (knockback, a failed
 * parkour, a breached aquifer), steer it to the nearest standable shore. Pairs
 * with the vanilla {@code FloatGoal}, which keeps it bobbing at the surface so
 * it can breathe while this goal does the getting-out.
 *
 * <h2>Why a reflex and not the LLM / the custom pathfinder</h2>
 * Same justification as the auto-eat reflex: a drowning bar is ~15 seconds and
 * an LLM round-trip plus task dispatch is far too slow. The custom
 * terrain-modifying pathfinder treats water as impassable by design (it
 * doesn't swim), so it can't plan its own way out — but the VANILLA ground
 * navigation happily paths through water at a malus, which is exactly how
 * vanilla wolves swim out of ponds. We just borrow that.
 *
 * <p>While this runs, the task executors yield (they check
 * {@link AnimusEntity#isDeepInWater()} and idle), then re-plan from dry land —
 * the task survives the swim instead of failing mid-lake.
 */
public final class WaterEscapeGoal extends Goal {

    private static final int SEARCH_HORIZONTAL = 16;
    private static final int SEARCH_VERTICAL = 6;
    /** Re-pick the shore target at most once a second. */
    private static final int RETARGET_INTERVAL_TICKS = 20;
    private static final double SWIM_SPEED = 1.2;

    private final AnimusEntity entity;
    private int retargetCooldown;

    public WaterEscapeGoal(AnimusEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return entity.isDeepInWater();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.isDeepInWater();
    }

    @Override
    public void start() {
        retargetCooldown = 0;
    }

    @Override
    public void tick() {
        if (--retargetCooldown > 0 && entity.getNavigation().isInProgress()) {
            return;
        }
        retargetCooldown = RETARGET_INTERVAL_TICKS;
        BlockPos shore = BlockPos.findClosestMatch(
                        entity.blockPosition(), SEARCH_HORIZONTAL, SEARCH_VERTICAL,
                        pos -> BlockHelper.isStandable(entity.level(), pos))
                .orElse(null);
        if (shore != null) {
            // Vanilla ground navigation swims (water has a finite path malus);
            // FloatGoal keeps the head above water while it does.
            entity.getNavigation().moveTo(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5,
                    SWIM_SPEED);
        }
        // No shore within range: keep floating (FloatGoal) and retry — drifting
        // beats diving.
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
