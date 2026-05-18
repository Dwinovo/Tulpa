package com.dwinovo.animus.network.payload;

import com.dwinovo.animus.Constants;
import com.dwinovo.animus.data.PlayerAnimusData;
import com.dwinovo.animus.data.PlayerAnimusStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Client → Server: open the player's virtual storage as a standard
 * 9 × 6 chest menu. Server uses vanilla {@code player.openMenu} which
 * handles the menu-sync packet to the client side; the client renders the
 * stock {@code ChestScreen} backed by the synced slots. No custom menu
 * type or screen registration needed — we're reusing vanilla's
 * {@code MenuType.GENERIC_9x6} pipeline by constructing a {@link ChestMenu}
 * via {@link ChestMenu#sixRows(int, net.minecraft.world.entity.player.Inventory, net.minecraft.world.Container)}.
 *
 * <p>This is the lazy/clean approach. A custom {@code AnimusStorageMenu}
 * subclass would let us add quality-of-life (slot tooltips, drag policy,
 * shift-click partitioning), but for MVP the chest UX is fine.
 */
public record OpenStorageMenuPayload() implements CustomPacketPayload {

    /** Singleton instance — payload has no data, no per-call allocation needed. */
    private static final OpenStorageMenuPayload INSTANCE = new OpenStorageMenuPayload();

    public static final Type<OpenStorageMenuPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "open_storage_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStorageMenuPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    /** Use this instead of constructing a new instance for sends. */
    public static OpenStorageMenuPayload instance() { return INSTANCE; }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenStorageMenuPayload p, ServerPlayer player) {
        PlayerAnimusData data = PlayerAnimusData.of(player);
        PlayerAnimusStorage storage = data.storage();
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p2) -> ChestMenu.sixRows(id, inv, storage),
                Component.literal("Animus Storage")));
        Constants.LOG.debug("[animus-net] open_storage_menu for {} ({} non-empty slots)",
                player.getName().getString(), storage.snapshotNonEmpty().size());
    }
}
