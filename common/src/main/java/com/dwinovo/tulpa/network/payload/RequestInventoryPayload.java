package com.dwinovo.tulpa.network.payload;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.entity.TulpaPlayer;
import com.dwinovo.tulpa.platform.Services;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client → Server: "show me this companion's backpack." Other players' full
 * inventory isn't synced to clients (only equipment is), so the read-only Items
 * tab fetches it on demand. Answered with one {@link TulpaInventoryPayload}.
 *
 * <p>Only the owner of a LOADED companion gets the contents; otherwise the reply
 * is {@code loaded=false} (asleep / not yours — no inventory oracle).
 */
public record RequestInventoryPayload(UUID uuid) implements CustomPacketPayload {

    /** The 36 main backpack slots (hotbar + storage); equipment is already client-synced. */
    public static final int MAIN_SLOTS = 36;

    public static final Type<RequestInventoryPayload> TYPE = new Type<>(
            new ResourceLocation(Constants.MOD_ID, "request_inventory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestInventoryPayload> STREAM_CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, RequestInventoryPayload::uuid,
                    RequestInventoryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server main thread. */
    public static void handle(RequestInventoryPayload p, ServerPlayer player) {
        TulpaPlayer tulpa = TulpaPlayer.findByUuid(player.level().getServer(), p.uuid());
        if (tulpa == null || !tulpa.isOwnedByPlayer(player.getUUID())) {
            Services.NETWORK.sendToPlayer(player,
                    new TulpaInventoryPayload(p.uuid(), false, List.of(), List.of(), 0, 0f));
            return;
        }
        Inventory inv = tulpa.getInventory();
        List<ItemStack> items = new ArrayList<>(MAIN_SLOTS);
        for (int i = 0; i < MAIN_SLOTS; i++) {
            items.add(inv.getItem(i).copy());
        }
        // The 2×2 crafting menu (vanilla InventoryMenu layout): slot 0 = result, slots 1-4 = grid.
        // Packed as [grid0, grid1, grid2, grid3, result] for the Items tab to mirror.
        List<ItemStack> craft = new ArrayList<>(5);
        for (int i = 1; i <= 4; i++) craft.add(tulpa.inventoryMenu.getSlot(i).getItem().copy());
        craft.add(tulpa.inventoryMenu.getSlot(0).getItem().copy());
        Services.NETWORK.sendToPlayer(player, new TulpaInventoryPayload(p.uuid(), true, items, craft,
                tulpa.getFoodData().getFoodLevel(), tulpa.getFoodData().getSaturationLevel()));
    }
}
