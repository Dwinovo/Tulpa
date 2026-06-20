package com.dwinovo.tulpa.network.payload;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.client.agent.AgentLoopRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → Client: an asynchronous WORLD EVENT for a companion's brain (dimension change, a hazard,
 * …) — the generic version of Claude Code's "channel notification" (an external event pushed into a
 * running session). The server detects the event (edge-triggered) and ships a ready-made {@code <event>}
 * XML string; the client loop queues it like an owner prompt and splices it in at a protocol-valid
 * boundary. {@code urgent} wakes an idle brain to react now; otherwise it rides along on the next
 * owner-driven turn (no extra LLM call). Death is its own bespoke freeze/thaw pair, not this.
 */
public record TulpaEventPayload(UUID entityUuid, String xml, boolean urgent) implements CustomPacketPayload {

    public static final Type<TulpaEventPayload> TYPE = new Type<>(
            new ResourceLocation(Constants.MOD_ID, "tulpa_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TulpaEventPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, TulpaEventPayload::entityUuid,
                    ByteBufCodecs.STRING_UTF8, TulpaEventPayload::xml,
                    ByteBufCodecs.BOOL, TulpaEventPayload::urgent,
                    TulpaEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that). */
    public static void handle(TulpaEventPayload p) {
        AgentLoopRegistry.get(p.entityUuid()).ifPresent(loop -> loop.injectEvent(p.xml(), p.urgent()));
    }
}
