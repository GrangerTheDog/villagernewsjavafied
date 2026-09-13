package com.javafied.villagernews.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gives every behavior-pack item an icon plus the item-definition and model
 * JSON that point at it.
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
			}
		}
		return count;
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
