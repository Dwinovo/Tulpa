package com.dwinovo.animus.pathing.movement;

import com.dwinovo.animus.pathing.calc.NavContext;
import com.dwinovo.animus.pathing.util.ActionCosts;
import com.dwinovo.animus.pathing.util.BlockHelper;
import com.dwinovo.animus.pathing.util.PathSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the candidate {@link Movement}s reachable from a feet-position,
 * each self-costed against the {@link NavContext} snapshot. Infeasible
 * movements (no scaffolding to bridge, unbreakable obstruction, hazard,
 * fall too deep) are simply not emitted.
 *
 * <h2>Primitive set (mineflayer-parity)</h2>
 * <ul>
 *   <li><b>Traverse</b> — same-Y step; bridges gaps by placing a floor block
 *       and mines head/feet obstructions.</li>
 *   <li><b>Ascend</b> — step up one block; mines head-room and may place the
 *       step block to climb a ledge that isn't there.</li>
 *   <li><b>Descend / Fall</b> — step out and drop to the first safe floor
 *       within {@link NavContext#maxFallHeight}.</li>
 *   <li><b>Diagonal</b> — same-Y step to a corner cell; emitted only when both
 *       orthogonal corner cells are open (no corner-clipping), mirrors
 *       Baritone {@code MovementDiagonal} / mineflayer diagonal moves.</li>
 *   <li><b>Pillar</b> — jump straight up one block, placing a scaffolding block
 *       beneath as you rise (mineflayer "move up" / jump-place). Needs a
 *       scaffold block and head-room.</li>
 *   <li><b>DigDown</b> — mine the floor block underfoot and drop one (mineflayer
 *       "dig down"). Only when a solid floor exists one block lower to land on.</li>
 *   <li><b>Parkour</b> — a committed running jump across a 2-4 block gap at the
 *       same level (Baritone {@code MovementParkour}); the executor supplies
 *       the impulse, the planner only asserts the corridor is jumpable.</li>
 * </ul>
 */
public final class Moves {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /** The four diagonal corners as orthogonal direction pairs. */
    private static final Direction[][] DIAGONALS = {
            {Direction.NORTH, Direction.EAST},
            {Direction.NORTH, Direction.WEST},
            {Direction.SOUTH, Direction.EAST},
            {Direction.SOUTH, Direction.WEST},
    };

    /** Maximum parkour gap (blocks) — 4 needs sprint physics, the vanilla cap. */
    private static final int MAX_PARKOUR = 4;

    private Moves() {}

    /** All feasible movements out of {@code from}. */
    public static List<Movement> generate(NavContext ctx, BlockPos from) {
        List<Movement> out = new ArrayList<>(18);
        for (Direction dir : HORIZONTAL) {
            Movement t = traverse(ctx, from, dir);
            if (t != null) out.add(t);
            Movement a = ascend(ctx, from, dir);
            if (a != null) out.add(a);
            Movement d = descend(ctx, from, dir);
            if (d != null) out.add(d);
            Movement p = parkour(ctx, from, dir);
            if (p != null) out.add(p);
        }
        for (Direction[] pair : DIAGONALS) {
            Movement g = diagonal(ctx, from, pair[0], pair[1]);
            if (g != null) out.add(g);
        }
        Movement up = pillar(ctx, from);
        if (up != null) out.add(up);
        Movement down = digDown(ctx, from);
        if (down != null) out.add(down);
        swims(ctx, from, out);
        return out;
    }

    // ---- Swim: water as first-class (expensive) terrain ----

    /**
     * THE SURFACE IS THE ONLY SWIM PLANE — encoded here, in the one place
     * movement edges are born, not policed per-tool. Emitted only while the
     * FROM cell is water:
     * <ul>
     *   <li>lateral strokes exist ONLY between SURFACE cells (feet in the top
     *       water layer, head in air) — the graph contains no underwater
     *       corridors, so nothing can ever route below the surface;</li>
     *   <li>vertical strokes go UP only — the escape ladder for a body
     *       knocked into depth. There is deliberately no downward stroke:
     *       diving is out of scope (25× mining penalties, buoyancy
     *       management; draining is the correct play and the tools teach
     *       it at the intent layer).</li>
     * </ul>
     * All strokes cost {@link ActionCosts#SWIM_ONE_BLOCK} (~2× walk), so land
     * routes dominate the moment the search reaches shore. The shore EXIT
     * needs no special edge: TRAVERSE/ASCEND generate from a water cell like
     * any other, so "swim to the bank, step out, walk on" is one plan.
     */
    private static void swims(NavContext ctx, BlockPos from, List<Movement> out) {
        BlockGetter level = ctx.view;
        if (!BlockHelper.isWater(level, from)) return;
        // Lateral: surface cells only (head out of the water on both ends).
        if (isSurfaceCell(level, from)) {
            for (Direction dir : HORIZONTAL) {
                BlockPos dest = from.relative(dir);
                if (!isSurfaceCell(level, dest)) continue;
                if (BlockHelper.isHazard(level, dest) || BlockHelper.isHazard(level, dest.above())) continue;
                out.add(new Movement(Movement.Kind.SWIM, from, dest,
                        ActionCosts.SWIM_ONE_BLOCK, List.of(), null));
            }
        }
        // Up toward the surface (water→water only; the final pop into the
        // air-headed surface cell is just the lateral/exit moves' domain).
        BlockPos up = from.above();
        if (BlockHelper.isWater(level, up)
                && BlockHelper.canOccupyInWater(level, up.above())
                && !BlockHelper.isHazard(level, up)) {
            out.add(new Movement(Movement.Kind.SWIM, from, up,
                    ActionCosts.SWIM_ONE_BLOCK, List.of(), null));
        }
    }

    /** A swimmable SURFACE cell: water feet, breathable (non-water) head. */
    private static boolean isSurfaceCell(BlockGetter level, BlockPos feet) {
        return BlockHelper.isWater(level, feet)
                && BlockHelper.canWalkThrough(level, feet.above());
    }

    // ---- Traverse: same-Y step, bridge gaps, dig obstructions ----

    private static Movement traverse(NavContext ctx, BlockPos from, Direction dir) {
        BlockGetter level = ctx.view;
        BlockPos dest = from.relative(dir);
        BlockPos head = dest.above();

        List<BlockPos> toBreak = new ArrayList<>(2);

        // Clear the two body cells at the destination.
        double feetBreak = clearCost(ctx, dest, toBreak);
        if (feetBreak >= ActionCosts.COST_INF) return null;
        double headBreak = clearCost(ctx, head, toBreak, true);   // top cell: fold falling stack
        if (headBreak >= ActionCosts.COST_INF) return null;

        if (BlockHelper.isHazard(level, dest) || BlockHelper.isHazard(level, head)) return null;

        BlockPos floor = dest.below();
        if (BlockHelper.canWalkOn(level, floor)) {
            // WALK branch: base WALK + half soul-sand penalty per soul-sand floor touched
            // (Baritone adds destOn and srcDown separately). Sprint when nothing to break.
            double walk = ActionCosts.WALK_ONE_BLOCK;
            double soulSandHalf =
                    (ActionCosts.WALK_ONE_OVER_SOUL_SAND - ActionCosts.WALK_ONE_BLOCK) / 2.0;
            if (level.getBlockState(floor).is(Blocks.SOUL_SAND)) walk += soulSandHalf;
            if (level.getBlockState(from.below()).is(Blocks.SOUL_SAND)) walk += soulSandHalf;
            double cost = (feetBreak == 0.0 && headBreak == 0.0 && ctx.canSprint)
                    ? walk * ActionCosts.SPRINT_MULTIPLIER
                    : walk + feetBreak + headBreak;
            return new Movement(Movement.Kind.TRAVERSE, from, dest, cost, toBreak, null);
        }

        // BRIDGE branch (Baritone MovementTraverse): the dest floor is missing, so place
        // one. Needs a face to place against — a side face lets us walk-place; if only
        // the block we're standing on backs it, we must sneak-backplace (× SNEAK/WALK);
        // with no support at all (or off a ladder/vine / soul sand) it's impossible.
        // Baritone uses a plain WALK here (no soul-sand term in the bridge branch).
        if (isLadderOrVine(level.getBlockState(from.below()))) return null;
        double placeCost = ctx.costOfPlacing(floor);   // INF if non-replaceable / no scaffold / hazard
        if (placeCost >= ActionCosts.COST_INF) return null;
        double walkMult = bridgeSupport(ctx, from, floor);
        if (walkMult >= ActionCosts.COST_INF) return null;
        double cost = ActionCosts.WALK_ONE_BLOCK * walkMult + feetBreak + headBreak + placeCost;
        return new Movement(Movement.Kind.TRAVERSE, from, dest, cost, toBreak, floor);
    }

    /** The 5 faces of the dest floor a bridge can place against (Baritone's
     *  HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP). */
    private static final Direction[] SUPPORT_DIRS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

    /**
     * Baritone MovementTraverse bridge support scan: walk-cost MULTIPLIER for placing
     * the floor block. A side face (any of the 5 except the block we stand on) that we
     * can place against → 1.0 (normal walk-place). Otherwise we must backplace against
     * the block under our feet → {@code SNEAK/WALK} (≈3.3, a slow sneak place), vetoed
     * on soul sand (can't sneak-backplace off it). No placeable face → {@code COST_INF}.
     */
    private static double bridgeSupport(NavContext ctx, BlockPos from, BlockPos floor) {
        BlockGetter level = ctx.view;
        BlockPos sourceBelow = from.below();
        for (Direction d : SUPPORT_DIRS) {
            BlockPos against = floor.relative(d);
            if (against.equals(sourceBelow)) continue;   // that's the backplace face, handled below
            if (canPlaceAgainst(level, against)) return 1.0;
        }
        if (!canPlaceAgainst(level, sourceBelow)) return ActionCosts.COST_INF;
        if (level.getBlockState(sourceBelow).is(Blocks.SOUL_SAND)) return ActionCosts.COST_INF;
        return ActionCosts.SNEAK_ONE_BLOCK / ActionCosts.WALK_ONE_BLOCK;
    }

    /** Baritone canPlaceAgainst: a full solid cube (or glass) presents a face to place
     *  against. {@code isCollisionShapeFullBlock} captures normal cubes + glass. */
    private static boolean canPlaceAgainst(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    /** Baritone MovementAscend place support: a face to place the step block against,
     *  excluding our own source column (no backplace mid-jump). */
    private static boolean ascendPlaceSupported(NavContext ctx, BlockPos from, BlockPos step) {
        BlockGetter level = ctx.view;
        for (Direction d : SUPPORT_DIRS) {
            BlockPos against = step.relative(d);
            if (against.getX() == from.getX() && against.getZ() == from.getZ()) continue;
            if (canPlaceAgainst(level, against)) return true;
        }
        return false;
    }

    // ---- Ascend: up one block, dig head-room, place step if needed ----

    private static Movement ascend(NavContext ctx, BlockPos from, Direction dir) {
        BlockGetter level = ctx.view;
        BlockPos dest = from.relative(dir).above(); // feet one up & over
        BlockPos destHead = dest.above();
        BlockPos jumpRoom = from.above(2);          // room to jump at the source column
        BlockPos step = dest.below();               // == from.relative(dir): floor we stand on after the step

        // Baritone MovementAscend suffocation veto: a FallingBlock at y+3 (above the
        // jump apex) would fall onto us once we clear the stack over our head. Refuse,
        // unless y+1 is already solid AND y+2 is itself falling (then we'd have had to
        // clear the whole stack to even stand here, so nothing is left to fall).
        if (level.getBlockState(from.above(3)).getBlock()
                    instanceof net.minecraft.world.level.block.FallingBlock
                && (BlockHelper.canWalkThrough(level, from.above(1))
                    || !(level.getBlockState(jumpRoom).getBlock()
                            instanceof net.minecraft.world.level.block.FallingBlock))) {
            return null;
        }
        // Can't jump-ascend off a ladder/vine (Baritone srcDown check).
        if (isLadderOrVine(level.getBlockState(from.below()))) return null;

        // Baritone MovementAscend: max(JUMP, WALK) + jumpPenalty (the jump and the
        // forward block overlap, so it's the larger of the two, not their sum) —
        // EXCEPT stepping ONTO soul sand costs the soul-sand walk instead, matching
        // how traverse/descend already price soul sand. (Bottom-slab ascend special
        // cases are not modelled — slabs aren't modelled anywhere in our move set.)
        double base = level.getBlockState(step).is(Blocks.SOUL_SAND)
                ? ActionCosts.WALK_ONE_OVER_SOUL_SAND
                : Math.max(ActionCosts.JUMP_ONE_BLOCK, ActionCosts.WALK_ONE_BLOCK);
        double cost = base + PathSettings.JUMP_PENALTY;
        List<BlockPos> toBreak = new ArrayList<>(3);

        double jumpBreak = clearCost(ctx, jumpRoom, toBreak);
        if (jumpBreak >= ActionCosts.COST_INF) return null;
        cost += jumpBreak;

        double feetBreak = clearCost(ctx, dest, toBreak);
        if (feetBreak >= ActionCosts.COST_INF) return null;
        cost += feetBreak;
        double headBreak = clearCost(ctx, destHead, toBreak, true);   // top cell: fold falling stack
        if (headBreak >= ActionCosts.COST_INF) return null;
        cost += headBreak;

        // The block we stand ON after the step (floor under dest) — `step` above.
        BlockPos toPlace = null;
        if (!BlockHelper.canWalkOn(level, step)) {
            double placeCost = ctx.costOfPlacing(step);
            if (placeCost >= ActionCosts.COST_INF) return null;
            // Baritone MovementAscend: the step block needs SOME face to place against
            // other than our own source column — we can't backplace mid-jump.
            if (!ascendPlaceSupported(ctx, from, step)) return null;
            cost += placeCost;
            toPlace = step;
        }

        if (BlockHelper.isHazard(level, dest) || BlockHelper.isHazard(level, destHead)) return null;
        return new Movement(Movement.Kind.ASCEND, from, dest, cost, toBreak, toPlace);
    }

    // ---- Descend / Fall: step out and drop to first safe floor ----

    private static Movement descend(NavContext ctx, BlockPos from, Direction dir) {
        BlockGetter level = ctx.view;
        BlockPos col = from.relative(dir);          // (destX, y, destZ)
        BlockPos landing = col.below();             // (destX, y-1): single-descend feet

        // Baritone MovementDescend breaks THREE cells of the dest column — the
        // landing-foot cell (y-1) plus the two body cells at source height (y, y+1)
        // — and that "frontBreak" applies to a single step-down AND to a longer fall.
        List<BlockPos> toBreak = new ArrayList<>(3);
        double frontBreak = 0;
        BlockPos[] frontCells = {landing, col, col.above()};   // col.above() is the top cell
        for (int i = 0; i < frontCells.length; i++) {
            double b = clearCost(ctx, frontCells[i], toBreak, i == frontCells.length - 1);
            if (b >= ActionCosts.COST_INF) return null;
            frontBreak += b;
        }
        // Can't descend off a ladder/vine — you'd climb it instead (Baritone fromDown check).
        if (isLadderOrVine(level.getBlockState(from.below()))) return null;

        BlockPos belowLanding = col.below(2);       // (destX, y-2)
        if (BlockHelper.canWalkOn(level, belowLanding)) {
            // Single-block descend: solid floor right below the landing. Walk off the
            // edge (soul-sand-scaled) + max(fall(1), center-after-fall).
            if (isLadderOrVine(level.getBlockState(landing))) return null;
            if (BlockHelper.isHazard(level, landing) || BlockHelper.isHazard(level, belowLanding)) return null;
            double walk = ActionCosts.WALK_OFF_BLOCK;
            if (level.getBlockState(from.below()).is(Blocks.SOUL_SAND)) {
                walk *= ActionCosts.WALK_ONE_OVER_SOUL_SAND / ActionCosts.WALK_ONE_BLOCK;
            }
            double total = frontBreak + walk
                    + Math.max(ActionCosts.fallCost(1), ActionCosts.CENTER_AFTER_FALL);
            return new Movement(Movement.Kind.DESCEND, from, landing, total, toBreak, null);
        }

        // Longer fall (Baritone dynamicFallCost, dry-land subset). The landing cell
        // must be air to fall through; scan down for the first floor. Feet land one
        // above the floor at y-fallHeight+1, and the fall is charged FALL_N[fallHeight]
        // — note this is fallHeight, NOT (blocks dropped): a fall to y-2 is fallHeight 3.
        // (Water/lava landings and bucket MLG are intentionally out of scope; vine/
        // ladder fall-speed resets are not modelled, so such columns cost the full
        // height.) Bound: unprotectedFallHeight <= maxFallHeightNoWater + 1.
        if (!BlockHelper.canWalkThrough(level, belowLanding)) return null;
        for (int fallHeight = 3; fallHeight <= ctx.maxFallHeight + 1; fallHeight++) {
            BlockPos onto = col.below(fallHeight);   // (destX, y-fallHeight): floor candidate
            if (BlockHelper.canWalkThrough(level, onto)) continue;   // still air — keep falling
            if (!BlockHelper.canWalkOn(level, onto)) return null;    // not standable — abort
            BlockPos feet = onto.above();                            // (destX, y-fallHeight+1)
            if (BlockHelper.isHazard(level, onto) || BlockHelper.isHazard(level, feet)) return null;
            double total = ActionCosts.WALK_OFF_BLOCK + ActionCosts.fallCost(fallHeight) + frontBreak;
            return new Movement(Movement.Kind.FALL, from, feet, total, toBreak, null);
        }
        return null;
    }

    /** A ladder or vine — you climb these, so they block stepping off / can reset a fall. */
    private static boolean isLadderOrVine(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.LADDER) || state.is(Blocks.VINE);
    }

    // ---- Diagonal: same-Y corner step over EXISTING ground only ----

    /**
     * Pure walking shortcut across a corner. Deliberately conservative: it
     * neither breaks nor places anything — diagonal bridging/digging produces
     * the ugly, unnatural "corner-to-corner staircase" (blocks touching only at
     * their corners), so all terrain modification is left to the cardinal moves,
     * which lay straight bridges that turn at 90°. A diagonal is emitted only
     * when the destination is already a clear, standable cell and neither
     * orthogonal cell we cut between is obstructed (no corner-clipping).
     */
    private static Movement diagonal(NavContext ctx, BlockPos from, Direction a, Direction b) {
        BlockGetter level = ctx.view;
        BlockPos dest = from.relative(a).relative(b);

        // Destination must already be a valid standing spot — solid floor below,
        // body clearance at feet+head. No placing, no breaking.
        if (!BlockHelper.isStandable(level, dest)) return null;

        // Both orthogonal cells we pass between must be open at both body
        // heights, otherwise the entity would clip a corner.
        BlockPos cornerA = from.relative(a);
        BlockPos cornerB = from.relative(b);
        if (!BlockHelper.canWalkThrough(level, cornerA)
                || !BlockHelper.canWalkThrough(level, cornerA.above())) return null;
        if (!BlockHelper.canWalkThrough(level, cornerB)
                || !BlockHelper.canWalkThrough(level, cornerB.above())) return null;

        if (BlockHelper.isHazard(level, dest) || BlockHelper.isHazard(level, dest.above())) return null;

        // Baritone MovementDiagonal: per-block walk cost (sprint-discounted on a
        // clear diagonal) times the √2 corner distance.
        double mult = ctx.canSprint ? ActionCosts.WALK_ONE_BLOCK * ActionCosts.SPRINT_MULTIPLIER
                : ActionCosts.WALK_ONE_BLOCK;
        double cost = mult * ActionCosts.SQRT_2;
        return new Movement(Movement.Kind.DIAGONAL, from, dest, cost, List.of(), null);
    }

    // ---- Pillar: jump up one, placing a block beneath as we rise ----

    private static Movement pillar(NavContext ctx, BlockPos from) {
        BlockGetter level = ctx.view;
        // Never pillar out of water: the jump cycle needs onGround, which a
        // floating body never has — the move would just stall and churn
        // replans. Height from water is "swim to shore first" by design.
        if (BlockHelper.isWater(level, from)) return null;
        BlockPos dest = from.above();      // feet end one block up
        BlockPos newHead = from.above(2);  // head room while standing on the new block

        // Need a scaffold block to drop under our feet (the current feet cell).
        double placeCost = ctx.costOfPlacing(from);
        if (placeCost >= ActionCosts.COST_INF) return null;
        // Baritone MovementPillar: +0.1 tick when what's below our feet is currently
        // air — slightly penalise pillaring on air vs on solid ground.
        if (level.getBlockState(from.below()).isAir()) {
            placeCost += 0.1;
        }

        // Baritone MovementPillar (block tower): jump + place-underfoot + jumpPenalty.
        double cost = ActionCosts.JUMP_ONE_BLOCK + placeCost + PathSettings.JUMP_PENALTY;
        List<BlockPos> toBreak = new ArrayList<>(1);
        double headBreak = clearCost(ctx, newHead, toBreak, true);   // top cell: fold falling stack
        if (headBreak >= ActionCosts.COST_INF) return null;
        cost += headBreak;

        if (BlockHelper.isHazard(level, dest) || BlockHelper.isHazard(level, newHead)) return null;
        // toPlace deliberately null: placement is the PILLAR phase's own job,
        // done mid-jump at the entity's LIVE base Y (which may differ from the
        // plan by a block) — the generic PREPARE_PLACE phase never runs for
        // pillars, so a toPlace here would be dead, misleading data.
        return new Movement(Movement.Kind.PILLAR, from, dest, cost, toBreak, null);
    }

    // ---- DigDown: mine the floor underfoot and drop one ----

    private static Movement digDown(NavContext ctx, BlockPos from) {
        if (!PathSettings.ALLOW_DOWNWARD) return null;
        BlockGetter level = ctx.view;
        BlockPos below = from.below();       // floor block to mine == destination feet
        BlockPos landing = from.below(2);    // must be solid to stand on after the drop

        if (!BlockHelper.canWalkOn(level, landing)) return null;

        double breakCost = ctx.costOfBreaking(below);
        if (breakCost >= ActionCosts.COST_INF) return null; // air/unbreakable handled by descend
        if (BlockHelper.isHazard(level, below) || BlockHelper.isHazard(level, landing)) return null;

        List<BlockPos> toBreak = new ArrayList<>(1);
        toBreak.add(below.immutable());
        double cost = breakCost + ActionCosts.fallCost(1);
        return new Movement(Movement.Kind.DIG_DOWN, from, below, cost, toBreak, null);
    }

    // ---- Parkour: a running jump across a 2-4 block gap at the same level ----

    /**
     * A single atomic edge that clears a gap by jumping, rather than bridging it
     * with scaffolding (Baritone {@code MovementParkour}). Emitted only when
     * there's a genuine gap straight ahead (no floor immediately in front) and a
     * clear air corridor at body height across to the nearest landing within
     * {@link #MAX_PARKOUR}. The momentum + jump timing are supplied by the
     * executor at runtime; the planner only asserts the gap is jumpable.
     *
     * <p>Conservative scope: flat (same-Y) landings only — ascend/drop parkour
     * and parkour-place are deferred. Neither breaks nor places anything.
     */
    private static Movement parkour(NavContext ctx, BlockPos from, Direction dir) {
        if (!PathSettings.ALLOW_PARKOUR) return null;   // Baritone default: parkour off
        BlockGetter level = ctx.view;
        // Only a real gap warrants a jump: a floor immediately ahead means a
        // plain traverse/descend already covers it.
        if (BlockHelper.canWalkOn(level, from.relative(dir).below())) return null;
        // Head-room to jump from the takeoff.
        if (!BlockHelper.canWalkThrough(level, from.above(2))) return null;

        for (int d = 2; d <= MAX_PARKOUR; d++) {
            // Every cell from 1..d must be clear air at feet & head (no clipping).
            boolean clear = true;
            for (int i = 1; i <= d; i++) {
                BlockPos g = from.relative(dir, i);
                if (!BlockHelper.canWalkThrough(level, g)
                        || !BlockHelper.canWalkThrough(level, g.above())) {
                    clear = false;
                    break;
                }
                // The arc rises ~1.25, so over the gap interior the head sweeps
                // the third cell too — a 3-high ceiling bonks the jump short.
                if (i < d && !BlockHelper.canWalkThrough(level, g.above(2))) {
                    clear = false;
                    break;
                }
            }
            if (!clear) break;   // blocked within the gap → no longer jump is possible

            BlockPos land = from.relative(dir, d);
            if (!BlockHelper.canWalkOn(level, land.below())) continue;   // no floor here, try farther
            if (BlockHelper.isHazard(level, land) || BlockHelper.isHazard(level, land.above())) break;
            double cost = ActionCosts.costFromJumpDistance(d);
            return new Movement(Movement.Kind.PARKOUR, from, land, cost, List.of(), null);
        }
        return null;
    }

    /**
     * Cost to make {@code cell} body-passable: 0 if already clear, the break
     * cost (and adds it to {@code toBreak}) if a breakable obstruction,
     * {@link ActionCosts#COST_INF} if unbreakable.
     */
    private static double clearCost(NavContext ctx, BlockPos cell, List<BlockPos> toBreak) {
        return clearCost(ctx, cell, toBreak, false);
    }

    /** {@code includeFalling}: this is the TOP cell the move breaks, so fold in the
     *  cost of the FallingBlock stack above it (it cascades down as we dig). */
    private static double clearCost(NavContext ctx, BlockPos cell, List<BlockPos> toBreak,
                                    boolean includeFalling) {
        if (BlockHelper.canWalkThrough(ctx.view, cell)) return 0.0;
        double breakCost = ctx.costOfBreaking(cell, includeFalling);
        if (breakCost >= ActionCosts.COST_INF) return ActionCosts.COST_INF;
        toBreak.add(cell.immutable());
        return breakCost;
    }
}
