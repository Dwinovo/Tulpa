package com.dwinovo.animus.entity;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The last-seen entry is what revival and the roster's "asleep" line trust
 * while the pet itself is unloaded — codec drift here silently strands pets.
 */
class AnimusLastSeenEntryTest {

    @Test
    void entryRoundTripsThroughJson() {
        AnimusLastSeen.Entry entry = new AnimusLastSeen.Entry(
                ResourceKey.create(Registries.DIMENSION,
                        Identifier.fromNamespaceAndPath("minecraft", "the_nether")),
                new BlockPos(304, 70, 560),
                UUID.fromString("11111111-2222-3333-4444-555555555555"));

        var json = AnimusLastSeen.Entry.CODEC.encodeStart(JsonOps.INSTANCE, entry)
                .getOrThrow();
        AnimusLastSeen.Entry back = AnimusLastSeen.Entry.CODEC
                .parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(entry, back);
    }
}
