package com.javafied.villagernews.converter;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copies Bedrock {@code animations/*.json} through as GeckoLib animation files
 * (same schema GeckoLib already consumes from Blockbench exports) and indexes
 * which output file defines which animation name, since Bedrock happily bundles
 * many named animations - for many different entities - into one file.
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

		Path outDir = outputAssetsDir.resolve("animations").resolve("entity");
		Files.createDirectories(outDir);

		try (var stream = Files.list(animationsDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject root = ConverterUtil.readJson(file);
				JsonObject animations = root.getAsJsonObject("animations");
				if (animations == null) {
					continue;
				}

				String fileName = file.getFileName().toString();
				String baseName = fileName.substring(0, fileName.length() - ".json".length());
				Path outPath = outDir.resolve(baseName + ".animation.json");
				ConverterUtil.writeJson(outPath, root);

				String relativePath = "animations/entity/" + baseName + ".animation.json";
				for (String animationName : animations.keySet()) {
					animationToPath.put(animationName, relativePath);
				}
			}
		}
		return animationToPath;
	}
}
