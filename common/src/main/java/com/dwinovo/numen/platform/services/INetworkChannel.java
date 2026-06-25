package com.dwinovo.numen.platform.services;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cross-loader networking surface. Wraps Forge's {@code SimpleChannel} and
 * Fabric's {@code ServerPlayNetworking} / {@code ClientPlayNetworking} so feature
 * code in {@code common} can declare a payload, its (de)serialiser, and its handler
 * once and have it work on both loaders.
 *
 * <h2>Pre-1.20.5 model</h2>
 * 1.20.4 predates vanilla's {@code CustomPacketPayload} / {@code StreamCodec}
 * overhaul, so payloads are plain records identified by a {@link ResourceLocation}
 * and serialised through {@link FriendlyByteBuf}: each payload exposes a public
 * {@code ID}, an instance {@code write(FriendlyByteBuf)}, and a static
 * {@code read(FriendlyByteBuf)}. The encoder / decoder / handler are wired here.
 *
 * <h2>Payload lifecycle (C→S)</h2>
 * <ol>
 *   <li>Define a record with {@code ID}, {@code write}, {@code read}.</li>
 *   <li>Call {@link #registerClientToServer} once from {@code NumenNetwork.register}.</li>
 *   <li>Client sends via {@link #sendToServer}; handler runs on the server main thread.</li>
 * </ol>
 *
 * <h2>Payload lifecycle (S→C)</h2>
 * <ol>
 *   <li>Same payload definition.</li>
 *   <li>Call {@link #registerServerToClient} once from {@code NumenNetwork.register}.</li>
 *   <li>Server sends via {@link #sendToPlayer}; handler runs on the client main thread.</li>
 * </ol>
 *
 * <h2>Threading guarantee</h2>
 * Handlers (both directions) are dispatched on the receiving side's main thread.
 * Common code doesn't need to schedule.
 */
public interface INetworkChannel {

    /**
     * Register a payload the client can send to the server.
     *
     * @param id      stable channel identifier (also the payload's {@code ID})
     * @param type    payload class — the send side maps an instance back to its registration
     * @param encoder writes the payload into the buffer
     * @param decoder reconstructs the payload from the buffer
     * @param handler invoked on the server main thread for each received payload
     */
    <T> void registerClientToServer(
            ResourceLocation id,
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, ServerPlayer> handler);

    /**
     * Register a payload the server can send to clients. The {@code handler}
     * runs only on the client side; on a dedicated server the type is still
     * registered (so the server can serialise outbound packets), but the
     * client-side handler hookup is guarded behind a side check, so the handler
     * lambda may reference client-only classes safely (they are lazy-loaded).
     *
     * @param handler invoked on the client main thread for each received payload
     */
    <T> void registerServerToClient(
            ResourceLocation id,
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            Consumer<T> handler);

    /**
     * Send a registered payload from the client to the server. Client-only —
     * calling on the dedicated server throws.
     */
    void sendToServer(Object payload);

    /**
     * Send a registered payload from the server to a specific player.
     * Server-only — call from server-thread code.
     */
    void sendToPlayer(ServerPlayer player, Object payload);
}
