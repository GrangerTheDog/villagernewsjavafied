package com.javafied.villagernews.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts Bedrock {@code animations/*.json} into GeckoLib animation files
 * (same schema GeckoLib already consumes from Blockbench exports), one
 * output file per animation identifier rather than mirroring Bedrock's own
 * grouping. Bedrock happily bundles thousands of named animations - for many
 * different entities - into one shared file, but GeckoLib's parser drops an
 * *entire* file the moment any single animation in it uses a construct it
 * doesn't support (this add-on has already hit two such cases). Splitting
 * per-animation means one bad entry can't take every other entity's
 * animations down with it, and only the genuinely broken ones fail.
 */
public final class AnimationConverter {
	private AnimationConverter() {
	}

	/** Returns a map of Bedrock animation name (e.g. "animation.oreville_vn.xxx") -> output path. */
	public static Map<String, String> convert(Path resourcePack, Path outputAssetsDir) throws IOException {
		Map<String, String> animationToPath = new LinkedHashMap<>();
		Path animationsDir = resourcePack.resolve("animations");
		if (!Files.isDirectory(animationsDir)) {
			return animationToPath;
		}

		// GeckoLib 5.x scans "geckolib/animations/" (not "animations/") to avoid clashing with vanilla's own folder.
		Path outDir = outputAssetsDir.resolve("geckolib").resolve("animations").resolve("entity");
		Files.createDirectories(outDir);

		try (var stream = Files.list(animationsDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject root = ConverterUtil.readJson(file);
				JsonObject animations = root.getAsJsonObject("animations");
				if (animations == null) {
					continue;
				}
				normalizeRelativeTo(animations);
				normalizeKeyframeLists(animations);
				String formatVersion = root.has("format_version") ? root.get("format_version").getAsString() : "1.10.0";

				for (String animationName : animations.keySet()) {
					String slug = ConverterUtil.slug(animationName);

					JsonObject outFile = new JsonObject();
					outFile.addProperty("format_version", formatVersion);
					JsonObject singleAnimation = new JsonObject();
					singleAnimation.add(animationName, animations.get(animationName));
					outFile.add("animations", singleAnimation);

					Path outPath = outDir.resolve(slug + ".animation.json");
					ConverterUtil.writeJson(outPath, outFile);
					animationToPath.put(animationName, "geckolib/animations/entity/" + slug + ".animation.json");
				}
			}
		}
		return animationToPath;
	}

	/**
	 * Bedrock lets a single keyframe carry a list: several Molang statements in
	 * a {@code timeline} entry, or several effects in a {@code particle_effects}
	 * / {@code sound_effects} entry. GeckoLib only accepts one value per
	 * keyframe and rejects the whole file otherwise (2234 of this add-on's
	 * 2512 animations). Timeline statements are joined into one Molang string,
	 * which is equivalent; for effect lists only the first effect survives.
	 */
	private static void normalizeKeyframeLists(JsonObject animations) {
		for (String animationName : animations.keySet()) {
			JsonObject animation = animations.getAsJsonObject(animationName);
			collapseLists(animation.getAsJsonObject("timeline"), true);
			collapseLists(animation.getAsJsonObject("particle_effects"), false);
			collapseLists(animation.getAsJsonObject("sound_effects"), false);
		}
	}

	private static void collapseLists(JsonObject keyframes, boolean joinAsMolang) {
		if (keyframes == null) {
			return;
		}
		for (String time : keyframes.keySet()) {
			JsonElement value = keyframes.get(time);
			if (!value.isJsonArray()) {
				continue;
			}
			var list = value.getAsJsonArray();
			if (list.isEmpty()) {
				keyframes.remove(time);
			} else if (joinAsMolang) {
				StringBuilder joined = new StringBuilder();
				for (JsonElement statement : list) {
					String s = statement.getAsString().strip();
					joined.append(s);
					if (!s.endsWith(";")) {
						joined.append(';');
					}
				}
				keyframes.addProperty(time, joined.toString());
			} else {
				keyframes.add(time, list.get(0));
			}
		}
	}

	/**
	 * Newer Bedrock animations nest a bone's {@code relative_to} as e.g.
	 * {@code {"rotation": "entity"}}; GeckoLib 5.5.5's parser only accepts a
	 * plain string there (it throws a JsonSyntaxException otherwise, which
	 * silently drops the *entire* file, not just the offending bone). Every
	 * occurrence we've seen only ever sets "rotation", so collapsing to that
	 * string is a safe, direct translation rather than a lossy guess.
	 */
	private static void normalizeRelativeTo(JsonObject animations) {
		for (String animationName : animations.keySet()) {
			JsonObject animation = animations.getAsJsonObject(animationName);
			JsonObject bones = animation.getAsJsonObject("bones");
			if (bones == null) {
				continue;
			}
			for (String boneName : bones.keySet()) {
				JsonObject bone = bones.getAsJsonObject(boneName);
				JsonElement relativeTo = bone.get("relative_to");
				if (relativeTo != null && relativeTo.isJsonObject()) {
					JsonElement rotation = relativeTo.getAsJsonObject().get("rotation");
					if (rotation != null && rotation.isJsonPrimitive()) {
						bone.add("relative_to", rotation);
					} else {
						bone.remove("relative_to");
					}
				}
			}
		}
	}
}
