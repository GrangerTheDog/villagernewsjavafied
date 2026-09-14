package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

/**
 * Converts Bedrock {@code sounds/sound_definitions.json} + its {@code .ogg}
 * files into a Java {@code sounds.json} plus copied (already Vorbis, no
 * transcoding needed) ogg files.
 */
public final class SoundConverter {
	private static final String SOUNDS_PREFIX = "sounds/";
	/** Bedrock entity -> Java sound event prefix, for vanilla mobs whose sounds the add-on silences. */
	private static final Map<String, String> VANILLA_ENTITIES = Map.of("villager_v2", "entity.villager.");
	/** Silenced events the mod already replaces with the add-on's own reactions; the rest stay vanilla for now. */
	private static final Set<String> REPLACED_EVENTS = Set.of("ambient", "hurt", "death");

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
				// Unqualified names in sounds.json resolve to minecraft:, not to this pack's namespace.
				outSoundList.add(ConverterUtil.MOD_ID + ":" + withoutPrefix);
			}

			outDefinition.add("sounds", outSoundList);
			String javaEventName = eventName.replace(':', '.');
			outSounds.add(javaEventName, outDefinition);
		}

		ConverterUtil.writeJson(outputAssetsDir.resolve("sounds.json"), outSounds);
		writeSilencedVanillaSounds(resourcePack, outputAssetsDir.resolveSibling("minecraft"));
		return copied;
	}

	/**
	 * The add-on turns vanilla villager sounds down to volume 0 in its
	 * {@code sounds.json} because its own voice lines take over. Mirror that for
	 * the events the mod replaces, by overriding them with no sounds at all.
	 */
	private static void writeSilencedVanillaSounds(Path resourcePack, Path minecraftAssetsDir) throws IOException {
		Path file = resourcePack.resolve("sounds.json");
		if (!Files.exists(file)) {
			return;
		}
		JsonObject entities = ConverterUtil.readJson(file).getAsJsonObject("entity_sounds");
		entities = entities == null ? null : entities.getAsJsonObject("entities");
		if (entities == null) {
			return;
		}
		JsonObject overrides = new JsonObject();
		for (Map.Entry<String, String> entity : VANILLA_ENTITIES.entrySet()) {
			JsonObject definition = entities.getAsJsonObject(entity.getKey());
			JsonObject events = definition == null ? null : definition.getAsJsonObject("events");
			if (events == null) {
				continue;
			}
			for (String event : events.keySet()) {
				JsonElement volume = events.getAsJsonObject(event).get("volume");
				if (REPLACED_EVENTS.contains(event) && volume != null && volume.getAsDouble() == 0) {
					JsonObject silent = new JsonObject();
					silent.addProperty("replace", true);
					silent.add("sounds", new JsonArray());
					overrides.add(entity.getValue() + event, silent);
				}
			}
		}
		if (!overrides.isEmpty()) {
			ConverterUtil.writeJson(minecraftAssetsDir.resolve("sounds.json"), overrides);
		}
	}
}
