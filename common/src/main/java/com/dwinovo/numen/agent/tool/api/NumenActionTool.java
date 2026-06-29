package com.dwinovo.numen.agent.tool.api;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.TaskResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link NumenTool} backed by a reflected {@link NumenAction} method — the
 * runtime bridge from the annotation authoring surface to the existing tool
 * machinery. Name / description / schema come from {@link ToolSchema}; calling
 * the tool binds the model's JSON arguments to the method's typed parameters,
 * injects engine context by type, and invokes the method.
 *
 * <h2>Where it runs — inferred, not declared</h2>
 * The author never tags a category. The adapter infers it from the signature:
 * a method that takes the live {@link NumenPlayer} body and returns a value is
 * a server-side <em>query</em> (runs on the tick thread against the
 * authoritative entity). Other shapes (pure-client, body tasks) are added as
 * tools migrate; until then they raise a clear "shape not yet supported".
 */
public final class NumenActionTool implements NumenTool {

    @FunctionalInterface
    private interface Binder {
        Object bind(JsonObject args, NumenPlayer entity);
    }

    private enum Kind { QUERY }

    private final Object holder;
    private final Method method;
    private final String name;
    private final String description;
    private final Map<String, Object> schema;
    private final long timeoutTicks;
    private final Binder[] binders;
    private final Kind kind;

    public NumenActionTool(Object holder, Method method) {
        NumenAction action = method.getAnnotation(NumenAction.class);
        if (action == null) {
            throw new IllegalArgumentException(method + " is not annotated with @NumenAction");
        }
        this.holder = holder;
        this.method = method;
        method.setAccessible(true);
        this.name = ToolSchema.actionName(method);
        this.description = action.description();
        this.schema = ToolSchema.schemaFor(method);
        this.timeoutTicks = action.timeoutTicks();

        boolean injectsEntity = false;
        List<Binder> plan = new ArrayList<>();
        for (Parameter p : method.getParameters()) {
            Arg arg = p.getAnnotation(Arg.class);
            if (arg != null) {
                String argName = arg.name().isEmpty() ? p.getName() : arg.name();
                Class<?> type = p.getType();
                boolean required = arg.required();
                plan.add((args, entity) -> coerce(args, argName, type, required));
            } else if (NumenPlayer.class.isAssignableFrom(p.getType())) {
                injectsEntity = true;
                plan.add((args, entity) -> entity);
            } else {
                throw new IllegalArgumentException("@NumenAction " + name
                        + ": parameter " + p.getName() + " of type " + p.getType().getName()
                        + " is neither an @Arg nor an injectable context type");
            }
        }
        this.binders = plan.toArray(new Binder[0]);

        boolean returnsValue = method.getReturnType() != void.class;
        if (injectsEntity && returnsValue) {
            this.kind = Kind.QUERY;
        } else {
            throw new IllegalArgumentException("@NumenAction " + name
                    + ": tool shape not yet supported by the adapter (only server queries so far)");
        }
    }

    @Override public String name() { return name; }
    @Override public String description() { return description; }
    @Override public Map<String, Object> parameterSchema() { return schema; }
    @Override public long defaultTimeoutTicks() { return timeoutTicks; }

    @Override public boolean isQuery() { return kind == Kind.QUERY; }

    @Override
    public String executeQuery(JsonObject args, NumenPlayer entity) {
        Object[] argv = new Object[binders.length];
        for (int i = 0; i < binders.length; i++) {
            argv[i] = binders[i].bind(args, entity);
        }
        Object result;
        try {
            result = method.invoke(holder, argv);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            // Argument-validation failures surface as a failed tool result; the
            // payload handler converts IllegalArgumentException for us.
            if (cause instanceof IllegalArgumentException iae) throw iae;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("cannot invoke @NumenAction " + name, ex);
        }
        return resultToString(result);
    }

    private String resultToString(Object result) {
        if (result instanceof String s) return s;
        if (result instanceof TaskResult tr) return tr.toJson();
        if (result == null) {
            throw new IllegalStateException("@NumenAction " + name + " returned null");
        }
        return result.toString();
    }

    /** Coerce one JSON argument to the method parameter's Java type. */
    private Object coerce(JsonObject args, String key, Class<?> type, boolean required) {
        JsonElement el = args.get(key);
        if (el == null || el.isJsonNull()) {
            if (required) throw new IllegalArgumentException("missing required argument: " + key);
            return null;   // nullable arg → boxed null
        }
        try {
            if (type == String.class) return el.getAsString();
            if (type == int.class || type == Integer.class) return el.getAsInt();
            if (type == long.class || type == Long.class) return el.getAsLong();
            if (type == double.class || type == Double.class) return el.getAsDouble();
            if (type == float.class || type == Float.class) return el.getAsFloat();
            if (type == boolean.class || type == Boolean.class) return el.getAsBoolean();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("argument '" + key + "' has the wrong type");
        }
        throw new IllegalArgumentException("unsupported @Arg type for '" + key + "': " + type.getName());
    }
}
