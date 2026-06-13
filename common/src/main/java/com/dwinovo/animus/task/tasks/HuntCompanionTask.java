package com.dwinovo.animus.task.tasks;

import com.dwinovo.animus.entity.AnimusPlayer;
import com.dwinovo.animus.pathing.exec.InputDriver;
import com.dwinovo.animus.pathing.exec.PlayerNav;
import com.dwinovo.animus.task.CompanionTask;
import com.dwinovo.animus.task.TaskResult;
import com.dwinovo.animus.task.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code hunt} on the player body: find / chase / fight N mobs. The combat twin
 * of {@code HuntTaskGoal}, but melee is the player's NATIVE attack
 * ({@code player.attack} with real cooldown / weapon modifiers / sweep / crit)
 * instead of the Mob's MeleeEngine — and the chase is {@link PlayerNav}.
 */
public final class HuntCompanionTask implements CompanionTask {

    private enum Phase { SCAN, ENGAGE }

    private static final int INITIAL_RADIUS = 24;
    private static final int RADIUS_STEP = 16;
    private static final double CHASE_SPEED = 1.2;
    /** Melee strike range² — vanilla player entity-interaction reach ≈ 3 blocks. */
    private static final double ATTACK_REACH_SQR = 9.0;

    private final AnimusPlayer player;
    private final HuntTaskRecord r;
    private final Set<Integer> skipped = new HashSet<>();

    private Phase phase = Phase.SCAN;
    private int currentRadius;
    private LivingEntity target;
    private PlayerNav nav;
    private String doneReason = "done";

    public HuntCompanionTask(AnimusPlayer player, HuntTaskRecord record) {
        this.player = player;
        this.r = record;
    }

    @Override
    public void start() {
        currentRadius = Math.min(INITIAL_RADIUS, r.maxRadius);
        phase = Phase.SCAN;
    }

    @Override
    public TaskState tick() {
        if (player.isDeadOrDying()) {
            r.setState(TaskState.CANCELLED);
            return r.getState();
        }
        switch (phase) {
            case SCAN -> tickScan();
            case ENGAGE -> tickEngage();
        }
        return r.getState();
    }

    private void tickScan() {
        if (r.getKilled() >= r.count) {
            doneReason = "hunted all requested";
            r.setState(TaskState.SUCCESS);
            return;
        }
        LivingEntity best = nearestTarget();
        if (best == null) {
            if (currentRadius < r.maxRadius) {
                currentRadius = Math.min(currentRadius + RADIUS_STEP, r.maxRadius);
                return;
            }
            doneReason = r.getKilled() > 0
                    ? "only killed " + r.getKilled() + "/" + r.count + " within " + r.maxRadius + " blocks"
                    : "no " + r.label + " found within " + r.maxRadius + " blocks";
            r.setState(r.getKilled() > 0 ? TaskState.SUCCESS : TaskState.FAILED);
            return;
        }
        target = best;
        nav = new PlayerNav(player, this::targetCell, CHASE_SPEED, this::inReach);
        phase = Phase.ENGAGE;
    }

    private void tickEngage() {
        if (target == null || target.isRemoved()) {
            stopNav();
            phase = Phase.SCAN;
            return;
        }
        if (target.isDeadOrDying()) {
            r.incrementKilled();
            player.setDebugTask(r.describe());
            target = null;
            stopNav();
            phase = Phase.SCAN;
            return;
        }
        switch (nav.tick()) {
            case RUNNING -> { /* closing distance */ }
            case ARRIVED -> swing();
            case FAILED -> {
                skipped.add(target.getId());
                target = null;
                stopNav();
                phase = Phase.SCAN;
            }
        }
    }

    /** Native melee swing when in reach and the attack cooldown has recovered. */
    private void swing() {
        if (target == null) return;
        InputDriver.lookAt(player, target.getEyePosition());
        if (player.getAttackStrengthScale(0.0f) >= 0.95f) {
            player.attack(target);        // real damage / cooldown / sweep / knockback
            player.resetAttackStrengthTicker();
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    private BlockPos targetCell() {
        return (target != null && !target.isRemoved()) ? target.blockPosition() : null;
    }

    private boolean inReach() {
        return target != null
                && player.distanceToSqr(Vec3.atCenterOf(target.blockPosition())) <= ATTACK_REACH_SQR;
    }

    private LivingEntity nearestTarget() {
        AABB box = player.getBoundingBox().inflate(currentRadius);
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity e : player.level().getEntities(player, box)) {
            if (e == player || e.isRemoved()) continue;
            if (!(e instanceof LivingEntity le) || le.isDeadOrDying()) continue;
            if (!r.targets.contains(e.getType())) continue;
            if (skipped.contains(e.getId())) continue;
            double d = player.distanceToSqr(e);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = le;
            }
        }
        return best;
    }

    private void stopNav() {
        if (nav != null) {
            nav.stop();
            nav = null;
        }
    }

    @Override
    public TaskResult buildResult(TaskState finalState) {
        stopNav();
        Map<String, Object> data = new HashMap<>();
        data.put("target", r.label);
        data.put("requested", r.count);
        data.put("killed", r.getKilled());
        return switch (finalState) {
            case SUCCESS -> TaskResult.ok(
                    "killed " + r.getKilled() + "/" + r.count + " " + r.label + " (" + doneReason + ")", data);
            case TIMEOUT -> new TaskResult(false,
                    "timed out after killing " + r.getKilled() + "/" + r.count + " " + r.label, true, false, data);
            case CANCELLED -> new TaskResult(false,
                    "interrupted after killing " + r.getKilled() + "/" + r.count + " " + r.label, false, true, data);
            case FAILED -> TaskResult.fail(doneReason, data);
            default -> TaskResult.fail("unexpected state: " + finalState, data);
        };
    }
}
