package com.dwinovo.tulpa;

import com.dwinovo.tulpa.network.TulpaNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

public class TulpaMod implements ModInitializer {

    @Override
    public void onInitialize() {
        TulpaNetwork.register();

        // Dev: /tulpa_summon — create a companion fake player at the caller.
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, env) ->
                        com.dwinovo.tulpa.entity.TulpaCommands.register(dispatcher));

        // When an owner logs in, bring their dormant companions back.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    ServerPlayer player = handler.getPlayer();
                    if (player instanceof com.dwinovo.tulpa.entity.TulpaPlayer) return;  // not the companion itself
                    com.dwinovo.tulpa.entity.Companions.respawnAllOwnedBy(server, player.getUUID());
                    com.dwinovo.tulpa.entity.Companions.syncRosterToOwner(server, player);
                });

        // The companion crossed a portal on its own — tell its brain (ambient world event).
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> {
                    if (player instanceof com.dwinovo.tulpa.entity.TulpaPlayer ap) {
                        com.dwinovo.tulpa.entity.Companions.onDimensionChanged(ap);
                    }
                });

        // Advance budget-sliced long-range block scans.
        ServerTickEvents.END_SERVER_TICK.register(
                com.dwinovo.tulpa.task.tasks.ScanBlocksJob::tick);
        // Drive companion player-body tasks (move_to / auto_mine) each tick.
        ServerTickEvents.END_SERVER_TICK.register(
                com.dwinovo.tulpa.task.CompanionTickDispatcher::tick);
        // Snapshot loaded chunks near companions each tick, for the off-thread planner to read live.
        ServerTickEvents.END_SERVER_TICK.register(
                com.dwinovo.tulpa.pathing.cache.PathCaches::serverTick);
        // Release those chunk references when the server stops (don't pin an old world's chunks).
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> com.dwinovo.tulpa.pathing.cache.PathCaches.dropAll());

        CommonClass.init();
        Constants.LOG.info("Tulpa mod initialised on Fabric.");
    }
}
