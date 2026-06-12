package com.dwinovo.animus.pathing.exec.drive;

import com.dwinovo.animus.pathing.movement.Movement;

/**
 * The executor-side services a {@link MovementDrive} may consult — the only
 * coupling channel back to the path. Deliberately tiny: drives own their
 * movement's execution; the host owns cross-movement concerns (what comes
 * next, the user's requested speed, diagnostics).
 */
public interface DriveHost {

    /** The movement after the one being driven, or null at the path end. */
    Movement nextMovement();

    /** The user-requested travel speed (drives apply their own safety caps). */
    double userSpeed();

    /** Verbose diagnostics channel (no-op unless pathing VERBOSE is on). */
    void log(String msg);
}
