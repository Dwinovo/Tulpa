package com.dwinovo.numen.network.payload;

import com.dwinovo.numen.Constants;
import com.dwinovo.numen.entity.Companions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → Server: the owner asked to summon a companion by name from the panel's
 * "+" button. Mirrors the {@code /numen player summon} command — summon is
 * idempotent per (owner, name), so re-summoning an existing name just wakes it.
 */
public record SummonRequestPayload(String name) {

    public static final int MAX_NAME = 32;

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "summon_request");

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, MAX_NAME);
    }

    public static SummonRequestPayload read(FriendlyByteBuf buf) {
        return new SummonRequestPayload(buf.readUtf(MAX_NAME));
    }

    /** Server main thread. */
    public static void handle(SummonRequestPayload p, ServerPlayer owner) {
        String name = p.name() == null ? "" : p.name().trim();
        if (name.isEmpty() || name.length() > MAX_NAME) return;
        ServerLevel level = (ServerLevel) owner.level();
        Companions.summon(level.getServer(), owner.getUUID(), name, level, owner.position());
        Companions.syncRosterToOwner(level.getServer(), owner);   // push the new roster to the owner
    }
}
