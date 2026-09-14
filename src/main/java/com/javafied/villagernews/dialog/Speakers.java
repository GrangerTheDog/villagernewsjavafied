package com.javafied.villagernews.dialog;

import com.javafied.villagernews.content.ModAttachments;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Who can speak, in the add-on's terms: ordinary villagers, each special
 * character, the wandering trader and Wooly. Many lines belong to one kind
 * (only the Mayor says his), and "the nearest villager reacts" skips the
 * trader and Wooly unless a line asks for them.
 */
public final class Speakers {
	public enum Kind {
		VILLAGER, MAYOR, TESTIFICATE_MAN, NUMBER_5, NUMBER_9, UNTOUCHABLE, TRADER, WOOLY
	}

	/** The script's default speaker families: everyone but Wooly. */
	public static final Set<Kind> DEFAULT_KINDS = Set.copyOf(EnumSet.complementOf(EnumSet.of(Kind.WOOLY)));
	/** Kinds the "nearest villager reacts" search considers (the script excludes the trader and Wooly there). */
	public static final Set<Kind> NEARBY_KINDS = Set.copyOf(EnumSet.complementOf(EnumSet.of(Kind.WOOLY, Kind.TRADER)));

	/** The add-on's ids for the special characters, as used by the villager variant attachment. */
	private static final Map<String, Kind> VARIANTS = Map.of("ilvfra", Kind.MAYOR, "poztxf", Kind.TESTIFICATE_MAN,
			"vwpagn", Kind.NUMBER_5, "xcrjxf", Kind.NUMBER_9, "ghibss", Kind.UNTOUCHABLE, "txczvv", Kind.TRADER,
			"mlkxjo", Kind.WOOLY);

	private Speakers() {
	}

	/** Null if this entity never speaks. */
	public static Kind kindOf(Entity entity) {
		if (entity instanceof WanderingTrader) {
			return Kind.TRADER;
		}
		String variant = entity.getAttached(ModAttachments.VILLAGER_VARIANT);
		if (entity instanceof Villager) {
			return variant == null ? Kind.VILLAGER : VARIANTS.getOrDefault(variant, Kind.VILLAGER);
		}
		if (entity instanceof Sheep && "mlkxjo".equals(variant)) {
			return Kind.WOOLY;
		}
		return null;
	}

	public static boolean isBaby(Entity entity) {
		return entity instanceof LivingEntity living && living.isBaby();
	}

	/** Ordinary villagers' profession, in Bedrock's names for the ones that differ ("none" for unemployed). */
	public static String profession(Entity entity) {
		if (!(entity instanceof Villager villager)) {
			return "";
		}
		return villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
	}
}
