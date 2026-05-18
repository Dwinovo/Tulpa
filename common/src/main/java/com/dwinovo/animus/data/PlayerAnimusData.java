package com.dwinovo.animus.data;

import com.dwinovo.animus.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side per-player container: holds the 6 {@link UnitConfig} slots,
 * the {@link PlayerAnimusStorage}, and the unit-id ↔ active-vanilla-entity
 * mapping used by the scheduling layer ({@code AssignTaskPayload}).
 *
 * <h2>Lifecycle</h2>
 * Held by {@link AnimusSavedData}, which serialises the persistent half
 * (units + storage) into the overworld's
 * {@link net.minecraft.world.level.storage.SavedDataStorage}. Survives
 * server restart. Mutations to persistent fields must call
 * {@link #markDirty} so the SavedData layer flushes the change.
 *
 * <h2>Persistent vs runtime fields</h2>
 * <ul>
 *   <li><strong>Persistent</strong>: {@link #units} (name, model_key, alive),
 *       {@link #storage} contents.</li>
 *   <li><strong>Runtime only</strong>: {@link #activeVanillaId},
 *       {@link #vanillaToUnitId} — rebuilt at summon time, not saved.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 * Server main thread only. All mutations come from server-thread handlers
 * (network payloads, tick callbacks). No synchronisation.
 */
public final class PlayerAnimusData {

    public static final int SLOT_COUNT = 6;

    private final UUID playerUuid;
    private final UnitConfig[] units = new UnitConfig[SLOT_COUNT];
    private final PlayerAnimusStorage storage = new PlayerAnimusStorage();
    /** Index = unit_id - 1; value = vanilla {@code entity.getId()} or -1 when idle. */
    private final int[] activeVanillaId = new int[SLOT_COUNT];
    private final Map<Integer, Integer> vanillaToUnitId = new HashMap<>();

    /** Set by {@link AnimusSavedData} when this instance is owned by it. */
    private AnimusSavedData parent;

    PlayerAnimusData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        for (int i = 0; i < SLOT_COUNT; i++) {
            units[i] = UnitConfig.defaultFor(i + 1);
            activeVanillaId[i] = -1;
        }
    }

    /** Get-or-lazily-create via {@link AnimusSavedData}. Server-side only. */
    public static PlayerAnimusData of(ServerPlayer player) {
        ServerLevel sl = (ServerLevel) player.level();
        return AnimusSavedData.get(sl.getServer()).getOrCreate(player.getUUID());
    }

    /** Lookup without creation — accepts the server to find the correct world's data. */
    public static Optional<PlayerAnimusData> lookup(MinecraftServer server, UUID playerUuid) {
        return AnimusSavedData.get(server).lookup(playerUuid);
    }

    /**
     * Reverse-lookup the owning player for a vanilla entity id. Useful when
     * the server gets a callback on an Animus and needs to find "whose
     * storage / which unit_id is this?". Linear over loaded players.
     */
    public static Optional<UnitKey> findUnitFor(MinecraftServer server, int vanillaEntityId) {
        return AnimusSavedData.get(server).findUnitFor(vanillaEntityId);
    }

    /** Convenience: get the server from a server-side entity, then look up. */
    public static Optional<UnitKey> findUnitFor(Entity contextEntity) {
        if (!(contextEntity.level() instanceof ServerLevel sl)) return Optional.empty();
        return findUnitFor(sl.getServer(), contextEntity.getId());
    }

    // ---- per-player API ----

    public UUID playerUuid() { return playerUuid; }
    public PlayerAnimusStorage storage() { return storage; }

    /** Read-only view of the 6 configs in slot order. */
    public UnitConfig[] units() { return units; }

    /** Returns the config for unit_id (1..6) or throws if out of range. */
    public UnitConfig unit(int unitId) {
        if (unitId < 1 || unitId > SLOT_COUNT) {
            throw new IllegalArgumentException("unit_id must be 1.." + SLOT_COUNT + ", got " + unitId);
        }
        return units[unitId - 1];
    }

    /** True if unit slot has an active spawned entity in the world. */
    public boolean isActive(int unitId) {
        return activeVanillaId[unitId - 1] != -1;
    }

    /** Vanilla {@code entity.getId()} of the spawned Animus, or -1 if idle. */
    public int activeVanillaId(int unitId) {
        return activeVanillaId[unitId - 1];
    }

    /**
     * Register the mapping between a freshly-spawned Animus and a unit slot.
     * Runtime state — does NOT trigger dirty.
     */
    public void bindActive(int unitId, int vanillaEntityId) {
        if (activeVanillaId[unitId - 1] != -1) {
            Constants.LOG.warn("[animus-data] bindActive player={} unit={} replaces previous vanilla id {}",
                    playerUuid, unitId, activeVanillaId[unitId - 1]);
            vanillaToUnitId.remove(activeVanillaId[unitId - 1]);
        }
        activeVanillaId[unitId - 1] = vanillaEntityId;
        vanillaToUnitId.put(vanillaEntityId, unitId);
    }

    /**
     * Clear the active mapping for a unit slot. Runtime state — does NOT
     * trigger dirty. Idempotent.
     */
    public void unbindActive(int unitId) {
        int vanillaId = activeVanillaId[unitId - 1];
        if (vanillaId == -1) return;
        vanillaToUnitId.remove(vanillaId);
        activeVanillaId[unitId - 1] = -1;
    }

    /** Used by {@link AnimusSavedData#findUnitFor} reverse lookup. */
    int findActiveUnitForVanillaId(int vanillaEntityId) {
        Integer unitId = vanillaToUnitId.get(vanillaEntityId);
        return unitId == null ? -1 : unitId;
    }

    // ---- persistence wiring ----

    /** Called by {@link AnimusSavedData} when this instance becomes part of saved state. */
    void bindParent(AnimusSavedData parent) {
        this.parent = parent;
    }

    /**
     * Mark the persistent state dirty so the world-save loop flushes
     * to disk. Call after any mutation to {@link #units} fields or
     * {@link #storage} contents.
     */
    public void markDirty() {
        if (parent != null) parent.setDirty();
    }

    /** Pair of (player_uuid, unit_id) — the canonical address of an Animus slot. */
    public record UnitKey(UUID playerUuid, int unitId) {}
}
