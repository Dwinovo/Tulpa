package com.dwinovo.numen.network;

import com.dwinovo.numen.network.payload.NumenDeathPayload;
import com.dwinovo.numen.network.payload.NumenLocationsPayload;
import com.dwinovo.numen.network.payload.LocateNumenPayload;
import com.dwinovo.numen.network.payload.CancelTasksPayload;
import com.dwinovo.numen.network.payload.ClientUiActionPayload;
import com.dwinovo.numen.network.payload.CompanionListPayload;
import com.dwinovo.numen.network.payload.ExecuteToolPayload;
import com.dwinovo.numen.network.payload.PathVizPayload;
import com.dwinovo.numen.network.payload.TaskResultPayload;
import com.dwinovo.numen.platform.Services;

/**
 * Central registration hub for every networking payload the mod declares. Each
 * loader's mod-init code calls {@link #register} exactly once during startup;
 * the {@link Services#NETWORK} platform implementation handles the loader-specific
 * timing (Forge {@code SimpleChannel} / Fabric {@code PayloadType}).
 *
 * <h2>Adding a new payload</h2>
 * <ol>
 *   <li>Define a record under {@code com.dwinovo.numen.network.payload} with a
 *       public {@code ID}, an instance {@code write(FriendlyByteBuf)}, and a
 *       static {@code read(FriendlyByteBuf)}.</li>
 *   <li>Add one {@code registerClientToServer(...)} or
 *       {@code registerServerToClient(...)} call here.</li>
 * </ol>
 */
public final class NumenNetwork {

    private NumenNetwork() {}

    public static void register() {
        // C→S: the client-side LLM emitted a tool_call; execute on the owner's Numen.
        Services.NETWORK.registerClientToServer(
                ExecuteToolPayload.ID, ExecuteToolPayload.class,
                ExecuteToolPayload::write, ExecuteToolPayload::read, ExecuteToolPayload::handle);

        // C→S: owner pressed Stop — cancel the running + queued tasks (body stop).
        Services.NETWORK.registerClientToServer(
                CancelTasksPayload.ID, CancelTasksPayload.class,
                CancelTasksPayload::write, CancelTasksPayload::read, CancelTasksPayload::handle);

        // S→C: tool execution finished; ship the result back to the owner.
        Services.NETWORK.registerServerToClient(
                TaskResultPayload.ID, TaskResultPayload.class,
                TaskResultPayload::write, TaskResultPayload::read, TaskResultPayload::handle);

        // S→C: an Numen body died; suspend the owner's agent loop (resolves the in-flight
        // tool call with the death cause). Recoverable — see NumenRespawnPayload.
        Services.NETWORK.registerServerToClient(
                NumenDeathPayload.ID, NumenDeathPayload.class,
                NumenDeathPayload::write, NumenDeathPayload::read, NumenDeathPayload::handle);

        // S→C: the dead companion has respawned at its owner; resume the suspended loop.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.numen.network.payload.NumenRespawnPayload.ID,
                com.dwinovo.numen.network.payload.NumenRespawnPayload.class,
                com.dwinovo.numen.network.payload.NumenRespawnPayload::write,
                com.dwinovo.numen.network.payload.NumenRespawnPayload::read,
                com.dwinovo.numen.network.payload.NumenRespawnPayload::handle);

        // S→C: a generic async world event (dimension change, hazard, …) for a companion's brain.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.numen.network.payload.NumenEventPayload.ID,
                com.dwinovo.numen.network.payload.NumenEventPayload.class,
                com.dwinovo.numen.network.payload.NumenEventPayload::write,
                com.dwinovo.numen.network.payload.NumenEventPayload::read,
                com.dwinovo.numen.network.payload.NumenEventPayload::handle);

        // S→C: the owner's companion roster (UUID + name), pushed on login + summon
        // so the client panel knows which fake players are its companions.
        Services.NETWORK.registerServerToClient(
                CompanionListPayload.ID, CompanionListPayload.class,
                CompanionListPayload::write, CompanionListPayload::read, CompanionListPayload::handle);

        // S→C: the companion's current pathfinding plan, for the in-world path
        // overlay (Baritone PathRenderer, ported to our server-authored path).
        Services.NETWORK.registerServerToClient(
                PathVizPayload.ID, PathVizPayload.class,
                PathVizPayload::write, PathVizPayload::read, PathVizPayload::handle);

        // S→C: server `/numen` verbs that must act on the caller's own client
        // (open settings GUI / reset conversations).
        Services.NETWORK.registerServerToClient(
                ClientUiActionPayload.ID, ClientUiActionPayload.class,
                ClientUiActionPayload::write, ClientUiActionPayload::read, ClientUiActionPayload::handle);

        // C→S: roster panel asks where its (possibly far / cross-dimension) pets are.
        Services.NETWORK.registerClientToServer(
                LocateNumenPayload.ID, LocateNumenPayload.class,
                LocateNumenPayload::write, LocateNumenPayload::read, LocateNumenPayload::handle);

        // S→C: locate answers — position/dimension/HP snapshots per pet.
        Services.NETWORK.registerServerToClient(
                NumenLocationsPayload.ID, NumenLocationsPayload.class,
                NumenLocationsPayload::write, NumenLocationsPayload::read, NumenLocationsPayload::handle);

        // C→S: the Items tab asks for a companion's backpack (not client-synced).
        Services.NETWORK.registerClientToServer(
                com.dwinovo.numen.network.payload.RequestInventoryPayload.ID,
                com.dwinovo.numen.network.payload.RequestInventoryPayload.class,
                com.dwinovo.numen.network.payload.RequestInventoryPayload::write,
                com.dwinovo.numen.network.payload.RequestInventoryPayload::read,
                com.dwinovo.numen.network.payload.RequestInventoryPayload::handle);

        // S→C: the requested backpack contents.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.numen.network.payload.NumenInventoryPayload.ID,
                com.dwinovo.numen.network.payload.NumenInventoryPayload.class,
                com.dwinovo.numen.network.payload.NumenInventoryPayload::write,
                com.dwinovo.numen.network.payload.NumenInventoryPayload::read,
                com.dwinovo.numen.network.payload.NumenInventoryPayload::handle);

        // C→S: the panel's "+" button asks to summon a companion by name.
        Services.NETWORK.registerClientToServer(
                com.dwinovo.numen.network.payload.SummonRequestPayload.ID,
                com.dwinovo.numen.network.payload.SummonRequestPayload.class,
                com.dwinovo.numen.network.payload.SummonRequestPayload::write,
                com.dwinovo.numen.network.payload.SummonRequestPayload::read,
                com.dwinovo.numen.network.payload.SummonRequestPayload::handle);

        // C→S: the rail ✕ → confirm asks to permanently delete a companion (drops its inventory first).
        Services.NETWORK.registerClientToServer(
                com.dwinovo.numen.network.payload.DismissRequestPayload.ID,
                com.dwinovo.numen.network.payload.DismissRequestPayload.class,
                com.dwinovo.numen.network.payload.DismissRequestPayload::write,
                com.dwinovo.numen.network.payload.DismissRequestPayload::read,
                com.dwinovo.numen.network.payload.DismissRequestPayload::handle);
    }
}
