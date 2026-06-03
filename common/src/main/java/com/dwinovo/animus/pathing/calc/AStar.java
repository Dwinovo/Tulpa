package com.dwinovo.animus.pathing.calc;

import net.minecraft.core.BlockPos;

/**
 * Configuration and factory for {@link AStarSearch}. Holds the per-search node
 * budget and hands out resumable, time-sliced searches.
 *
 * <h2>Time-sliced by default</h2>
 * Call {@link #newSearch} and {@code step()} it once per tick — the search
 * spreads its work across ticks so no single tick stalls, even with several
 * entities planning at once. {@link #compute} is a synchronous convenience that
 * simply steps a fresh search to completion in one call, for callers that don't
 * need slicing.
 */
public final class AStar {

    /** Default hard cap on node expansions per search (across all ticks). */
    public static final int DEFAULT_MAX_NODES = 10_000;

    /**
     * Default per-tick expansion budget. Picked so a typical near search
     * finishes in a single tick while a worst-case search (the full node cap)
     * spreads over a handful of ticks (~{@value #DEFAULT_MAX_NODES} /
     * {@value #DEFAULT_NODES_PER_TICK} ≈ 7 ticks) instead of hitching one.
     */
    public static final int DEFAULT_NODES_PER_TICK = 1_500;

    /** Hard cap on node expansions per search. */
    private final int maxNodes;

    public AStar(int maxNodes) {
        this.maxNodes = maxNodes;
    }

    public AStar() {
        this(DEFAULT_MAX_NODES);
    }

    /** Begin a resumable, time-sliced search. Step it once per tick. */
    public AStarSearch newSearch(NavContext ctx, BlockPos start, BlockPos goal) {
        return new AStarSearch(ctx, start, goal, maxNodes);
    }

    /**
     * Run a search to completion synchronously and return its path. Convenience
     * for non-time-sliced callers; prefer {@link #newSearch} on the tick path.
     */
    public Path compute(NavContext ctx, BlockPos start, BlockPos goal) {
        AStarSearch search = newSearch(ctx, start, goal);
        // One unbounded step drives the search straight to a terminal state.
        search.step(Integer.MAX_VALUE);
        return search.result();
    }
}
