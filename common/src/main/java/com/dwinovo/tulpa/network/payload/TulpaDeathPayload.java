package com.dwinovo.tulpa.network.payload;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.client.agent.AgentLoopRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → Client: an Tulpa body died for good. The owner's client-side
 * {@link com.dwinovo.tulpa.client.agent.EntityAgentLoop} must stop — a dead
 * companion has no body to act through, so it must never call the LLM again.
 *
 * <h2>Why a dedicated signal</h2>
 * Without it, a loop whose body just died keeps dispatching tools and gets back
 * misleading {@code entity not found} / {@code not loaded on client} errors,
 * which the LLM misreads as a transient sync glitch and retries until the loop
 * guard trips. This payload makes death explicit: the loop is hard-stopped and
 * disposed, so no further LLM turns happen.
 *
 * <h2>Death is recoverable (not disposed)</h2>
 * The companion respawns at its owner after a delay (see {@link TulpaRespawnPayload}), so the loop
 * is SUSPENDED, not disposed: {@code onEntityDied} resolves any in-flight tool calls with the death
 * cause (so the conversation stays valid and the brain learns WHY it stopped) and latches it idle.
 * {@code cause} is the vanilla death message ("X was slain by a zombie") for that tool result.
 */
public record TulpaDeathPayload(UUID entityUuid, String cause, long respawnDelayMs) {

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "tulpa_death");

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeUtf(cause);
        buf.writeVarLong(respawnDelayMs);
    }

    public static TulpaDeathPayload read(FriendlyByteBuf buf) {
        return new TulpaDeathPayload(buf.readUUID(), buf.readUtf(), buf.readVarLong());
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that). */
    public static void handle(TulpaDeathPayload p) {
        Constants.LOG.info("[tulpa-net] tulpa_death entity={} ({}) — suspending loop", p.entityUuid(), p.cause());
        AgentLoopRegistry.get(p.entityUuid()).ifPresent(loop -> loop.onEntityDied(p.cause()));
        // Keep it in the roster (marked dead) so the HUD / rail can show the respawn countdown;
        // it goes live again on TulpaRespawnPayload.
        com.dwinovo.tulpa.client.agent.ClientDeaths.markDead(
                p.entityUuid(), System.currentTimeMillis() + p.respawnDelayMs());
    }
}
