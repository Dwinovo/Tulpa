package com.dwinovo.numen.agent.tool.api;

import com.dwinovo.numen.agent.tool.tools.GetSelfStatusTool;
import com.dwinovo.numen.agent.tool.tools.MoveToTool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dogfood for the {@link ToolSchema} reflection layer: it must reproduce the
 * structure of the hand-written schemas the built-in tools carry, so migrating
 * a tool to {@code @NumenAction} can't silently change what the model sees.
 *
 * <p>Two deliberately hard shapes are covered:
 * <ul>
 *   <li>{@code get_self_status} — no arguments (empty properties / required);</li>
 *   <li>{@code move_to} — nullable coordinate union ({@code [number,null]}),
 *       a constrained {@code speed} ({@code minimum}/{@code maximum}), and the
 *       strict-mode rule that every property is listed in {@code required}.</li>
 * </ul>
 * Descriptions are compared separately (they are free-text passthrough); the
 * structural assertion strips them so the test isn't a copy of paragraph prose.
 */
class ToolSchemaTest {

    /** Sample holder mirroring the built-in tools' signatures (bodies irrelevant here). */
    static final class SampleTools {

        @NumenAction(name = "get_self_status", description = "read status")
        String getSelfStatus() {
            return null;
        }

        @NumenAction(name = "move_to", description = "travel somewhere")
        void moveTo(
                @Arg(value = "target x", required = false) Double x,
                @Arg(value = "target y", required = false) Double y,
                @Arg(value = "target z", required = false) Double z,
                @Arg(value = "speed multiplier", min = 0.1, max = 2.0) double speed) {
        }
    }

    @Test
    void noArgToolMatchesHandWrittenSchema() {
        Map<String, Object> generated = ToolSchema.schemaFor(method("getSelfStatus"));
        Map<String, Object> handWritten = new GetSelfStatusTool().parameterSchema();
        assertEquals(stripDescriptions(handWritten), stripDescriptions(generated),
                "no-arg schema structure must match the hand-written one");
        assertEquals("get_self_status", ToolSchema.actionName(method("getSelfStatus")));
    }

    @Test
    void constrainedToolMatchesHandWrittenSchema() {
        Method m = method("moveTo");
        Map<String, Object> generated = ToolSchema.schemaFor(m);
        Map<String, Object> handWritten = new MoveToTool().parameterSchema();

        // Property keys come from the parameter names (requires -parameters at compile time).
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) generated.get("properties");
        assertEquals(List.of("x", "y", "z", "speed"), new ArrayList<>(props.keySet()),
                "parameter names must drive property keys (is -parameters enabled?)");

        assertEquals(stripDescriptions(handWritten), stripDescriptions(generated),
                "constrained schema structure (nullable union, min/max, all-required) must match");
        assertEquals("move_to", ToolSchema.actionName(m));
    }

    @Test
    void descriptionsArePassedThrough() {
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) ToolSchema.schemaFor(method("moveTo")).get("properties");
        for (var e : props.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) e.getValue();
            Object desc = prop.get("description");
            assertTrue(desc instanceof String && !((String) desc).isBlank(),
                    "property '" + e.getKey() + "' must carry a non-blank description");
        }
    }

    private static Method method(String name) {
        for (Method m : SampleTools.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new AssertionError("no such sample method: " + name);
    }

    /** Recursively drop every {@code description} key so the comparison is purely structural. */
    @SuppressWarnings("unchecked")
    private static Object stripDescriptions(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                if ("description".equals(e.getKey())) continue;
                out.put((String) e.getKey(), stripDescriptions(e.getValue()));
            }
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object x : list) out.add(stripDescriptions(x));
            return out;
        }
        return node;
    }
}
