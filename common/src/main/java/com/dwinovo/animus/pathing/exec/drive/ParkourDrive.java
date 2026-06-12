package com.dwinovo.animus.pathing.exec.drive;

import com.dwinovo.animus.entity.AnimusEntity;
import com.dwinovo.animus.pathing.exec.BodyMotor;
import com.dwinovo.animus.pathing.movement.Movement;
import net.minecraft.core.BlockPos;

/**
 * PARKOUR: a committed running jump across a gap. Ballistic — once launched,
 * the drive holds the takeoff vector against drag and never re-aims (homing
 * reverses the velocity once past the centre and drops the body into the
 * gap). All launch state lives here.
 *
 * <p>The executor knows one thing about parkour: while {@link #launched()}
 * and airborne, re-localization must not run (the feet are legitimately over
 * the void, in no movement's valid set).
 */
public final class ParkourDrive extends MovementDrive {

    private boolean launched = false;
    private double dirX, dirZ;
    private double vel;

    public ParkourDrive(AnimusEntity entity, Movement mv, DriveHost host) {
        super(entity, mv, host);
    }

    @Override
    protected boolean placesFloor() {
        return false;   // a parkour edge is a clear-air jump, nothing to place
    }

    /** Mid-flight (ballistic) — the executor skips re-localization. */
    public boolean launched() {
        return launched && !entity.onGround();
    }

    @Override
    protected Result drive() {
        double cx = mv.dest.getX() + 0.5;
        double cz = mv.dest.getZ() + 0.5;
        entity.getLookControl().setLookAt(cx, mv.dest.getY() + entity.getEyeHeight(), cz);

        // Landed cleanly on the target.
        if (entity.blockPosition().equals(mv.dest) && entity.onGround()) {
            return Result.STEP_DONE;
        }

        if (!launched) {
            // Hold at the takeoff until grounded, THEN launch — never let
            // MoveControl walk us off the edge into the gap before we jump.
            motor.hold(BodyMotor.Owner.PATH);
            double dx = cx - entity.getX();
            double dz = cz - entity.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (entity.onGround() && dist > 1.0e-3) {
                // Commit the whole jump in one impulse. A 0.42 jump is airborne
                // ~10 ticks to a same-Y landing; with the per-tick re-assert
                // below, horizontal travel ≈ vel × 10, so size vel to put us at
                // the landing centre (with a little margin for residual drag).
                dirX = dx / dist;
                dirZ = dz / dist;
                vel = Math.clamp(dist * 0.10, 0.15, 0.55);
                motor.impulse(BodyMotor.Owner.PATH, dirX * vel, 0.42, dirZ * vel);
                launched = true;
                // Park MoveControl's aim on the landing: faces us forward
                // without steering (its op would otherwise still point at the
                // takeoff hold and turn us around mid-flight).
                motor.aim(BodyMotor.Owner.PATH, cx, mv.dest.getY(), cz);
                host.log(String.format("parkour launch -> %s v=%.2f", mv.dest, vel));
            }
            return Result.RUNNING;
        }

        // Airborne & committed: hold the LAUNCH vector against drag while
        // vanilla gravity owns Y.
        if (!entity.onGround()) {
            motor.impulse(BodyMotor.Owner.PATH, dirX * vel,
                    entity.getDeltaMovement().y, dirZ * vel);
            return Result.RUNNING;
        }

        // Touched down somewhere that isn't dest (dest returned above).
        BlockPos feet = entity.blockPosition();
        if (feet.equals(mv.src)) {
            // Slipped back onto the takeoff — line up and jump again.
            launched = false;
            return Result.RUNNING;
        }
        // Landed short or aside. Re-localization already ran this tick and
        // matched nothing (an on-path landing would have resynced the index),
        // and there is no jumping out of the gap from below — replan.
        host.log("REPLAN parkour landed off-target @ " + feet + " (wanted " + mv.dest + ")");
        return Result.NEEDS_REPLAN;
    }
}
