package com.dwinovo.animus;

import com.dwinovo.animus.entity.AnimusEntity;
import com.dwinovo.animus.entity.InitEntity;
import com.dwinovo.animus.network.AnimusNetwork;
import com.dwinovo.animus.network.payload.UnitsSnapshotPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class AnimusMod implements ModInitializer {

    @Override
    public void onInitialize() {
        EntityType<AnimusEntity> animusType = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                InitEntity.ANIMUS_KEY,
                EntityType.Builder.<AnimusEntity>of(AnimusEntity::new, MobCategory.CREATURE)
                        .sized(0.6f, 0.9f)
                        .build(InitEntity.ANIMUS_KEY));
        InitEntity.ANIMUS = () -> animusType;

        FabricDefaultAttributeRegistry.register(animusType,
                AnimusEntity.createAttributes().build());

        AnimusNetwork.register();

        // Push initial snapshot to the client when a player joins. Server-shutdown
        // cleanup is no longer needed — AnimusSavedData is persisted to disk by
        // vanilla's save loop.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                UnitsSnapshotPayload.sendTo(handler.getPlayer()));

        CommonClass.init();
        Constants.LOG.info("Animus mod initialised on Fabric.");
    }
}
