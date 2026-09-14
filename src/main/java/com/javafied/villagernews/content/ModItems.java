package com.javafied.villagernews.content;

import com.javafied.villagernews.VillagerNewsJavafied;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The add-on's items, under their add-on ids so the converted icons, models
 * and names (which the player's own add-on supplies) line up with them, plus
 * spawn eggs for its characters - all in a "Villager News" creative tab.
 */
public final class ModItems {
	private static final List<Item> TAB_CONTENTS = new ArrayList<>();

	/** Villager News Handbook: the add-on's guide, read from the player's converted add-on. */
	public static final Item HANDBOOK = register("kfjmlk", p -> AttachableItem.create(p, true), new Item.Properties().stacksTo(1));
	public static final Item MAYOR_HAT = wearable("cryhjc");
	public static final Item TESTIFICATE_MAN_HELMET = wearable("ufernq");
	public static final Item MOUSTACHE = wearable("odplew");
	public static final Item MICROPHONE = register("dsojot", p -> AttachableItem.create(p, false), new Item.Properties().stacksTo(1));
	public static final Item VILLAGER_NOSE = wearable("qzhdgf");

	public static final Item MAYOR_SPAWN_EGG = spawnEgg("ilvfra", EntityTypes.VILLAGER);
	public static final Item TESTIFICATE_MAN_SPAWN_EGG = spawnEgg("poztxf", EntityTypes.VILLAGER);
	public static final Item VILLAGER_5_SPAWN_EGG = spawnEgg("vwpagn", EntityTypes.VILLAGER);
	public static final Item VILLAGER_9_SPAWN_EGG = spawnEgg("xcrjxf", EntityTypes.VILLAGER);
	public static final Item UNTOUCHABLE_SPAWN_EGG = spawnEgg("ghibss", EntityTypes.VILLAGER);
	public static final Item WOOLY_SPAWN_EGG = spawnEgg("mlkxjo", EntityTypes.SHEEP);

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			VillagerNewsJavafied.id("villager_news"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.villagernewsjavafied"))
					.icon(() -> new ItemStack(MAYOR_HAT))
					.displayItems((parameters, output) -> TAB_CONTENTS.forEach(output::accept))
					.build());

	private ModItems() {
	}

	/** Forces this class (and its static registrations) to load. */
	public static void init() {
	}

	private static Item wearable(String path) {
		return register(path, WearableItem::create, new Item.Properties().stacksTo(1).equippable(EquipmentSlot.HEAD));
	}

	/** Named like the add-on's own eggs ("item.spawn_egg.entity.<ns>.<id>" in its converted lang). */
	private static Item spawnEgg(String variant, EntityType<? extends Mob> type) {
		return register(variant + "_spawn_egg", properties -> new VariantSpawnEggItem(type, variant, properties),
				new Item.Properties().overrideDescription("item.spawn_egg.entity." + VillagerNewsJavafied.MOD_ID + "." + variant));
	}

	private static Item register(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		Identifier id = VillagerNewsJavafied.id(path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		TAB_CONTENTS.add(item);
		return item;
	}
}
