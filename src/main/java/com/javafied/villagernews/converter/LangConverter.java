package com.javafied.villagernews.converter;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Converts Bedrock {@code texts/<locale>.lang} (key=value, with Bedrock's
 * tab-then-"#" trailing comments) into Java {@code lang/<locale>.json}.
 */
public final class LangConverter {
	private LangConverter() {
	}

	public static int convert(Path resourcePack, Path outputAssetsDir) throws IOException {
		Path textsDir = resourcePack.resolve("texts");
		if (!Files.isDirectory(textsDir)) {
			return 0;
		}
		Path langOutDir = outputAssetsDir.resolve("lang");
		Files.createDirectories(langOutDir);

		int totalKeys = 0;
		try (var stream = Files.list(textsDir)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".lang")).toList()) {
				String fileName = file.getFileName().toString();
				String locale = fileName.substring(0, fileName.length() - ".lang".length()).toLowerCase(Locale.ROOT);

				JsonObject out = new JsonObject();
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					String trimmed = line.strip();
					if (trimmed.isEmpty() || trimmed.startsWith("#")) {
						continue;
					}
					int eq = trimmed.indexOf('=');
					if (eq < 0) {
						continue;
					}
					String key = trimmed.substring(0, eq).strip();
					String value = trimmed.substring(eq + 1);
					int tab = value.indexOf('\t');
					if (tab >= 0) {
						value = value.substring(0, tab);
					}
					value = value.strip();
					out.addProperty(toJavaKey(key), value);
				}

				ConverterUtil.writeJson(langOutDir.resolve(locale + ".json"), out);
				totalKeys += out.size();
			}
		}
		return totalKeys;
	}

	/**
	 * Bedrock keys like "entity.oreville_vn:mlkxjo.name" (and, e.g.,
	 * "item.spawn_egg.entity.oreville_vn:mlkxjo.name") carry the addon's own
	 * namespace immediately before a colon, with an arbitrary prefix before that.
	 * Java resolves translation keys as "entity.&lt;modid&gt;.path", so only the
	 * namespace token right before the colon is swapped for our mod id here -
	 * as long as entities/items are registered under the same short id used as
	 * the path (e.g. "villagernewsjavafied:mlkxjo"), Java's default
	 * description-id lookup resolves correctly with no per-entry code needed.
	 */
	private static String toJavaKey(String bedrockKey) {
		return bedrockKey.replaceAll("[A-Za-z0-9_]+:", ConverterUtil.MOD_ID + ".");
	}
}
