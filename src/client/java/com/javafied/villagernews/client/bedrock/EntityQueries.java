package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.content.ModAttachments;

import com.google.gson.JsonElement;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.phys.Vec3;

import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bedrock's {@code q.*} queries, answered from the Java entity the reskin is
 * drawn over. Only the queries the add-on actually uses are implemented;
 * anything else reads as 0, same as an unknown query in Bedrock.
 */
final class EntityQueries implements ObjectValue {
	/** Bedrock villager_v2 "minecraft:variant" order; index = profession. */
	private static final List<String> PROFESSIONS = List.of("none", "farmer", "fisherman", "shepherd", "fletcher",
			"librarian", "cartographer", "cleric", "armorer", "weaponsmith", "toolsmith", "butcher", "leatherworker",
			"mason", "nitwit");
	/** Bedrock villager_v2 "minecraft:mark_variant" order; index = biome type. */
	private static final List<String> BIOMES = List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");

	private final Entity entity;
	private final float partialTick;
	private final Map<String, JsonElement> properties;
	/** Property values the add-on's script would have written (see {@link VillagerPuppetPort}); win over defaults. */
	private final Map<String, Value> propertyOverrides;

	EntityQueries(Entity entity, float partialTick, Map<String, JsonElement> properties, Map<String, Value> propertyOverrides) {
		this.entity = entity;
		this.partialTick = partialTick;
		this.properties = properties;
		this.propertyOverrides = propertyOverrides;
	}

	@Override
	public ObjectProperty getProperty(String name) {
		return ObjectProperty.property(value(name.toLowerCase(Locale.ROOT)), true);
	}

	private Value value(String name) {
		LivingEntity living = entity instanceof LivingEntity l ? l : null;
		return switch (name) {
			case "is_baby" -> Value.of(living != null && living.isBaby());
			case "is_alive" -> Value.of(entity.isAlive());
			case "is_on_ground" -> Value.of(entity.onGround());
			case "is_in_water" -> Value.of(entity.isInWater());
			case "is_riding" -> Value.of(entity.isPassenger());
			case "has_rider" -> Value.of(entity.isVehicle());
			case "is_sleeping" -> Value.of(living != null && living.isSleeping());
			case "is_on_fire" -> Value.of(entity.isOnFire());
			case "is_avoiding_mobs" -> Value.of(Boolean.TRUE.equals(entity.getAttached(ModAttachments.AVOIDING)));
			case "is_sheared" -> Value.of(entity instanceof Sheep sheep && sheep.isSheared());
			case "health" -> Value.of(living != null ? living.getHealth() : 0);
			case "life_time" -> Value.of((entity.tickCount + partialTick) / 20.0);
			case "frame_alpha" -> Value.of(partialTick);
			case "modified_move_speed" -> Value.of(living != null ? living.walkAnimation.speed(partialTick) : 0);
			case "modified_distance_moved" -> Value.of(living != null ? living.walkAnimation.position(partialTick) : 0);
			case "ground_speed" -> {
				Vec3 v = entity.getDeltaMovement();
				yield Value.of(Math.sqrt(v.x * v.x + v.z * v.z) * 20);
			}
			case "vertical_speed" -> Value.of(entity.getDeltaMovement().y * 20);
			case "target_x_rotation" -> Value.of(entity.getXRot(partialTick));
			case "target_y_rotation" -> Value.of(living != null
					? Mth.wrapDegrees(lerpDegrees(living.yHeadRotO, living.yHeadRot) - lerpDegrees(living.yBodyRotO, living.yBodyRot))
					: 0);
			case "body_y_rotation" -> Value.of(living != null
					? lerpDegrees(living.yBodyRotO, living.yBodyRot)
					: entity.getYRot(partialTick));
			// Bedrock picks one of six villager faces at spawn; Java has none, so derive a stable one per entity.
			case "skin_id" -> Value.of(Math.floorMod(entity.getUUID().hashCode(), 6));
			case "variant" -> Value.of(villager() == null ? 0 : indexOf(PROFESSIONS, keyPath(villager().profession().unwrapKey())));
			case "mark_variant" -> Value.of(villager() == null ? 0 : indexOf(BIOMES, keyPath(villager().type().unwrapKey())));
			case "trade_tier" -> Value.of(villager() == null ? 0 : villager().level() - 1);
			case "property" -> function(args -> propertyValue(args.next().eval().getAsString()));
			case "has_property" -> function(args -> {
				String property = args.next().eval().getAsString();
				return Value.of(propertyOverrides.containsKey(property) || properties.containsKey(property));
			});
			case "is_name_any" -> function(args -> {
				String customName = entity.getCustomName() == null ? null : entity.getCustomName().getString();
				for (int i = 0; i < args.length(); i++) {
					if (args.next().eval().getAsString().equals(customName)) {
						return Value.of(true);
					}
				}
				return Value.of(false);
			});
			case "any" -> function(args -> {
				double subject = args.next().eval().getAsNumber();
				for (int i = 1; i < args.length(); i++) {
					if (args.next().eval().getAsNumber() == subject) {
						return Value.of(true);
					}
				}
				return Value.of(false);
			});
			case "position" -> function(args -> {
				int axis = (int) args.next().eval().getAsNumber();
				Vec3 pos = entity.getPosition(partialTick);
				return Value.of(axis == 0 ? pos.x : axis == 1 ? pos.y : pos.z);
			});
			// We only ever render the "fancy" path.
			case "graphics_mode_is_any" -> function(args -> Value.of(true));
			default -> Value.nil();
		};
	}

	private Value propertyValue(String property) {
		Value override = propertyOverrides.get(property);
		if (override != null) {
			return override;
		}
		JsonElement value = properties.get(property);
		if (value == null || !value.isJsonPrimitive()) {
			return Value.nil();
		}
		if (value.getAsJsonPrimitive().isBoolean()) {
			return Value.of(value.getAsBoolean());
		}
		if (value.getAsJsonPrimitive().isNumber()) {
			return Value.of(value.getAsDouble());
		}
		return StringValue.of(value.getAsString());
	}

	private VillagerData villager() {
		return entity instanceof Villager v ? v.getVillagerData() : null;
	}

	private float lerpDegrees(float previous, float current) {
		return previous + Mth.wrapDegrees(current - previous) * partialTick;
	}

	private static String keyPath(java.util.Optional<? extends net.minecraft.resources.ResourceKey<?>> key) {
		return key.map(k -> k.identifier().getPath()).orElse("");
	}

	private static int indexOf(List<String> list, String value) {
		return Math.max(0, list.indexOf(value));
	}

	private static Function<Object> function(java.util.function.Function<Function.Arguments, Value> body) {
		return (ctx, args) -> body.apply(args);
	}
}
