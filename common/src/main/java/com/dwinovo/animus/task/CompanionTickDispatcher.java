package com.dwinovo.animus.task;

import com.dwinovo.animus.entity.AnimusPlayer;
import com.dwinovo.animus.network.payload.TaskResultPayload;
import com.dwinovo.animus.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-tick driver of companion tasks — the player-body replacement for the
 * Mob's {@code GoalSelector} + {@code customServerAiStep}. Each tick, for every
 * live {@link AnimusPlayer}: pull the head of its task queue, run the matching
 * {@link CompanionTask} to completion (deadline-bounded), and ship finished
 * results back to the owner as {@link TaskResultPayload}. Registered from both
 * loaders' end-of-tick hooks.
 */
public final class CompanionTickDispatcher {

    private record Running(CompanionTask task, TaskRecord record) {}

    private static final Map<UUID, Running> ACTIVE = new HashMap<>();

    private CompanionTickDispatcher() {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof AnimusPlayer ap) {
                tickOne(ap);
            }
        }
    }

    private static void tickOne(AnimusPlayer player) {
        UUID id = player.getUUID();
        Running running = ACTIVE.get(id);

        if (running == null) {
            TaskRecord rec = player.getTaskQueue().pollHead();
            if (rec != null) {
                rec.setState(TaskState.RUNNING);
                player.setActiveTask(rec);
                player.setDebugTask(rec.describe());
                player.pathTally().reset();
                CompanionTask task = CompanionTaskFactory.create(player, rec);
                running = new Running(task, rec);
                ACTIVE.put(id, running);
                task.start();   // may flip the record terminal immediately
            }
        } else if (running.record().getState() == TaskState.RUNNING) {
            if (player.level().getGameTime() >= running.record().getDeadlineGameTime()) {
                running.record().setState(TaskState.TIMEOUT);
            } else {
                running.record().setState(running.task().tick());
            }
        }

        // Finish on any terminal state (set by start(), tick(), deadline, or cancel).
        running = ACTIVE.get(id);
        if (running != null) {
            TaskState st = running.record().getState();
            if (st != TaskState.RUNNING && st != TaskState.PENDING) {
                running.record().setResult(running.task().buildResult(st));
                player.getTaskQueue().complete(running.record());
                player.setActiveTask(null);
                player.setDebugTask(null);
                ACTIVE.remove(id);
            }
        }

        // Drive the fake player's own server-side player tick AFTER the task set
        // its inputs this tick. A real ServerPlayer's movement physics
        // (travel against zza/xxa), food, air and pose are driven by the network
        // layer calling doTick(); our FakeConnection is a no-op, so without this
        // the body only ever turns (setYRot writes a field directly) and never
        // walks — zza has no consumer. This is Carpet's EntityPlayerMPFake.tick()
        // trick. ServerPlayer.tick() (run by the entity system) handles only
        // menus/sync, not movement, so calling doTick() once here adds the
        // missing travel pass without double-ticking.
        driveVanillaPlayerTick(player);

        drainResults(player);
    }

    /**
     * Run one vanilla player tick on the fake body. Every 10 ticks resync the
     * connection's tracked position and let chunk loading follow the body so it
     * doesn't walk out of its loaded area — mirrors Carpet's fake-player tick.
     */
    private static void driveVanillaPlayerTick(AnimusPlayer player) {
        if (player.level() instanceof ServerLevel sl) {
            if (sl.getGameTime() % 10 == 0) {
                player.connection.resetPosition();
                sl.getChunkSource().move(player);
            }
        }
        try {
            player.doTick();
        } catch (Exception ignored) {
            // mirrors Carpet — fake-connection internals can NPE on edge cases
        }
    }

    private static void drainResults(AnimusPlayer player) {
        List<TaskRecord> completed = player.getTaskQueue().drainCompleted();
        if (completed.isEmpty()) return;
        ServerPlayer owner = player.resolveOwnerPlayer();
        if (owner == null) return;   // owner offline — drop (the loop will re-ask)
        for (TaskRecord rec : completed) {
            TaskResult result = rec.getResult();
            String json = result == null
                    ? "{\"success\":false,\"message\":\"no result produced\"}"
                    : result.toJson();
            Services.NETWORK.sendToPlayer(owner,
                    new TaskResultPayload(player.getUUID(), rec.getToolCallId(), json));
        }
    }
}
