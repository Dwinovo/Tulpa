package com.dwinovo.animus.pathing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cost-model invariants the A* heuristic depends on — most
 * importantly the DESCEND_ONE_BLOCK ↔ fall-cap coupling documented on the
 * constant: if someone raises the fall height without lowering the bound,
 * this is the test that goes red instead of path quality silently degrading.
 */
class ActionCostsTest {

    @Test
    void fallCostIsZeroAtGroundAndMonotonic() {
        assertEquals(0.0, ActionCosts.fallCost(0));
        double prev = 0.0;
        for (int n = 1; n <= 16; n++) {
            double c = ActionCosts.fallCost(n);
            assertTrue(c > prev, "fallCost must grow with height (n=" + n + ")");
            prev = c;
        }
    }

    @Test
    void descendBoundIsAdmissibleUpToTheFallCap() {
        // The heuristic charges DESCEND_ONE_BLOCK per block of descent; every
        // legal way down must cost at least that per block. Falls are capped
        // at 3 — check the bound against every legal fall height.
        for (int n = 1; n <= 3; n++) {
            double perBlock = ActionCosts.fallCost(n) / n;
            assertTrue(perBlock >= ActionCosts.DESCEND_ONE_BLOCK,
                    "fallCost(" + n + ")/n = " + perBlock + " undercuts the heuristic bound");
        }
        // And document WHY the cap matters: at 10 blocks the bound would break.
        assertTrue(ActionCosts.fallCost(10) / 10 < ActionCosts.DESCEND_ONE_BLOCK,
                "if this stops holding, the coupling comment on DESCEND_ONE_BLOCK is stale");
    }

    @Test
    void parkourCostGrowsWithGapAndExceedsPlainWalking() {
        double prev = 0.0;
        for (int gap = 2; gap <= 4; gap++) {
            double c = ActionCosts.costFromJumpDistance(gap);
            assertTrue(c > prev, "jump cost must grow with gap");
            assertTrue(c > ActionCosts.WALK_ONE_BLOCK * gap,
                    "a jump must never be cheaper than walking the same distance");
            prev = c;
        }
    }

    @Test
    void placingCostsMoreThanCanonicalDigging() {
        // The design contract from the field: digging beats placing by 0.5 in
        // the canonical case (stone + correct pickaxe ≈ 6 ticks + BREAK_ADDITIONAL).
        double canonicalDig = 6.0 + ActionCosts.BREAK_ADDITIONAL;
        assertEquals(canonicalDig + 0.5, ActionCosts.PLACE_BLOCK, 1e-9);
    }
}
