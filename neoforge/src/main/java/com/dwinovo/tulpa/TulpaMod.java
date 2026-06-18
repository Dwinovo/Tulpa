package com.dwinovo.tulpa;

import com.dwinovo.tulpa.network.TulpaNetwork;
import com.dwinovo.tulpa.platform.NeoForgeTulpaConfig;
import com.dwinovo.tulpa.platform.NeoForgeNetworkChannel;
import com.dwinovo.tulpa.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Constants.MOD_ID)
public class TulpaMod {

    public TulpaMod(IEventBus eventBus, ModContainer container) {
        eventBus.addListener(TulpaMod::registerPayloads);

        // Register the TOML config spec — NeoForge handles file creation +
        // hot-reload from this point on. SPEC is built lazily in the
        // NeoForgeTulpaConfig static initialiser so referencing it here is
        // safe (no I/O happens until the world loads).
        container.registerConfig(ModConfig.Type.COMMON, NeoForgeTulpaConfig.SPEC);

        // Queue payload registrations into NeoForgeNetworkChannel; the queue
        // flushes when RegisterPayloadHandlersEvent fires (see below).
        TulpaNetwork.register();

        // Per-tick server work: long-range scans + companion task dispatch.
        NeoForge.EVENT_BUS.addListener(TulpaMod::onServerTickPost);
        // Dev: /tulpa_summon — create a companion fake player at the caller.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                com.dwinovo.tulpa.entity.TulpaCommands.register(e.getDispatcher()));
        // When an owner logs in, bring their dormant companions back.
        NeoForge.EVENT_BUS.addListener(TulpaMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(TulpaMod::onPlayerChangedDimension);
        // Release pathfinding chunk-ref snapshots when the server stops (don't pin an old world).
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
                com.dwinovo.tulpa.pathing.cache.PathCaches.dropAll());

        CommonClass.init();
        Constants.LOG.info("Tulpa mod initialised on NeoForge.");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (Services.NETWORK instanceof NeoForgeNetworkChannel ch) {
            ch.flushPending(event);
        }
    }

    private static void onServerTickPost(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.dwinovo.tulpa.task.tasks.ScanBlocksJob.tick(event.getServer());
        com.dwinovo.tulpa.task.CompanionTickDispatcher.tick(event.getServer());
        // Snapshot loaded chunks near companions each tick, for the off-thread planner to read live.
        com.dwinovo.tulpa.pathing.cache.PathCaches.serverTick(event.getServer());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player instanceof com.dwinovo.tulpa.entity.TulpaPlayer) return;  // not the companion itself
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            com.dwinovo.tulpa.entity.Companions.respawnAllOwnedBy(server, player.getUUID());
            com.dwinovo.tulpa.entity.Companions.syncRosterToOwner(server, player);
        }
    }

    /** The companion crossed a portal on its own — tell its brain (ambient world event). */
    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof com.dwinovo.tulpa.entity.TulpaPlayer ap) {
            com.dwinovo.tulpa.entity.Companions.onDimensionChanged(ap);
        }
    }
}
