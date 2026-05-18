package com.dwinovo.animus.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-slot configuration for one of a player's six Animus units. Holds
 * <strong>only persistent metadata</strong> (name, model_key, alive flag).
 * Runtime state — the currently-spawned vanilla entity, the active
 * EntityAgent's ConvoState — lives elsewhere and is intentionally
 * non-persistent (entity is re-spawned fresh each assignment, conversation
 * is task-scoped).
 *
 * <p>Mutable POJO rather than record because the {@link #name} and
 * {@link #modelKey} change via the GUI tab and we don't want to thrash
 * record-update churn through every change.
 *
 * @see PlayerAnimusData
 */
public final class UnitConfig {

    /** 1..6 — the stable id the LLM sees and references. */
    public final int unitId;

    /** Player-set display name. {@code null} means unnamed (show "Unit N"). */
    private String name;

    /** Bedrock-model identifier (e.g. {@code "animus:hachiware"}). */
    private String modelKey;

    /** False = dead, in respawn cooldown. True = available for assignment. */
    private boolean alive;

    public UnitConfig(int unitId, String name, String modelKey, boolean alive) {
        this.unitId = unitId;
        this.name = name;
        this.modelKey = modelKey;
        this.alive = alive;
    }

    public static UnitConfig defaultFor(int unitId) {
        // null name = "unnamed" (LLM will see name=null in env block)
        return new UnitConfig(unitId, null, "animus:hachiware", true);
    }

    /**
     * Codec for persistence. Treats {@code null} name as an empty string on
     * the wire — emptied on the way out, re-nulled on read.
     */
    public static final Codec<UnitConfig> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("unit_id").forGetter(c -> c.unitId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(c -> c.name == null ? "" : c.name),
            Codec.STRING.fieldOf("model_key").forGetter(c -> c.modelKey),
            Codec.BOOL.fieldOf("alive").forGetter(c -> c.alive)
    ).apply(inst, (id, name, model, alive) ->
            new UnitConfig(id, name.isEmpty() ? null : name, model, alive)));

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public String modelKey() { return modelKey; }
    public void setModelKey(String modelKey) { this.modelKey = modelKey; }

    public boolean alive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    /** Display label for GUI lists. Falls back to "Unit N" when unnamed. */
    public String displayLabel() {
        return name != null && !name.isBlank() ? name : "Unit " + unitId;
    }
}
