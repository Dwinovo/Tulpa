package com.dwinovo.animus.pathing.exec.drive;

import com.dwinovo.animus.entity.AnimusEntity;
import com.dwinovo.animus.pathing.exec.BodyMotor;
import com.dwinovo.animus.pathing.movement.Movement;
import com.dwinovo.animus.pathing.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * PILLAR: jump straight up and drop a scaffold block beneath mid-rise. All
 * sub-state (the per-cycle base height, the placed-this-cycle latch) lives
 * here — the executor no longer carries pillar fields it must remember to
 * reset. Input-driven: MoveControl never gets a wantedY above the entity
 * (that makes vanilla auto-jump and fight our explicit jump cycle).
 */
public final class PillarDrive extends MovementDrive {

    /** No cycle started yet — keeps the airborne place branch off. */
    private int pillarBaseY = Integer.MAX_VALUE;
    private boolean placedThisCycle = false;

    public PillarDrive(AnimusEntity entity, Movement mv, DriveHost host) {
        super(entity, mv, host);
    }

    @Override
    protected boolean placesFloor() {
        return false;   // placement is this drive's own mid-jump job
    }

    @Override
    protected Result drive() {
        Level level = entity.level();
        double cx = mv.dest.getX() + 0.5;
        double cz = mv.dest.getZ() + 0.5;
        // Drive MoveControl toward the column at the entity's CURRENT height —
        // rising is entirely our explicit jump's job. Off-column pushes are
        // caught by the executor's re-localization.
        motor.steer(BodyMotor.Owner.PATH, cx, entity.getY(), cz, 0.2);
        entity.getLookControl().setLookAt(cx, mv.dest.getY() + 1.0, cz);

        double cdx = entity.getX() - cx;
        double cdz = entity.getZ() - cz;
        boolean centred = (cdx * cdx + cdz * cdz) <= 0.10;   // over the column (~0.3 block)

        // Reached the planned top — advance WITHOUT requiring onGround (the
        // apex is often airborne; feetY is floored so this only fires at/above
        // target) but only once a floor actually exists beneath, else we're
        // mid-rise with nothing placed yet and would just fall back through.
        if (entity.blockPosition().getY() >= mv.dest.getY() && centred
                && BlockHelper.canWalkOn(level, entity.blockPosition().below())) {
            return Result.STEP_DONE;
        }

        if (entity.onGround()) {
            if (centred) {
                // Start one pillar cycle from our ACTUAL feet height.
                pillarBaseY = entity.blockPosition().getY();
                placedThisCycle = false;
                motor.jump(BodyMotor.Owner.PATH);
            }
            // not centred → don't jump; let MoveControl centre us.
        } else if (!placedThisCycle && pillarBaseY != Integer.MAX_VALUE
                && entity.getY() >= pillarBaseY + 1.0) {
            // Airborne with the box fully ABOVE the base cell (apex is +1.25,
            // so +1.0..+1.25 is the placement window) → drop a scaffold in to
            // land on. Below +1.0 the block would materialize inside our legs.
            BlockPos base = new BlockPos(mv.dest.getX(), pillarBaseY, mv.dest.getZ());
            if (BlockHelper.canWalkOn(level, base)) {
                placedThisCycle = true;   // already solid somehow
            } else if (placementObstructed(base)) {
                // Someone's box still clips the cell — retry within the window.
                return Result.RUNNING;
            } else if (BlockHelper.isReplaceableForPlacement(level, base)) {
                ItemStack stack = takeScaffold();
                if (stack == null) {
                    host.log("REPLAN: out of scaffolding mid-pillar @ " + base);
                    return Result.NEEDS_REPLAN;
                }
                BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
                if (!level.setBlock(base, state, 3)) {
                    host.log("REPLAN: pillar setBlock failed @ " + base);
                    return Result.NEEDS_REPLAN;
                }
                stack.shrink(1);
                entity.getInventory().setChanged();
                entity.pathTally().addPlaced(state.getBlock());
                entity.scaffoldLedger().record(base, state.getBlock());
                host.log("placed pillar block " + state.getBlock() + " @ " + base);
                placedThisCycle = true;
            }
        }
        return Result.RUNNING;
    }
}
