package com.dwinovo.tulpa.platform;

import com.dwinovo.tulpa.platform.services.INetworkChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fabric implementation of {@link INetworkChannel} for 1.20.4 (pre-{@code CustomPacketPayload}).
 * Channels are keyed by {@link ResourceLocation} and serialised through
 * {@link FriendlyByteBuf}: the server-side receiver registration lives in
 * {@code ServerPlayNetworking} (available everywhere); the client-side receiver is
 * registered through {@code ClientPlayNetworking}, guarded behind an environment
 * check so common code never class-loads the client-only type on a server.
 *
 * <p>The send side maps a payload instance back to its channel id + encoder via the
 * {@link #senders} registry, so {@code TulpaNetwork} can keep calling
 * {@code sendToServer(payload)} / {@code sendToPlayer(player, payload)} ergonomically.
 */
public final class FabricNetworkChannel implements INetworkChannel {

    /** payload class → (channel id, encoder), populated by both register* directions. */
    private record Sender(ResourceLocation id, BiConsumer<Object, FriendlyByteBuf> encoder) {}

    private final Map<Class<?>, Sender> senders = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private <T> void rememberSender(ResourceLocation id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder) {
        senders.put(type, new Sender(id, (BiConsumer<Object, FriendlyByteBuf>) encoder));
    }

    @Override
    public <T> void registerClientToServer(
            ResourceLocation id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, ServerPlayer> handler) {
        rememberSender(id, type, encoder);
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, h, buf, responseSender) -> {
            T payload = decoder.apply(buf);   // decode on the netty thread (buf is freed after the callback)
            server.execute(() -> handler.accept(payload, player));
        });
    }

    @Override
    public <T> void registerServerToClient(
            ResourceLocation id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
            Consumer<T> handler) {
        rememberSender(id, type, encoder);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerClientReceiverImpl(id, decoder, handler);
        }
    }

    /** Isolated for lazy class-load — runs only on a client environment. */
    private static <T> void registerClientReceiverImpl(
            ResourceLocation id, Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                id, (client, h, buf, responseSender) -> {
                    T payload = decoder.apply(buf);
                    client.execute(() -> handler.accept(payload));
                });
    }

    @Override
    public void sendToServer(Object payload) {
        Sender sender = lookup(payload);
        FriendlyByteBuf buf = PacketByteBufs.create();
        sender.encoder().accept(payload, buf);
        // Lazy class-load: ClientPlayNetworking is client-only. Server JVM never reaches this.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(sender.id(), buf);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object payload) {
        Sender sender = lookup(payload);
        FriendlyByteBuf buf = PacketByteBufs.create();
        sender.encoder().accept(payload, buf);
        ServerPlayNetworking.send(player, sender.id(), buf);
    }

    private Sender lookup(Object payload) {
        Sender sender = senders.get(payload.getClass());
        if (sender == null) {
            throw new IllegalStateException("Unregistered payload type: " + payload.getClass().getName());
        }
        return sender;
    }
}
