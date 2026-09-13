package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Converts Bedrock {@code sounds/sound_definitions.json} + its {@code .ogg}
 * files into a Java {@code sounds.json} plus copied (already Vorbis, no
 * transcoding needed) ogg files.
 */
public final class SoundConverter {
	private static final String SOUNDS_PREFIX = "sounds/";

	private SoundConverter() {
	}

	public static int convert(Path resourcePack, Path outputAssetsDir) throws IOException {
		Path definitionsFile = resourcePack.resolve("sounds").resolve("sound_definitions.json");
		if (!Files.exists(definitionsFile)) {
			return 0;
		}
		Path soundsSrcDir = resourcePack.resolve("sounds");
		Path soundsOutDir = outputAssetsDir.resolve("sounds");

		JsonObject root = ConverterUtil.readJson(definitionsFile);
		JsonObject definitions = root.has("sound_definitions") ? root.getAsJsonObject("sound_definitions") : root;

		JsonObject outSounds = new JsonObject();
		int copied = 0;

		for (String eventName : definitions.keySet()) {
			JsonObject definition = definitions.getAsJsonObject(eventName);
			JsonObject outDefinition = new JsonObject();
			if (definition.has("category")) {
				outDefinition.add("category", definition.get("category"));
			}

			JsonArray outSoundList = new JsonArray();
			JsonArray sounds = definition.has("sounds") ? definition.getAsJsonArray("sounds") : new JsonArray();
			for (JsonElement soundEntry : sounds) {
				String relativePath = soundEntry.isJsonObject()
						? soundEntry.getAsJsonObject().get("name").getAsString()
						: soundEntry.getAsString();
				if (!relativePath.startsWith(SOUNDS_PREFIX)) {
					continue;
				}
				String withoutPrefix = relativePath.substring(SOUNDS_PREFIX.length());
				Path srcOgg = soundsSrcDir.resolve(withoutPrefix + ".ogg");
				if (!Files.exists(srcOgg)) {
					continue;
				}
				Path dstOgg = soundsOutDir.resolve(withoutPrefix + ".ogg");
				Files.createDirectories(dstOgg.getParent());
				Files.copy(srcOgg, dstOgg, StandardCopyOption.REPLACE_EXISTING);
				copied++;
				outSoundList.add(withoutPrefix);
			}

			outDefinition.add("sounds", outSoundList);
			String javaEventName = eventName.replace(':', '.');
			outSounds.add(javaEventName, outDefinition);
		}

		ConverterUtil.writeJson(outputAssetsDir.resolve("sounds.json"), outSounds);
		return copied;
	}
}
