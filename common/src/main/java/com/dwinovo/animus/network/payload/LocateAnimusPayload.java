package com.dwinovo.animus.network.payload;

import com.dwinovo.animus.Constants;
import com.dwinovo.animus.entity.AnimusEntity;
import com.dwinovo.animus.platform.Services;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client → Server: "where are these companions of mine right now?" Sent by the
 * roster panel (and the chat vitals strip) for entities the client can't see —
 * a working companion may be ticking in chunk-ticket-loaded terrain thousands
 * of blocks away, or in another dimension.
 *
 * <p>Answered with one {@link AnimusLocationsPayload} carrying a snapshot per
 * requested UUID. Ownership is enforced per entity: UUIDs that don't resolve
 * to a pet owned by the sender come back {@code found=false} (no oracle for
 * other players' pets).
 */
public record LocateAnimusPayload(List<UUID> entityUuids) implements CustomPacketPayload {

    /** Roster panels are small; cap defends against garbage input. */
    public static final int MAX_UUIDS = 16;

    public static final Type<LocateAnimusPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "locate_animus"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocateAnimusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_UUIDS)),
                    LocateAnimusPayload::entityUuids,
                    LocateAnimusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Handler invoked on the server main thread. */
    public static void handle(LocateAnimusPayload p, ServerPlayer player) {
        List<AnimusLocationsPayload.Snapshot> out = new ArrayList<>(p.entityUuids().size());
        for (UUID uuid : p.entityUuids()) {
            AnimusEntity animus = AnimusEntity.findByUuid(player.level().getServer(), uuid);
            if (animus != null && animus.isOwnedByPlayer(player.getUUID())) {
                out.add(AnimusLocationsPayload.Snapshot.live(uuid,
                        animus.getX(), animus.getY(), animus.getZ(),
                        animus.level().dimension().identifier().toString(),
                        animus.getHealth(), animus.getMaxHealth()));
                continue;
            }
            // Unloaded (idle past the ticket linger, or a fresh session): fall
            // back to the persistent last-seen index so the roster can show
            // WHERE it sleeps instead of a useless "offline". Owner-gated —
            // the index carries ownership precisely because the entity can't.
            var lastSeen = com.dwinovo.animus.entity.AnimusLastSeen.find(
                    player.level().getServer(), uuid);
            if (animus == null && lastSeen != null && lastSeen.owner().equals(player.getUUID())) {
                var pos = lastSeen.pos();
                out.add(AnimusLocationsPayload.Snapshot.lastSeen(uuid,
                        pos.getX(), pos.getY(), pos.getZ(),
                        lastSeen.dimension().identifier().toString()));
                continue;
            }
            out.add(AnimusLocationsPayload.Snapshot.notFound(uuid));
        }
        Services.NETWORK.sendToPlayer(player, new AnimusLocationsPayload(out));
    }
}
