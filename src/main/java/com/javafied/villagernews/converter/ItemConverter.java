package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gives every behavior-pack item an icon plus the item-definition and model
 * JSON that point at it; likewise a {@code <entity>_spawn_egg} for every
 * client entity with a spawn-egg texture. Head-worn items also get their 3D
 * attachable model ({@code geckolib/models/item/<id>}) and texture
 * ({@code textures/attachable/<id>.png}) for GeckoLib's armor renderer.
 *
 * <p>Icons have to land under {@code textures/item/}: since 1.19.3 only the
 * directories listed in {@code atlases/items.json} get stitched into the item
 * atlas, and vanilla lists just that one. A model referencing a texture
 * anywhere else (like the add-on's own {@code textures/oreville/vn/}) reports
 * "Missing textures" no matter how valid the PNG itself is.
 */
public final class ItemConverter {
	private ItemConverter() {
	}

	public static int convert(Path resourcePack, Path behaviorPack, Path outputAssetsDir) throws IOException {
		if (behaviorPack == null) {
			return 0;
		}
		Path itemsDir = behaviorPack.resolve("items");
		Path atlasFile = resourcePack.resolve("textures").resolve("item_texture.json");
		if (!Files.isDirectory(itemsDir) || !Files.exists(atlasFile)) {
			return 0;
		}
		JsonObject textureData = ConverterUtil.readJson(atlasFile).getAsJsonObject("texture_data");

		int count = 0;
		try (var stream = Files.list(itemsDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject item = ConverterUtil.readJson(file).getAsJsonObject("minecraft:item");
				if (item == null) {
					continue;
				}
				String identifier = item.getAsJsonObject("description").get("identifier").getAsString();
				String path = identifier.substring(identifier.indexOf(':') + 1);

				String iconKey = iconKey(item.getAsJsonObject("components"));
				if (iconKey == null || !textureData.has(iconKey)) {
					continue;
				}
				String texturePath = firstString(textureData.getAsJsonObject(iconKey).get("textures"));
				Path source = texturePath == null ? null : TextureConverter.find(resourcePack, texturePath);
				if (source == null) {
					continue;
				}

				TextureConverter.writePng(TextureConverter.read(source),
						outputAssetsDir.resolve("textures").resolve("item").resolve(path + ".png"));
				writeModels(outputAssetsDir, path);
				count++;
				if (isHeadWearable(item.getAsJsonObject("components"))) {
					convertWornModel(resourcePack, identifier, path, outputAssetsDir);
				}
			}
		}
		count += convertSpawnEggs(resourcePack, textureData, outputAssetsDir);
		return count;
	}

	private static boolean isHeadWearable(JsonObject components) {
		JsonObject wearable = components == null ? null : components.getAsJsonObject("minecraft:wearable");
		return wearable != null && wearable.has("slot") && wearable.get("slot").getAsString().equals("slot.armor.head");
	}

	private static int convertSpawnEggs(Path resourcePack, JsonObject textureData, Path outputAssetsDir) throws IOException {
		Path entityDir = resourcePack.resolve("entity");
		if (!Files.isDirectory(entityDir)) {
			return 0;
		}
		int count = 0;
		try (var stream = Files.walk(entityDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject clientEntity = ConverterUtil.readJson(file).getAsJsonObject("minecraft:client_entity");
				JsonObject description = clientEntity == null ? null : clientEntity.getAsJsonObject("description");
				JsonObject egg = description == null ? null : description.getAsJsonObject("spawn_egg");
				if (egg == null || !egg.has("texture") || !textureData.has(egg.get("texture").getAsString())) {
					continue;
				}
				String identifier = description.get("identifier").getAsString();
				String path = identifier.substring(identifier.indexOf(':') + 1) + "_spawn_egg";
				String texturePath = firstString(textureData.getAsJsonObject(egg.get("texture").getAsString()).get("textures"));
				Path source = texturePath == null ? null : TextureConverter.find(resourcePack, texturePath);
				if (source == null) {
					continue;
				}
				TextureConverter.writePng(TextureConverter.read(source),
						outputAssetsDir.resolve("textures").resolve("item").resolve(path + ".png"));
				writeModels(outputAssetsDir, path);
				count++;
			}
		}
		return count;
	}

	/**
	 * The attachable's geometry, re-rooted under an {@code armorHead} bone at
	 * the player head's pivot - the bone GeckoLib's armor renderer pins to the
	 * wearer's head. The add-on's attachables are modelled in player space,
	 * so their bones keep their positions.
	 */
	private static void convertWornModel(Path resourcePack, String identifier, String path, Path outputAssetsDir) throws IOException {
		Path attachables = resourcePack.resolve("attachables");
		if (!Files.isDirectory(attachables)) {
			return;
		}
		try (var stream = Files.walk(attachables)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject attachable = ConverterUtil.readJson(file).getAsJsonObject("minecraft:attachable");
				JsonObject description = attachable == null ? null : attachable.getAsJsonObject("description");
				if (description == null || !identifier.equals(description.get("identifier").getAsString())) {
					continue;
				}
				String geometryId = firstString(description.getAsJsonObject("geometry").get("default"));
				String texture = firstString(description.getAsJsonObject("textures").get("default"));
				JsonObject geometry = GeometryConverter.find(resourcePack, geometryId);
				Path textureFile = texture == null ? null : TextureConverter.find(resourcePack, texture);
				if (geometry == null || textureFile == null) {
					return;
				}
				JsonArray bones = geometry.getAsJsonArray("bones");
				JsonObject armorHead = new JsonObject();
				armorHead.addProperty("name", "armorHead");
				JsonArray pivot = new JsonArray();
				pivot.add(0);
				pivot.add(24);
				pivot.add(0);
				armorHead.add("pivot", pivot);
				JsonArray rebased = new JsonArray();
				rebased.add(armorHead);
				for (JsonElement bone : bones) {
					JsonObject b = bone.getAsJsonObject().deepCopy();
					if (!b.has("parent")) {
						b.addProperty("parent", "armorHead");
					}
					rebased.add(b);
				}
				geometry.add("bones", rebased);
				JsonObject root = new JsonObject();
				root.addProperty("format_version", "1.12.0");
				JsonArray geometries = new JsonArray();
				geometries.add(geometry);
				root.add("minecraft:geometry", geometries);
				ConverterUtil.writeJson(outputAssetsDir.resolve("geckolib").resolve("models").resolve("item").resolve(path + ".geo.json"), root);
				TextureConverter.writePng(TextureConverter.read(textureFile),
						outputAssetsDir.resolve("textures").resolve("attachable").resolve(path + ".png"));
				return;
			}
		}
	}

	/** Bedrock accepts {@code "icon": "key"}, {@code {"texture": "key"}} and {@code {"textures": {"default": "key"}}}. */
	private static String iconKey(JsonObject components) {
		if (components == null) {
			return null;
		}
		JsonElement icon = components.get("minecraft:icon");
		if (icon == null) {
			return null;
		}
		if (icon.isJsonPrimitive()) {
			return icon.getAsString();
		}
		JsonObject obj = icon.getAsJsonObject();
		if (obj.has("texture")) {
			return obj.get("texture").getAsString();
		}
		if (obj.has("textures")) {
			JsonElement textures = obj.get("textures");
			return textures.isJsonPrimitive() ? textures.getAsString() : firstString(textures.getAsJsonObject().get("default"));
		}
		return null;
	}

	private static String firstString(JsonElement element) {
		if (element == null) {
			return null;
		}
		if (element.isJsonArray()) {
			return element.getAsJsonArray().isEmpty() ? null : element.getAsJsonArray().get(0).getAsString();
		}
		return element.getAsString();
	}

	private static void writeModels(Path outputAssetsDir, String path) throws IOException {
		String modelId = ConverterUtil.MOD_ID + ":item/" + path;

		JsonObject textures = new JsonObject();
		textures.addProperty("layer0", modelId);
		JsonObject model = new JsonObject();
		model.addProperty("parent", "minecraft:item/generated");
		model.add("textures", textures);
		ConverterUtil.writeJson(outputAssetsDir.resolve("models").resolve("item").resolve(path + ".json"), model);

		JsonObject modelRef = new JsonObject();
		modelRef.addProperty("type", "minecraft:model");
		modelRef.addProperty("model", modelId);
		JsonObject definition = new JsonObject();
		definition.add("model", modelRef);
		ConverterUtil.writeJson(outputAssetsDir.resolve("items").resolve(path + ".json"), definition);
	}
}
