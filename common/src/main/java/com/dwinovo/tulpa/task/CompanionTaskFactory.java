package com.dwinovo.tulpa.task;

import com.dwinovo.tulpa.entity.TulpaPlayer;
import com.dwinovo.tulpa.task.tasks.BreakBlockCompanionTask;
import com.dwinovo.tulpa.task.tasks.BreakBlockTaskRecord;
import com.dwinovo.tulpa.task.tasks.DropCompanionTask;
import com.dwinovo.tulpa.task.tasks.DropItemsTaskRecord;
import com.dwinovo.tulpa.task.tasks.EatCompanionTask;
import com.dwinovo.tulpa.task.tasks.EatItemTaskRecord;
import com.dwinovo.tulpa.task.tasks.EquipCompanionTask;
import com.dwinovo.tulpa.task.tasks.HuntCompanionTask;
import com.dwinovo.tulpa.task.tasks.HuntTaskRecord;
import com.dwinovo.tulpa.task.tasks.ShootCompanionTask;
import com.dwinovo.tulpa.task.tasks.ShootTaskRecord;
import com.dwinovo.tulpa.task.tasks.EquipTaskRecord;
import com.dwinovo.tulpa.task.tasks.MineBlockTaskRecord;
import com.dwinovo.tulpa.task.tasks.MineCompanionTask;
import com.dwinovo.tulpa.task.tasks.MoveToCompanionTask;
import com.dwinovo.tulpa.task.tasks.MoveToTaskRecord;
import com.dwinovo.tulpa.task.tasks.PlaceBlockCompanionTask;
import com.dwinovo.tulpa.task.tasks.PlaceBlockTaskRecord;
import com.dwinovo.tulpa.task.tasks.WaitCompanionTask;
import com.dwinovo.tulpa.task.tasks.WaitTaskRecord;

/**
 * Maps a queued {@link TaskRecord} to the {@link CompanionTask} that runs it on
 * the player body. Phase 0 wires {@code move_to} and {@code auto_mine}; every
 * other tool resolves to {@link UnsupportedCompanionTask} until its executor is
 * ported off the old Mob {@code LlmTaskGoal}.
 */
public final class CompanionTaskFactory {

    private CompanionTaskFactory() {}

    public static CompanionTask create(TulpaPlayer player, TaskRecord record) {
        if (record instanceof MoveToTaskRecord r) return new MoveToCompanionTask(player, r);
        if (record instanceof MineBlockTaskRecord r) return new MineCompanionTask(player, r);
        if (record instanceof EquipTaskRecord r) return new EquipCompanionTask(player, r);
        if (record instanceof WaitTaskRecord r) return new WaitCompanionTask(player, r);
        if (record instanceof DropItemsTaskRecord r) return new DropCompanionTask(player, r);
        if (record instanceof BreakBlockTaskRecord r) return new BreakBlockCompanionTask(player, r);
        if (record instanceof EatItemTaskRecord r) return new EatCompanionTask(player, r);
        if (record instanceof HuntTaskRecord r) return new HuntCompanionTask(player, r);
        if (record instanceof ShootTaskRecord r) return new ShootCompanionTask(player, r);
        if (record instanceof com.dwinovo.tulpa.task.tasks.CollectItemsTaskRecord r)
            return new com.dwinovo.tulpa.task.tasks.CollectItemsTaskGoal(player, r);
        if (record instanceof PlaceBlockTaskRecord r) return new PlaceBlockCompanionTask(player, r);
        if (record instanceof com.dwinovo.tulpa.task.tasks.InteractAtTaskRecord r)
            return new com.dwinovo.tulpa.task.tasks.InteractAtCompanionTask(player, r);
        if (record instanceof com.dwinovo.tulpa.task.tasks.InteractEntityTaskRecord r)
            return new com.dwinovo.tulpa.task.tasks.InteractEntityCompanionTask(player, r);
        if (record instanceof com.dwinovo.tulpa.task.tasks.LocateStructureTaskRecord r)
            return new com.dwinovo.tulpa.task.tasks.LocateStructureTaskGoal(player, r);
        if (record instanceof com.dwinovo.tulpa.task.tasks.LocateBiomeTaskRecord r)
            return new com.dwinovo.tulpa.task.tasks.LocateBiomeTaskGoal(player, r);
        return new UnsupportedCompanionTask(record);
    }
}
