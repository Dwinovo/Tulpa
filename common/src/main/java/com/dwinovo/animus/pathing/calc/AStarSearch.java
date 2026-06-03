package com.dwinovo.animus.pathing.calc;

import com.dwinovo.animus.pathing.movement.Movement;
import com.dwinovo.animus.pathing.movement.Moves;
import com.dwinovo.animus.pathing.util.ActionCosts;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A resumable, <em>time-sliced</em> A* search over the {@link Moves} primitive
 * graph — the mineflayer-pathfinder model adapted to a single-threaded server.
 *
 * <p>Instead of running to completion in one call (which can hitch the server
 * tick when several entities replan at once), the open/closed sets live on this
 * object and {@link #step(int)} expands at most {@code budget} nodes per call.
 * The owning goal calls {@code step} once per tick until the state turns
 * {@link State#DONE}, then reads {@link #result()}. The search stays entirely on
 * the tick thread, so it reads the live world directly with no snapshot and no
 * cross-thread hazards.
 *
 * <h2>Cost &amp; heuristic</h2>
 * Edge costs come from the movement primitives (ticks; walk + mine + place).
 * The heuristic is straight-line horizontal distance × {@code WALK} plus a small
 * upward term — a lower bound on remaining cost, keeping the search close to
 * admissible so paths are near-optimal without over-exploring.
 *
 * <h2>Bounded</h2>
 * Capped at {@code maxNodes} expansions <em>across all ticks</em>. If the goal
 * isn't reached within budget, the closest node by heuristic is returned as a
 * {@link Path#partial} route; the executor walks it and replans from there.
 */
public final class AStarSearch {

    /** Terminal vs. still-running. {@link #result()} is valid only at {@code DONE}. */
    public enum State { COMPUTING, DONE }

    /** Internal open/closed bookkeeping node, keyed by block position. */
    private static final class Node {
        final BlockPos pos;
        double g;          // cost from start
        double f;          // g + heuristic
        Node parent;
        Movement via;      // movement that led parent -> this
        boolean closed;

        Node(BlockPos pos) { this.pos = pos; }
    }

    private final NavContext ctx;
    private final BlockPos start;
    private final BlockPos goal;
    private final int maxNodes;

    private final Map<BlockPos, Node> nodes = new HashMap<>();
    private final PriorityQueue<Node> open =
            new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

    private Node best;                 // closest-to-goal seen, for partial fallback
    private double bestH;
    private int expansions = 0;

    private State state = State.COMPUTING;
    private Path result;

    AStarSearch(NavContext ctx, BlockPos start, BlockPos goal, int maxNodes) {
        this.ctx = ctx;
        this.start = start.immutable();
        this.goal = goal.immutable();
        this.maxNodes = maxNodes;

        Node startNode = new Node(this.start);
        startNode.g = 0;
        startNode.f = heuristic(this.start, this.goal);
        nodes.put(this.start, startNode);
        open.add(startNode);
        best = startNode;
        bestH = startNode.f;
    }

    /** Current search state. */
    public State state() {
        return state;
    }

    /**
     * The computed route. {@code null} until the search reaches
     * {@link State#DONE}; thereafter a complete path (goal reached), a
     * {@link Path#partial} best-effort path, or an empty path (no progress).
     */
    public Path result() {
        return result;
    }

    /**
     * Expand at most {@code budget} nodes this tick. Returns the resulting
     * state: {@link State#COMPUTING} if more work remains (call again next
     * tick), or {@link State#DONE} once a path — complete or partial — is
     * available from {@link #result()}.
     */
    public State step(int budget) {
        if (state == State.DONE) {
            return state;
        }

        int budgetLeft = budget;
        while (!open.isEmpty() && expansions < maxNodes && budgetLeft-- > 0) {
            Node current = open.poll();
            if (current.closed) continue;
            current.closed = true;
            expansions++;

            if (current.pos.equals(goal)) {
                result = reconstruct(start, goal, current, false);
                state = State.DONE;
                return state;
            }

            double h = heuristic(current.pos, goal);
            if (h < bestH) {
                bestH = h;
                best = current;
            }

            for (Movement mv : Moves.generate(ctx, current.pos)) {
                if (mv.cost >= ActionCosts.COST_INF) continue;
                BlockPos np = mv.dest;
                double tentativeG = current.g + mv.cost;

                Node neighbor = nodes.computeIfAbsent(np, Node::new);
                if (neighbor.closed && tentativeG >= neighbor.g) continue;
                if (neighbor.via != null && tentativeG >= neighbor.g) continue;

                neighbor.parent = current;
                neighbor.via = mv;
                neighbor.g = tentativeG;
                neighbor.f = tentativeG + heuristic(np, goal);
                neighbor.closed = false;
                open.add(neighbor);
            }
        }

        // Terminated (open exhausted or node cap hit) vs. just out of this
        // tick's budget. Only the former produces a result; otherwise resume.
        if (open.isEmpty() || expansions >= maxNodes) {
            result = (best.via == null)
                    ? new Path(start, start, Collections.emptyList(), true)
                    : reconstruct(start, best.pos, best, true);
            state = State.DONE;
        }
        return state;
    }

    private static Path reconstruct(BlockPos start, BlockPos end, Node end_, boolean partial) {
        ArrayDeque<Movement> stack = new ArrayDeque<>();
        Node cur = end_;
        while (cur != null && cur.via != null) {
            stack.push(cur.via);
            cur = cur.parent;
        }
        List<Movement> moves = new ArrayList<>(stack);
        return new Path(start, end, moves, partial);
    }

    /**
     * Admissible-ish heuristic: horizontal Euclidean distance × walk cost, plus
     * upward delta × jump cost (downward is free — falling is cheap).
     */
    private static double heuristic(BlockPos from, BlockPos goal) {
        double dx = goal.getX() - from.getX();
        double dz = goal.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int dy = goal.getY() - from.getY();
        double vertical = dy > 0 ? dy * ActionCosts.JUMP_ONE_BLOCK : 0;
        return horizontal * ActionCosts.WALK_ONE_BLOCK + vertical;
    }
}
