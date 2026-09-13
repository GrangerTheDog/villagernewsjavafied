package com.javafied.villagernews.content;

import com.javafied.villagernews.VillagerNewsJavafied;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/**
 * Vertical-slice item registration: only "Mayor Hat" is wired up by hand for
 * now, to prove converted texture + lang + registration work end-to-end. The
 * full item set will be registered from the converter's item manifest in a
 * follow-up pass, not hand-written like this.
 */
public final class ModItems {
	public static final Item MAYOR_HAT = register("cryhjc", new Item.Properties().equippable(EquipmentSlot.HEAD));

	private ModItems() {
	}

	/** Forces this class (and its static registrations) to load. */
	public static void init() {
	}

	private static Item register(String path, Item.Properties properties) {
		Identifier id = VillagerNewsJavafied.id(path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item item = new Item(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
