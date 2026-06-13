package com.dwinovo.animus.task.tasks;

import com.dwinovo.animus.entity.AnimusPlayer;
import com.dwinovo.animus.pathing.exec.InputDriver;
import com.dwinovo.animus.task.CompanionTask;
import com.dwinovo.animus.task.TaskResult;
import com.dwinovo.animus.task.TaskState;

import java.util.Map;

/**
 * {@code wait} on the player body: idle in place for the requested duration
 * (game-time based, freeze/tick-rate aware). Player-body twin of WaitTaskGoal.
 */
public final class WaitCompanionTask implements CompanionTask {

    private final AnimusPlayer player;
    private final WaitTaskRecord r;
    private long wakeAtGameTime;

    public WaitCompanionTask(AnimusPlayer player, WaitTaskRecord record) {
        this.player = player;
        this.r = record;
    }

    @Override
    public void start() {
        this.wakeAtGameTime = player.level().getGameTime() + r.seconds * 20L;
        InputDriver.halt(player);
    }

    @Override
    public TaskState tick() {
        InputDriver.halt(player);   // hold still while idling
        return player.level().getGameTime() >= wakeAtGameTime ? TaskState.SUCCESS : TaskState.RUNNING;
    }

    @Override
    public TaskResult buildResult(TaskState finalState) {
        Map<String, Object> data = Map.of("seconds", r.seconds);
        String label = r.seconds + "s" + (r.reason.isEmpty() ? "" : " (" + r.reason + ")");
        return switch (finalState) {
            case SUCCESS -> TaskResult.ok("waited " + label, data);
            case CANCELLED -> TaskResult.cancelled("wait interrupted before " + label + " elapsed");
            case TIMEOUT -> TaskResult.timeout("wait timed out unexpectedly");
            default -> TaskResult.fail("unexpected state: " + finalState);
        };
    }
}
