package com.dwinovo.animus.client.debug;

/**
 * Client-side, process-global toggle for the {@code /animus debug} head
 * overlay. When {@link #enabled} is true the {@link com.dwinovo.animus.render
 * entity renderer} draws each Animus's current task above its head.
 *
 * <p>Deliberately a plain static flag: the overlay is a purely visual,
 * single-player-view debugging aid with no gameplay effect, so it needs no
 * per-entity state or server round-trip — the task text itself is already
 * synced via {@link com.dwinovo.animus.entity.AnimusEntity}'s entity data.
 * Accessed only from the client render thread.
 */
public final class AnimusDebugState {

    private static boolean enabled = false;

    private AnimusDebugState() {}

    /** Whether the debug head overlay is currently showing. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Flip the overlay on/off; returns the new state (for command feedback). */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }
}
