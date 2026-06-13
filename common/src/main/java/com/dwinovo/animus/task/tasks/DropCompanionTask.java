package com.dwinovo.animus.task.tasks;

import com.dwinovo.animus.entity.AnimusPlayer;
import com.dwinovo.animus.task.CompanionTask;
import com.dwinovo.animus.task.PlayerInv;
import com.dwinovo.animus.task.TaskResult;
import com.dwinovo.animus.task.TaskState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** {@code drop_items} on the player body — toss items forward. One-tick. */
public final class DropCompanionTask implements CompanionTask {

    private final AnimusPlayer player;
    private final DropItemsTaskRecord r;
    private int dropped;
    private String doneReason = "done";

    public DropCompanionTask(AnimusPlayer player, DropItemsTaskRecord record) {
        this.player = player;
        this.r = record;
    }

    @Override
    public void start() {
        dropped = 0;
        if (!(player.level() instanceof ServerLevel sl)) {
            doneReason = "not on a server level";
            r.setState(TaskState.FAILED);
            return;
        }
        Inventory inv = player.getInventory();
        int have = PlayerInv.count(inv, r.item);
        if (have <= 0) {
            doneReason = "no " + r.label + " in inventory to drop";
            r.setState(TaskState.FAILED);
            return;
        }
        dropped = Math.min(r.count, have);
        PlayerInv.remove(inv, r.item, dropped);

        Vec3 look = player.getLookAngle();
        int remaining = dropped;
        while (remaining > 0) {
            int lump = Math.min(remaining, new ItemStack(r.item).getMaxStackSize());
            remaining -= lump;
            ItemEntity drop = new ItemEntity(sl,
                    player.getX(), player.getEyeY() - 0.3, player.getZ(),
                    new ItemStack(r.item, lump), look.x * 0.3, 0.1, look.z * 0.3);
            drop.setPickUpDelay(40);
            sl.addFreshEntity(drop);
        }
        doneReason = "dropped " + dropped + "x " + r.label
                + (dropped < r.count ? " (only had " + dropped + ")" : "");
        r.setState(TaskState.SUCCESS);
    }

    @Override
    public TaskState tick() {
        return r.getState();
    }

    @Override
    public TaskResult buildResult(TaskState finalState) {
        Map<String, Object> data = new HashMap<>();
        data.put("item", r.label);
        data.put("dropped", dropped);
        data.put("remaining_in_inventory", PlayerInv.count(player.getInventory(), r.item));
        return switch (finalState) {
            case SUCCESS -> TaskResult.ok(doneReason, data);
            case CANCELLED -> TaskResult.cancelled("drop interrupted");
            case TIMEOUT -> TaskResult.timeout("drop timed out unexpectedly");
            default -> TaskResult.fail(doneReason, data);
        };
    }
}
