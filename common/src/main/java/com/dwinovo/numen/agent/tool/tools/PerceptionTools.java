package com.dwinovo.numen.agent.tool.tools;

import com.dwinovo.numen.agent.tool.api.NumenAction;
import com.dwinovo.numen.entity.NumenPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Perception tools authored on the {@link NumenAction} surface — the first
 * built-ins to dogfood the annotation/reflection path. Behaviour is identical
 * to the hand-written {@code NumenTool} classes they replace; only the wiring
 * (auto-derived schema, reflective invoke, entity injected by type) changed.
 */
public final class PerceptionTools {

    @NumenAction(name = "get_self_status", description =
            "Read your complete status in one call: name, game mode, HP / max HP, "
            + "hunger / saturation, position, dimension, biome, the structures you "
            + "are standing in (village, mineshaft, …), equipment (hands + armor — "
            + "an equipped item leaves the backpack, it is NOT lost), your full "
            + "backpack inventory, current attack target, and movement state. ALWAYS "
            + "call this before combat or planning decisions and periodically during "
            + "long tasks. No arguments.")
    public String getSelfStatus(NumenPlayer self) {
        JsonObject root = new JsonObject();
        root.addProperty("entity_id", self.getId());
        root.addProperty("name", self.getName().getString());
        root.addProperty("game_mode", self.gameMode.getGameModeForPlayer().getName());
        root.addProperty("hp", self.getHealth());
        root.addProperty("max_hp", self.getMaxHealth());
        root.addProperty("hunger", self.getFoodData().getFoodLevel());
        root.addProperty("saturation", self.getFoodData().getSaturationLevel());

        JsonObject pos = new JsonObject();
        pos.addProperty("x", self.getX());
        pos.addProperty("y", self.getY());
        pos.addProperty("z", self.getZ());
        root.add("position", pos);

        root.addProperty("dimension", self.level().dimension().location().toString());
        root.addProperty("biome", self.level().getBiome(self.blockPosition())
                .unwrapKey().map(k -> k.location().toString()).orElse("unknown"));
        // Structures whose bounding box contains us right now (e.g. village, mineshaft).
        JsonArray structures = new JsonArray();
        if (self.level() instanceof ServerLevel sl) {
            Registry<Structure> reg = sl.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (Structure s : sl.structureManager().getAllStructuresAt(self.blockPosition()).keySet()) {
                ResourceLocation key = reg.getKey(s);
                if (key != null) structures.add(key.toString());
            }
        }
        root.add("structures", structures);

        // Equipment: hands + armor. Lives OUTSIDE the backpack container.
        JsonObject equipment = new JsonObject();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = self.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            if (s.getCount() > 1) o.addProperty("count", s.getCount());
            equipment.add(slot.getName(), o);
        }
        root.add("equipment", equipment);

        // Full backpack inventory (empty slots omitted).
        var inv = self.getInventory();
        JsonArray items = new JsonArray();
        int used = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            used++;
            JsonObject o = new JsonObject();
            o.addProperty("slot", i);
            o.addProperty("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            o.addProperty("count", s.getCount());
            items.add(o);
        }
        JsonObject inventory = new JsonObject();
        inventory.add("items", items);
        inventory.addProperty("slots_used", used);
        inventory.addProperty("slots_total", inv.getContainerSize());
        root.add("inventory", inventory);

        // A player body has no AI attack-target; combat is task-driven.
        root.add("target", JsonNull.INSTANCE);

        root.addProperty("on_ground", self.onGround());
        root.addProperty("in_water", self.isInWater());
        root.addProperty("in_lava", self.isInLava());

        return root.toString();
    }
}
