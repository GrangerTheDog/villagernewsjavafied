package com.javafied.villagernews.client.bedrock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import team.unnamed.mocha.runtime.value.Value;

import java.util.Map;

/**
 * Hand port of the part of the add-on's script ({@code ebi.js}) that its
 * villager visuals depend on.
 *
 * <p>In Bedrock every real villager ({@code minecraft:villager_v2}) gets an
 * invisible-host / visible-puppet pair: the script spawns an
 * {@code oreville_vn:villager} puppet, and every tick ({@code ufogjv}) copies
 * the host's state into the puppet's properties ({@code ntshmr}) and
 * teleports the puppet above the host ({@code zvxovy}); the puppet's
 * {@code offset} animation then pulls the model back down onto the host.
 * Here the model is drawn on the real villager itself, so both halves are
 * reproduced from the villager directly.
 */
final class VillagerPuppetPort {
	/** Path of the add-on client entity the script spawns as the puppet. */
	static final String PUPPET = "villager";
	static final double ADULT_LIFT = 1.9;
	static final double BABY_LIFT = 0.98;

	private VillagerPuppetPort() {
	}

	/** {@code ntshmr}: p:fsjsbp packs sleeping/on-ground/in-water/vehicle as decimal digits; p:enczhb is the vehicle. */
	static Map<String, Value> hostDrivenProperties(Entity host) {
		int vehicle = vehicleIndex(host);
		boolean sleeping = host instanceof LivingEntity living && living.isSleeping();
		int packed = 1000 * (sleeping ? 1 : 0) + 100 * (host.onGround() ? 1 : 0) + 10 * (host.isInWater() ? 1 : 0) + vehicle;
		return Map.of("p:fsjsbp", Value.of(packed), "p:enczhb", Value.of(vehicle));
	}

	/**
	 * {@code zvxovy}: how far above its host the script keeps the puppet, in
	 * blocks - the displacement the {@code offset} animation was written to
	 * undo. None while sleeping (puppet placed on the host) or riding (the
	 * puppet rides the vehicle instead).
	 */
	static double lift(Entity host) {
		if (host.isPassenger() || host instanceof LivingEntity living && living.isSleeping()) {
			return 0;
		}
		return host instanceof LivingEntity living && living.isBaby() ? BABY_LIFT : ADULT_LIFT;
	}

	/** {@code whwndv}: 1 in a boat, 2 in a minecart, else 0. Java has a boat/raft per wood type. */
	private static int vehicleIndex(Entity host) {
		Entity vehicle = host.getVehicle();
		if (vehicle == null) {
			return 0;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).getPath();
		if (type.endsWith("_boat") || type.endsWith("_raft")) {
			return 1;
		}
		return type.equals("minecart") ? 2 : 0;
	}
}
