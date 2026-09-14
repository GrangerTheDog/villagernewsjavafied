package com.javafied.villagernews.behavior;

import com.javafied.villagernews.content.ModAttachments;

import com.google.gson.JsonElement;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * An entity's Bedrock properties ({@code p:...}) as its behavior definition
 * declares them: stored (persistently) only when changed from the default,
 * and computed live for the ones the script derives from the host villager.
 */
public final class BehaviorProperties {
	private final Entity entity;
	private final BehaviorDefinitions.Definition definition;

	public BehaviorProperties(Entity entity, BehaviorDefinitions.Definition definition) {
		this.entity = entity;
		this.definition = definition;
	}

	/** Boolean, Double or String; null if the entity doesn't declare it. */
	public Object get(String name) {
		if (PuppetHost.PACKED_STATE.equals(name)) {
			return (double) PuppetHost.packedState(entity);
		}
		if (PuppetHost.VEHICLE.equals(name)) {
			return (double) PuppetHost.vehicleIndex(entity);
		}
		BehaviorDefinitions.Property property = definition.properties().get(name);
		if (property == null) {
			return null;
		}
		String stored = entity.getAttachedOrElse(ModAttachments.BEHAVIOR_PROPERTIES, Map.of()).get(name);
		return stored != null ? typed(property.type(), stored)
				: property.defaultValue() == null ? null : typed(property.type(), property.defaultValue().getAsString());
	}

	public void set(String name, JsonElement value) {
		BehaviorDefinitions.Property property = definition.properties().get(name);
		if (property == null || !value.isJsonPrimitive()) {
			return;
		}
		Map<String, String> updated = new HashMap<>(entity.getAttachedOrElse(ModAttachments.BEHAVIOR_PROPERTIES, Map.of()));
		updated.put(name, value.getAsString());
		entity.setAttached(ModAttachments.BEHAVIOR_PROPERTIES, Map.copyOf(updated));
	}

	private static Object typed(String type, String raw) {
		return switch (type) {
			case "bool" -> Boolean.parseBoolean(raw);
			case "int", "float" -> {
				try {
					yield Double.parseDouble(raw);
				} catch (NumberFormatException e) {
					yield 0.0;
				}
			}
			default -> raw;
		};
	}
}
