package com.dwinovo.animus.pathing.exec;

import com.dwinovo.animus.entity.AnimusEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The single sanctioned writer of the entity's locomotion controls
 * ({@code MoveControl} wanted-position, {@code JumpControl}, direct velocity).
 *
 * <h2>Why an arbitration layer</h2>
 * The motor state is shared and mutable: the path executor, the water-escape
 * reflex and combat all used to write it directly, and every ownership
 * transition leaked the previous writer's state — the canonical bug being a
 * stale {@code MOVE_TO} thrusting the body toward a submerged node while the
 * escape reflex tried to swim it ashore. Routing every write through one
 * object with an {@link Owner} makes the cleanup structural: claiming the
 * motor from a different owner parks it first, releasing it parks it again.
 *
 * <h2>Honest scope</h2>
 * Vanilla {@code PathNavigation} (used by the escape reflex and vanilla
 * combat goals) writes MoveControl internally and cannot be routed through
 * here without mixins. It remains a documented co-writer: reflex goals claim
 * {@link Owner#REFLEX} on start (parking any PATH leftovers) and release on
 * stop, which is exactly the discipline the manual parking calls used to
 * approximate one bug at a time.
 */
public final class BodyMotor {

    /** Who currently drives the body. Transitions auto-park. */
    public enum Owner { NONE, PATH, REFLEX }

    private final AnimusEntity entity;
    private Owner owner = Owner.NONE;

    public BodyMotor(AnimusEntity entity) {
        this.entity = entity;
    }

    /** Steer toward a point at {@code speed} (MoveControl MOVE_TO). */
    public void steer(Owner who, double x, double y, double z, double speed) {
        claim(who);
        entity.getMoveControl().setWantedPosition(x, y, z, speed);
    }

    /** Keep the facing on a point but add no thrust (speed-0 park-with-aim). */
    public void aim(Owner who, double x, double y, double z) {
        claim(who);
        entity.getMoveControl().setWantedPosition(x, y, z, 0.0);
    }

    /** Stand still: park MoveControl at the entity's own position. */
    public void hold(Owner who) {
        claim(who);
        park();
    }

    /** One-shot velocity impulse (parkour launch, sustained airborne vector). */
    public void impulse(Owner who, double vx, double vy, double vz) {
        claim(who);
        entity.setDeltaMovement(vx, vy, vz);
    }

    /** Damp horizontal velocity by {@code factor}, leaving Y to gravity. */
    public void brakeHorizontal(Owner who, double factor) {
        claim(who);
        Vec3 d = entity.getDeltaMovement();
        entity.setDeltaMovement(d.x * factor, d.y, d.z * factor);
    }

    /** Trigger the vanilla jump control. */
    public void jump(Owner who) {
        claim(who);
        entity.getJumpControl().jump();
    }

    /**
     * Give the motor up. Only the current owner can release; the motor parks
     * so nothing stale keeps steering after the owner stops ticking.
     */
    public void release(Owner who) {
        if (owner == who) {
            park();
            owner = Owner.NONE;
        }
    }

    private void claim(Owner who) {
        if (owner != who) {
            park();   // never inherit the previous owner's wanted-position
            owner = who;
        }
    }

    private void park() {
        entity.getMoveControl().setWantedPosition(
                entity.getX(), entity.getY(), entity.getZ(), 0.0);
    }
}
