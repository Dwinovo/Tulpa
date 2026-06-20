package com.dwinovo.tulpa.network.payload;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.client.agent.AgentLoopRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → Client: a companion has respawned at the owner's side after dying — both the same-session
 * timed recovery and the at-login recovery for a companion that died while the owner was away. Carries
 * the {@code cause} so the brain always learns WHY it died, even when a logout wiped the client's
 * in-memory death state. The owner's {@link com.dwinovo.tulpa.client.agent.EntityAgentLoop} is created
 * if needed and reawakened with a death {@code <event>}.
 */
public record TulpaRespawnPayload(UUID entityUuid, String cause) {

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "tulpa_respawn");

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeUtf(cause);
    }

    public static TulpaRespawnPayload read(FriendlyByteBuf buf) {
        return new TulpaRespawnPayload(buf.readUUID(), buf.readUtf());
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that).
     *  getOrCreate (not get): after a logout the loop may not exist yet — make it so the death event lands. */
    public static void handle(TulpaRespawnPayload p) {
        Constants.LOG.info("[tulpa-net] tulpa_respawn entity={} ({}) — resuming loop", p.entityUuid(), p.cause());
        com.dwinovo.tulpa.client.agent.ClientDeaths.clear(p.entityUuid());
        AgentLoopRegistry.getOrCreate(p.entityUuid()).onRespawned(p.cause());
    }
}
