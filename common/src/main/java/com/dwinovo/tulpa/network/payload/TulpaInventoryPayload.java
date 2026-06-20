package com.dwinovo.tulpa.network.payload;

import com.dwinovo.tulpa.Constants;
import com.dwinovo.tulpa.client.data.ClientTulpaInventory;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Server → Client: a companion's 36 main backpack slots, answering
 * {@link RequestInventoryPayload}. {@code loaded=false} means the body is asleep
 * in unloaded chunks (or not the requester's) — no contents. Dropped into
 * {@link ClientTulpaInventory} for the Items tab to render read-only.
 */
public record TulpaInventoryPayload(UUID uuid, boolean loaded, List<ItemStack> items,
                                    List<ItemStack> craft, int foodLevel, float saturation)
        implements CustomPacketPayload {

    public static final Type<TulpaInventoryPayload> TYPE = new Type<>(
            new ResourceLocation(Constants.MOD_ID, "tulpa_inventory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TulpaInventoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, TulpaInventoryPayload::uuid,
                    ByteBufCodecs.BOOL, TulpaInventoryPayload::loaded,
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC, TulpaInventoryPayload::items,
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC, TulpaInventoryPayload::craft,
                    ByteBufCodecs.VAR_INT, TulpaInventoryPayload::foodLevel,
                    ByteBufCodecs.FLOAT, TulpaInventoryPayload::saturation,
                    TulpaInventoryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client main thread. */
    public static void handle(TulpaInventoryPayload p) {
        ClientTulpaInventory.update(p.uuid(), new ClientTulpaInventory.Snapshot(
                p.loaded(), p.items(), p.craft(), p.foodLevel(), p.saturation(), System.currentTimeMillis()));
    }
}
