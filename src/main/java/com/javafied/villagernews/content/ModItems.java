package com.javafied.villagernews.content;

import com.javafied.villagernews.VillagerNewsJavafied;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The add-on's items, under readable ids ({@code handbook}, {@code mayor_hat};
 * the converter writes the player's add-on's icons, models and names under
 * the same ones), plus spawn eggs for its characters - all in a "Villager
 * News" creative tab.
 */
public final class ModItems {
	private static final List<Item> TAB_CONTENTS = new ArrayList<>();
	private static final ResourceKey<EquipmentAsset> WORN = ResourceKey.create(EquipmentAssets.ROOT_ID, VillagerNewsJavafied.id("worn"));

	/** Villager News Handbook: the add-on's guide, read from the player's converted add-on. */
	public static final Item HANDBOOK = register("handbook", p -> AttachableItem.create(p, true), new Item.Properties().stacksTo(1));
	public static final Item MAYOR_HAT = wearable("mayor_hat");
	public static final Item TESTIFICATE_MAN_HELMET = wearable("testificate_man_helmet");
	public static final Item MOUSTACHE = wearable("moustache");
	/** Held up to speak into while used: at full speed, as in the add-on. */
	public static final Item MICROPHONE = register("microphone", p -> AttachableItem.create(p, false), new Item.Properties().stacksTo(1)
			.component(DataComponents.USE_EFFECTS, new UseEffects(true, true, 1)));
	public static final Item VILLAGER_NOSE = wearable("villager_nose");

	public static final Item MAYOR_SPAWN_EGG = spawnEgg("mayor", EntityTypes.VILLAGER);
	public static final Item TESTIFICATE_MAN_SPAWN_EGG = spawnEgg("testificate_man", EntityTypes.VILLAGER);
	public static final Item VILLAGER_5_SPAWN_EGG = spawnEgg("villager_5", EntityTypes.VILLAGER);
	public static final Item VILLAGER_9_SPAWN_EGG = spawnEgg("villager_9", EntityTypes.VILLAGER);
	public static final Item UNTOUCHABLE_SPAWN_EGG = spawnEgg("untouchable", EntityTypes.VILLAGER);
	public static final Item WOOLY_SPAWN_EGG = spawnEgg("wooly", EntityTypes.SHEEP);

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

	/**
	 * Worn on the head. The equipment asset is only a marker: vanilla hands a
	 * head item with one to the armor layer (where GeckoLib draws the 3D
	 * model) rather than drawing its flat icon on the head; nothing loads it.
	 */
	private static Item wearable(String path) {
		return register(path, WearableItem::create, new Item.Properties().stacksTo(1)
				.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setAsset(WORN).build()));
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
