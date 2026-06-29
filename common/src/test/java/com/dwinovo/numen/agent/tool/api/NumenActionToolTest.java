package com.dwinovo.numen.agent.tool.api;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.agent.tool.tools.GetSelfStatusTool;
import com.dwinovo.numen.agent.tool.tools.PerceptionTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zero-regression proof for the first real {@code @NumenAction} migration:
 * the reflected {@code get_self_status} must present the <em>identical</em>
 * LLM-facing surface (name, description, parameter schema) as the hand-written
 * {@link GetSelfStatusTool} it replaces, so tool selection can't shift. The
 * original is kept as the oracle until a batch of tools is migrated.
 */
class NumenActionToolTest {

    @Test
    void migratedGetSelfStatusMatchesOriginalSurface() {
        NumenTool migrated = NumenTools.tool(new PerceptionTools(), "get_self_status");
        NumenTool original = new GetSelfStatusTool();

        assertEquals(original.name(), migrated.name());
        assertEquals(original.description(), migrated.description());
        assertEquals(original.parameterSchema(), migrated.parameterSchema(),
                "auto-derived schema must equal the hand-written one");
        assertTrue(migrated.isQuery(), "get_self_status must stay a server query");
    }
}
