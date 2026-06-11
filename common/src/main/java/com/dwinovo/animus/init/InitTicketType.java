package com.dwinovo.animus.init;

import net.minecraft.server.level.TicketType;

/**
 * The mod's chunk ticket types. Constructed in common; each loader REGISTERS
 * them into {@code BuiltInRegistries.TICKET_TYPE} at init — the same thing
 * vanilla does for ENDER_PEARL/PORTAL/etc. Registration is what makes the
 * type visible to debug tooling and serializable by the ticket storage;
 * an unregistered instance works but is invisible and unsaveable.
 */
public final class InitTicketType {

    /** Registry path for {@link #TASK} (namespace is the mod id). */
    public static final String TASK_ID = "task";

    /**
     * Keeps an ENGAGED Animus loaded and ticking away from any player.
     * Flags mirror vanilla ENDER_PEARL: LOADING alone gives border chunks
     * whose entities never tick (hence SIMULATION), and a playerless
     * dimension stops ticking entities entirely after 300 empty ticks unless
     * a ticket carries KEEP_DIMENSION_ACTIVE. Deliberately NOT
     * FLAG_PERSIST: the 200-tick timeout makes every teardown path (task
     * done, death, crash, server stop) "just stop refreshing", and the
     * revival index re-loads pets after restarts — a persisted ticket with
     * no owner-liveness validation would orphan chunks instead.
     */
    public static final TicketType TASK = new TicketType(200L,
            TicketType.FLAG_LOADING
                    | TicketType.FLAG_SIMULATION
                    | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    private InitTicketType() {}
}
