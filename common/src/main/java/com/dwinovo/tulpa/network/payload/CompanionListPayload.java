package com.dwinovo.tulpa.network.payload;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.client.agent.TulpaRoster;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client: the roster of companions this player owns (UUID + name).
 * The companion body is a fake {@code ServerPlayer}, so the client can't be
 * "enrolled" by right-clicking a Mob any more — the server is the authority on
 * which companions exist. Pushed on owner login (after their dormant companions
 * respawn) and right after a fresh summon, so the client's {@link TulpaRoster}
 * panel always reflects the truth without the player having to seek each body
 * out physically.
 */
public record CompanionListPayload(List<Entry> companions) {

    /** Cap defends against absurd input; nobody owns hundreds of companions. */
    public static final int MAX = 64;

    /** One companion's roster line. */
    public record Entry(UUID uuid, String name) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(uuid);
            buf.writeUtf(name, 256);
        }

        static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readUUID(), buf.readUtf(256));
        }
    }

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "companion_list");

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(companions, (b, e) -> e.write(b));
    }

    public static CompanionListPayload read(FriendlyByteBuf buf) {
        return new CompanionListPayload(buf.readCollection(ArrayList::new, Entry::read));
    }

    /** Client-side handler. Runs on the client main thread (network layer arranges that). */
    public static void handle(CompanionListPayload p) {
        java.util.List<TulpaRoster.Entry> snapshot = new java.util.ArrayList<>();
        for (Entry e : p.companions()) {
            snapshot.add(new TulpaRoster.Entry(e.uuid(), e.name()));
        }
        TulpaRoster.instance().replaceAll(snapshot);
    }
}
