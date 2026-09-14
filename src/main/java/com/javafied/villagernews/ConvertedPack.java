package com.javafied.villagernews;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Where the add-on the player converted lives: a resource pack folder under
 * {@code <gamedir>/resourcepacks/}. Its {@code assets/} feed the client; its
 * {@code server/} folder holds the data the (integrated or dedicated) server
 * side of the port reads, such as the dialog tables.
 */
public final class ConvertedPack {
	public static final String PACK_ID = "villagernewsjavafied-converted";

	private ConvertedPack() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK_ID);
	}

	/** The converted add-on's version (from the manifest the converter writes); null if unknown. */
	public static String addonVersion() {
		Path manifest = dir().resolve("manifest.json");
		try {
			if (java.nio.file.Files.exists(manifest)) {
				var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(manifest)).getAsJsonObject();
				return json.has("addonVersion") ? json.get("addonVersion").getAsString() : null;
			}
		} catch (java.io.IOException | RuntimeException e) {
			return null;
		}
		return null;
	}

	public static Path serverData(String file) {
		return dir().resolve("server").resolve(file);
	}
}
