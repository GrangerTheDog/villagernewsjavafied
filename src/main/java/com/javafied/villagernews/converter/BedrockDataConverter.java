package com.javafied.villagernews.converter;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Carries the add-on's own client-side logic across verbatim, for the mod's
 * Bedrock runtime to interpret: client entity definitions (scripts, render
 * controller lists, resource tables), render controllers, animation
 * controllers and materials - plus each behavior-pack entity's property
 * defaults, which that logic reads through {@code q.property(...)}.
 *
 * <p>Interpreted rather than translated into Java: the add-on's identifiers
 * are obfuscated and can change between releases, and none of it should end
 * up baked into the mod itself.
 */
public final class BedrockDataConverter {
	private BedrockDataConverter() {
	}

	public static int convert(Path resourcePack, Path behaviorPack, Path outputAssetsDir) throws IOException {
		Path outDir = outputAssetsDir.resolve("bedrock");
		int count = 0;
		count += copyDir(resourcePack.resolve("entity"), outDir.resolve("entity"), ".json");
		count += copyDir(resourcePack.resolve("render_controllers"), outDir.resolve("render_controllers"), ".json");
		count += copyDir(resourcePack.resolve("animation_controllers"), outDir.resolve("animation_controllers"), ".json");
		count += copyDir(resourcePack.resolve("animations"), outDir.resolve("animations"), ".json");
		count += copyDir(resourcePack.resolve("materials"), outDir.resolve("materials"), ".material");
		if (behaviorPack != null) {
			writePropertyDefaults(behaviorPack.resolve("entities"), outDir.resolve("properties.json"));
			count++;
			// Behavior definitions (sensors, events, properties) for the server-side interpreter.
			count += copyDir(behaviorPack.resolve("entities"), outputAssetsDir.getParent().getParent().resolve("server").resolve("entities"), ".json");
		}
		return count;
	}

	private static int copyDir(Path from, Path to, String extension) throws IOException {
		if (!Files.isDirectory(from)) {
			return 0;
		}
		int count = 0;
		try (var stream = Files.walk(from)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(extension)).toList()) {
				Path target = to.resolve(from.relativize(file).toString());
				Files.createDirectories(target.getParent());
				Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				count++;
			}
		}
		return count;
	}

	/** { "ns:entity": { "p:prop": default, ... }, ... } */
	private static void writePropertyDefaults(Path entitiesDir, Path target) throws IOException {
		JsonObject all = new JsonObject();
		if (Files.isDirectory(entitiesDir)) {
			try (var stream = Files.walk(entitiesDir)) {
				for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
					JsonObject entity = ConverterUtil.readJson(file).getAsJsonObject("minecraft:entity");
					if (entity == null) {
						continue;
					}
					JsonObject description = entity.getAsJsonObject("description");
					JsonObject properties = description.getAsJsonObject("properties");
					JsonObject defaults = new JsonObject();
					if (properties != null) {
						for (String name : properties.keySet()) {
							JsonObject property = properties.getAsJsonObject(name);
							if (property.has("default")) {
								defaults.add(name, property.get("default"));
							}
						}
					}
					all.add(description.get("identifier").getAsString(), defaults);
				}
			}
		}
		ConverterUtil.writeJson(target, all);
	}
}
