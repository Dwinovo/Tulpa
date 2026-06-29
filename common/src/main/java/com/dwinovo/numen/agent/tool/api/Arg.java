package com.dwinovo.numen.agent.tool.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as a model-facing tool argument. Its name comes from
 * the parameter name (bytecode must be compiled with {@code -parameters}; the
 * Numen build does this) unless {@link #name()} overrides it; its JSON-schema
 * type is derived from the parameter's Java type. The author supplies only the
 * description plus any constraints that can't be read from the type.
 *
 * <p>Parameters <em>without</em> this annotation are engine-injected context
 * (the companion handle, the reply callback) and are excluded from the schema.
 *
 * <h2>Optionality and strict mode</h2>
 * {@link #required()} {@code = false} makes the argument <em>nullable</em>
 * (type becomes a {@code [type, "null"]} union) — but the property still appears
 * in the schema's {@code required} list. This matches the OpenAI strict /
 * structured-output contract, where every property must be listed in
 * {@code required} and "optional" is expressed as "may be null", not "may be
 * absent". A nullable arg should map to a boxed / nullable Java parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Arg {

    /** Human description of the argument, shown to the model. */
    String value();

    /** Override the argument name. Empty = use the parameter's own name. */
    String name() default "";

    /** {@code false} = nullable (a {@code [type,"null"]} union); still listed in {@code required}. */
    boolean required() default true;

    /** Inclusive numeric lower bound ({@code minimum}). {@code NaN} = unset. */
    double min() default Double.NaN;

    /** Inclusive numeric upper bound ({@code maximum}). {@code NaN} = unset. */
    double max() default Double.NaN;

    /** Minimum array length ({@code minItems}) for a {@code List}/array argument. Negative = unset. */
    int minItems() default -1;

    /** Allowed string values ({@code enum}). Empty = unconstrained. */
    String[] enumValues() default {};
}
