package com.dwinovo.tulpa.network.payload;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.client.data.ClientTulpaInventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client: a companion's 36 main backpack slots, answering
 * {@link RequestInventoryPayload}. {@code loaded=false} means the body is asleep
 * in unloaded chunks (or not the requester's) — no contents. Dropped into
 * {@link ClientTulpaInventory} for the Items tab to render read-only.
 */
public record TulpaInventoryPayload(UUID uuid, boolean loaded, List<ItemStack> items,
                                    List<ItemStack> craft, int foodLevel, float saturation) {

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "tulpa_inventory");

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeBoolean(loaded);
        buf.writeCollection(items, FriendlyByteBuf::writeItem);
        buf.writeCollection(craft, FriendlyByteBuf::writeItem);
        buf.writeVarInt(foodLevel);
        buf.writeFloat(saturation);
    }

    public static TulpaInventoryPayload read(FriendlyByteBuf buf) {
        return new TulpaInventoryPayload(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readItem),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readItem),
                buf.readVarInt(),
                buf.readFloat());
    }

    /** Client main thread. */
    public static void handle(TulpaInventoryPayload p) {
        ClientTulpaInventory.update(p.uuid(), new ClientTulpaInventory.Snapshot(
                p.loaded(), p.items(), p.craft(), p.foodLevel(), p.saturation(), System.currentTimeMillis()));
    }
}
