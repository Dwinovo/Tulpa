package com.dwinovo.tulpa.network;

import com.dwinovo.tulpa.network.payload.TulpaDeathPayload;
import com.dwinovo.tulpa.network.payload.TulpaLocationsPayload;
import com.dwinovo.tulpa.network.payload.LocateTulpaPayload;
import com.dwinovo.tulpa.network.payload.CancelTasksPayload;
import com.dwinovo.tulpa.network.payload.ClientUiActionPayload;
import com.dwinovo.tulpa.network.payload.CompanionListPayload;
import com.dwinovo.tulpa.network.payload.ExecuteToolPayload;
import com.dwinovo.tulpa.network.payload.PathVizPayload;
import com.dwinovo.tulpa.network.payload.TaskResultPayload;
import com.dwinovo.tulpa.platform.Services;

/**
 * Central registration hub for every networking payload the mod declares. Each
 * loader's mod-init code calls {@link #register} exactly once during startup;
 * the {@link Services#NETWORK} platform implementation handles the loader-specific
 * timing (Forge {@code SimpleChannel} / Fabric {@code PayloadType}).
 *
 * <h2>Adding a new payload</h2>
 * <ol>
 *   <li>Define a record under {@code com.dwinovo.tulpa.network.payload} with a
 *       public {@code ID}, an instance {@code write(FriendlyByteBuf)}, and a
 *       static {@code read(FriendlyByteBuf)}.</li>
 *   <li>Add one {@code registerClientToServer(...)} or
 *       {@code registerServerToClient(...)} call here.</li>
 * </ol>
 */
public final class TulpaNetwork {

    private TulpaNetwork() {}

    public static void register() {
        // C→S: the client-side LLM emitted a tool_call; execute on the owner's Tulpa.
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

        // S→C: an Tulpa body died; suspend the owner's agent loop (resolves the in-flight
        // tool call with the death cause). Recoverable — see TulpaRespawnPayload.
        Services.NETWORK.registerServerToClient(
                TulpaDeathPayload.ID, TulpaDeathPayload.class,
                TulpaDeathPayload::write, TulpaDeathPayload::read, TulpaDeathPayload::handle);

        // S→C: the dead companion has respawned at its owner; resume the suspended loop.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.tulpa.network.payload.TulpaRespawnPayload.ID,
                com.dwinovo.tulpa.network.payload.TulpaRespawnPayload.class,
                com.dwinovo.tulpa.network.payload.TulpaRespawnPayload::write,
                com.dwinovo.tulpa.network.payload.TulpaRespawnPayload::read,
                com.dwinovo.tulpa.network.payload.TulpaRespawnPayload::handle);

        // S→C: a generic async world event (dimension change, hazard, …) for a companion's brain.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.tulpa.network.payload.TulpaEventPayload.ID,
                com.dwinovo.tulpa.network.payload.TulpaEventPayload.class,
                com.dwinovo.tulpa.network.payload.TulpaEventPayload::write,
                com.dwinovo.tulpa.network.payload.TulpaEventPayload::read,
                com.dwinovo.tulpa.network.payload.TulpaEventPayload::handle);

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

        // S→C: server `/tulpa` verbs that must act on the caller's own client
        // (open settings GUI / reset conversations).
        Services.NETWORK.registerServerToClient(
                ClientUiActionPayload.ID, ClientUiActionPayload.class,
                ClientUiActionPayload::write, ClientUiActionPayload::read, ClientUiActionPayload::handle);

        // C→S: roster panel asks where its (possibly far / cross-dimension) pets are.
        Services.NETWORK.registerClientToServer(
                LocateTulpaPayload.ID, LocateTulpaPayload.class,
                LocateTulpaPayload::write, LocateTulpaPayload::read, LocateTulpaPayload::handle);

        // S→C: locate answers — position/dimension/HP snapshots per pet.
        Services.NETWORK.registerServerToClient(
                TulpaLocationsPayload.ID, TulpaLocationsPayload.class,
                TulpaLocationsPayload::write, TulpaLocationsPayload::read, TulpaLocationsPayload::handle);

        // C→S: the Items tab asks for a companion's backpack (not client-synced).
        Services.NETWORK.registerClientToServer(
                com.dwinovo.tulpa.network.payload.RequestInventoryPayload.ID,
                com.dwinovo.tulpa.network.payload.RequestInventoryPayload.class,
                com.dwinovo.tulpa.network.payload.RequestInventoryPayload::write,
                com.dwinovo.tulpa.network.payload.RequestInventoryPayload::read,
                com.dwinovo.tulpa.network.payload.RequestInventoryPayload::handle);

        // S→C: the requested backpack contents.
        Services.NETWORK.registerServerToClient(
                com.dwinovo.tulpa.network.payload.TulpaInventoryPayload.ID,
                com.dwinovo.tulpa.network.payload.TulpaInventoryPayload.class,
                com.dwinovo.tulpa.network.payload.TulpaInventoryPayload::write,
                com.dwinovo.tulpa.network.payload.TulpaInventoryPayload::read,
                com.dwinovo.tulpa.network.payload.TulpaInventoryPayload::handle);

        // C→S: the panel's "+" button asks to summon a companion by name.
        Services.NETWORK.registerClientToServer(
                com.dwinovo.tulpa.network.payload.SummonRequestPayload.ID,
                com.dwinovo.tulpa.network.payload.SummonRequestPayload.class,
                com.dwinovo.tulpa.network.payload.SummonRequestPayload::write,
                com.dwinovo.tulpa.network.payload.SummonRequestPayload::read,
                com.dwinovo.tulpa.network.payload.SummonRequestPayload::handle);

        // C→S: the rail ✕ → confirm asks to permanently delete a companion (drops its inventory first).
        Services.NETWORK.registerClientToServer(
                com.dwinovo.tulpa.network.payload.DismissRequestPayload.ID,
                com.dwinovo.tulpa.network.payload.DismissRequestPayload.class,
                com.dwinovo.tulpa.network.payload.DismissRequestPayload::write,
                com.dwinovo.tulpa.network.payload.DismissRequestPayload::read,
                com.dwinovo.tulpa.network.payload.DismissRequestPayload::handle);
    }
}
