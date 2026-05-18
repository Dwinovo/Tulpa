package com.dwinovo.animus.data;

import com.dwinovo.animus.Constants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-world persistent root for every player's Animus data — storage
 * contents + 6 unit configs. Lives on the overworld's
 * {@link net.minecraft.world.level.storage.SavedDataStorage}, which means
 * one save per world folder (single-player) or one save per server world
 * (multi-player). Restored automatically on server boot via the
 * {@link #TYPE} codec.
 *
 * <h2>What persists</h2>
 * <ul>
 *   <li>{@link UnitConfig} for each of the 6 slots — name, model_key, alive</li>
 *   <li>{@link PlayerAnimusStorage} contents — full {@link ItemStack} list
 *       (preserves NBT, durability, enchants)</li>
 * </ul>
 *
 * <h2>What does NOT persist</h2>
 * <ul>
 *   <li>Active-slot bindings ({@code activeVanillaId}) — runtime only;
 *       in-world entities are summoned/discarded on demand, so the binding
 *       table is rebuilt from scratch each server boot.</li>
 *   <li>EntityAgent {@code ConvoState} — task-scoped, never persisted.</li>
 * </ul>
 *
 * <h2>Dirty marking</h2>
 * Any mutation that touches a persisted field — storage insert, unit name
 * change, model change, alive flip — must call {@link PlayerAnimusData#markDirty}.
 * Vanilla's save loop picks dirty SavedData up at next world save (~ every
 * 6000 ticks by default) and on server shutdown via {@code saveAndJoin}.
 */
public final class AnimusSavedData extends SavedData {

    /** Persistent shape of one player's data. Used purely for codec round-trip. */
    private record PlayerSnapshot(UUID uuid, List<UnitConfig> units, List<ItemStack> storage) {
        public static final Codec<PlayerSnapshot> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(PlayerSnapshot::uuid),
                UnitConfig.CODEC.listOf().fieldOf("units").forGetter(PlayerSnapshot::units),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("storage").forGetter(PlayerSnapshot::storage)
        ).apply(inst, PlayerSnapshot::new));
    }

    private static final Codec<AnimusSavedData> CODEC = PlayerSnapshot.CODEC.listOf()
            .xmap(AnimusSavedData::fromSnapshots, AnimusSavedData::toSnapshots);

    public static final SavedDataType<AnimusSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "player_data"),
            AnimusSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, PlayerAnimusData> byPlayer = new HashMap<>();

    /**
     * Server reference latched on the first {@link #get(MinecraftServer)}
     * call so {@link PlayerAnimusData#markDirty} can look up the owning
     * player and push a fresh {@link com.dwinovo.animus.network.payload.UnitsSnapshotPayload}.
     * Without this, chest-menu slot drags would persist to disk but the
     * client mirror (and therefore the {@code get_storage} tool the LLM
     * sees) would stay stale until the next mining event.
     */
    private MinecraftServer server;

    public AnimusSavedData() {}

    // ---- public API ----

    /** Get-or-load the singleton for this server (overworld data storage). */
    public static AnimusSavedData get(MinecraftServer server) {
        AnimusSavedData data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        if (data.server == null) data.server = server;
        return data;
    }

    public MinecraftServer server() { return server; }

    /**
     * Get-or-create a player's data block, wiring {@code this} as its
     * persistence parent so its {@code markDirty} propagates up.
     */
    public PlayerAnimusData getOrCreate(UUID playerUuid) {
        return byPlayer.computeIfAbsent(playerUuid, uuid -> {
            PlayerAnimusData created = new PlayerAnimusData(uuid);
            created.bindParent(this);
            return created;
        });
    }

    /** Lookup without creating — returns empty if the player has never been initialised. */
    public Optional<PlayerAnimusData> lookup(UUID playerUuid) {
        return Optional.ofNullable(byPlayer.get(playerUuid));
    }

    /**
     * Reverse-lookup the (player, unit) that owns a given vanilla entity
     * id. Linear over loaded players, but the typical online-player count
     * is small.
     */
    public Optional<PlayerAnimusData.UnitKey> findUnitFor(int vanillaEntityId) {
        for (var entry : byPlayer.entrySet()) {
            int unitId = entry.getValue().findActiveUnitForVanillaId(vanillaEntityId);
            if (unitId != -1) {
                return Optional.of(new PlayerAnimusData.UnitKey(entry.getKey(), unitId));
            }
        }
        return Optional.empty();
    }

    // ---- codec round-trip ----

    private static AnimusSavedData fromSnapshots(List<PlayerSnapshot> snapshots) {
        AnimusSavedData self = new AnimusSavedData();
        for (PlayerSnapshot s : snapshots) {
            PlayerAnimusData p = new PlayerAnimusData(s.uuid);
            // Populate BEFORE binding parent — storage.setItem triggers
            // setChanged → markDirty, which would falsely mark the
            // just-loaded SavedData dirty. With parent unbound, markDirty
            // is a no-op.
            for (UnitConfig cfg : s.units()) {
                if (cfg.unitId >= 1 && cfg.unitId <= PlayerAnimusData.SLOT_COUNT) {
                    p.units()[cfg.unitId - 1] = cfg;
                }
            }
            for (int i = 0; i < s.storage().size() && i < p.storage().getContainerSize(); i++) {
                p.storage().setItem(i, s.storage().get(i));
            }
            p.bindParent(self);  // bind AFTER population
            self.byPlayer.put(s.uuid, p);
        }
        return self;
    }

    private static List<PlayerSnapshot> toSnapshots(AnimusSavedData self) {
        List<PlayerSnapshot> out = new ArrayList<>(self.byPlayer.size());
        for (var entry : self.byPlayer.entrySet()) {
            PlayerAnimusData p = entry.getValue();
            List<UnitConfig> units = new ArrayList<>(p.units().length);
            for (UnitConfig u : p.units()) units.add(u);
            List<ItemStack> stacks = new ArrayList<>(p.storage().getContainerSize());
            for (int i = 0; i < p.storage().getContainerSize(); i++) {
                stacks.add(p.storage().getItem(i).copy());
            }
            out.add(new PlayerSnapshot(entry.getKey(), units, stacks));
        }
        return out;
    }
}
