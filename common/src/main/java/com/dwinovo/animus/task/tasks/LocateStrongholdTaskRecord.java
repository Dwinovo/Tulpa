package com.dwinovo.animus.task.tasks;

import com.dwinovo.animus.task.TaskRecord;

/**
 * Typed task descriptor for {@code locate_stronghold}: find the nearest
 * stronghold (which holds the End portal). No parameters — the search origin is
 * the entity's own position and the radius is fixed (vanilla eye-of-ender uses
 * 100 chunks). The goal ({@link LocateStrongholdTaskGoal}) runs a single
 * server-side {@code findNearestMapStructure} — the same lookup an eye of ender
 * makes — and returns the coordinates / direction / distance.
 */
public final class LocateStrongholdTaskRecord extends TaskRecord {

    public static final String TOOL_NAME = "locate_stronghold";

    public LocateStrongholdTaskRecord(String toolCallId, long deadlineGameTime) {
        super(TOOL_NAME, toolCallId, deadlineGameTime);
    }

    @Override
    public String describe() {
        return TOOL_NAME;
    }
}
