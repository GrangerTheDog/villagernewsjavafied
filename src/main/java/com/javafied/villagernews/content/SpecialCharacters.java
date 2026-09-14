package com.javafied.villagernews.content;

import com.javafied.villagernews.behavior.BehaviorSensors;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of how the add-on places its special characters: one of each
 * lives somewhere in the world. When a village generates, one of its
 * villagers becomes a character not yet in the world - as long as it's over
 * 1000 blocks from world spawn and 150 from where another character
 * appeared. When a character dies, it may turn up again in another village.
 */
public final class SpecialCharacters {
	/** The Mayor, Testificate Man, Villager #5, Villager #9, the Untouchable Villager, and Wooly (a sheep). */
	private static final List<String> CHARACTERS = List.of("ilvfra", "poztxf", "vwpagn", "xcrjxf", "ghibss", "mlkxjo");
	private static final String WOOLY = "mlkxjo";
	private static final double MIN_DISTANCE_FROM_SPAWN = 1000;
	private static final double MIN_DISTANCE_APART = 150;

	/** Villagers to consider at the end of the tick - not while the entity manager is mid-load. */
	private static final List<Villager> pending = new ArrayList<>();

	private SpecialCharacters() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager && villager.hasAttached(ModAttachments.FROM_VILLAGE_GENERATION)) {
				villager.removeAttached(ModAttachments.FROM_VILLAGE_GENERATION);
				pending.add(villager);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pending.isEmpty()) {
				return;
			}
			List<Villager> batch = List.copyOf(pending);
			pending.clear();
			for (Villager villager : batch) {
				if (villager.isAlive() && villager.level() instanceof ServerLevel level) {
					maybeBecomeCharacter(level, villager);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				forget(level, entity);
			}
		});
	}

	private static void maybeBecomeCharacter(ServerLevel level, Villager villager) {
		ServerLevel overworld = level.getServer().overworld();
		Map<String, String> placed = overworld.getAttachedOrElse(ModAttachments.SPECIAL_CHARACTERS, Map.of());
		List<String> missing = new ArrayList<>();
		for (String character : CHARACTERS) {
			if (!placed.containsKey(character) && BehaviorSensors.definitions().get(character) != null) {
				missing.add(character);
			}
		}
		if (missing.isEmpty() || !farEnough(level, villager.blockPosition(), placed)) {
			return;
		}
		String character = missing.get(ThreadLocalRandom.current().nextInt(missing.size()));
		Entity placedEntity = character.equals(WOOLY) ? replaceWithWooly(level, villager) : becomeCharacter(level, villager, character);
		if (placedEntity != null) {
			Map<String, String> updated = new HashMap<>(placed);
			updated.put(character, placedEntity.getStringUUID() + "," + placedEntity.getBlockX() + "," + placedEntity.getBlockZ());
			overworld.setAttached(ModAttachments.SPECIAL_CHARACTERS, Map.copyOf(updated));
		}
	}

	private static Entity becomeCharacter(ServerLevel level, Villager villager, String character) {
		villager.setAttached(ModAttachments.VILLAGER_VARIANT, character);
		villager.setCustomName(Component.translatable("entity.villagernewsjavafied." + character));
		if (SpecialTrades.hasOwnTrades(character)) {
			SpecialTrades.makeTrader(level, villager);
		}
		return villager;
	}

	private static Entity replaceWithWooly(ServerLevel level, Villager villager) {
		Sheep sheep = EntityTypes.SHEEP.create(level, EntitySpawnReason.STRUCTURE);
		if (sheep == null) {
			return null;
		}
		sheep.snapTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), 0);
		sheep.setAttached(ModAttachments.VILLAGER_VARIANT, WOOLY);
		sheep.setCustomName(Component.translatable("entity.villagernewsjavafied." + WOOLY));
		sheep.setPersistenceRequired();
		villager.discard();
		level.addFreshEntity(sheep);
		return sheep;
	}

	private static boolean farEnough(ServerLevel level, BlockPos pos, Map<String, String> placed) {
		BlockPos spawn = level.getServer().overworld().getRespawnData().pos();
		if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && horizontal(spawn.getX(), spawn.getZ(), pos) < MIN_DISTANCE_FROM_SPAWN) {
			return false;
		}
		for (String value : placed.values()) {
			String[] parts = value.split(",");
			if (parts.length == 3 && horizontal(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), pos) < MIN_DISTANCE_APART) {
				return false;
			}
		}
		return true;
	}

	private static double horizontal(int x, int z, BlockPos pos) {
		return Math.hypot(pos.getX() - x, pos.getZ() - z);
	}

	private static void forget(ServerLevel level, Entity entity) {
		ServerLevel overworld = level.getServer().overworld();
		Map<String, String> placed = overworld.getAttachedOrElse(ModAttachments.SPECIAL_CHARACTERS, Map.of());
		String uuid = entity.getStringUUID();
		if (placed.values().stream().anyMatch(v -> v.startsWith(uuid + ","))) {
			Map<String, String> updated = new HashMap<>(placed);
			updated.values().removeIf(v -> v.startsWith(uuid + ","));
			overworld.setAttached(ModAttachments.SPECIAL_CHARACTERS, Map.copyOf(updated));
		}
	}
}
