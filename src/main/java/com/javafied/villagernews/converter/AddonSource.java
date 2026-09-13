package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Locates the resource pack and (optional) behavior pack inside a user-supplied
 * {@code .mcaddon} file or an already-extracted folder, by reading each candidate
 * pack's {@code manifest.json}. Never bundled with the mod; always points at
 * content the user supplied themselves.
 */
public final class AddonSource {
	public final Path resourcePack;
	public final Path behaviorPack;

	private AddonSource(Path resourcePack, Path behaviorPack) {
		this.resourcePack = resourcePack;
		this.behaviorPack = behaviorPack;
	}

	public static AddonSource locate(Path input, Path stagingDir) throws IOException {
		Path root;
		if (Files.isDirectory(input)) {
			root = input;
		} else {
			root = stagingDir.resolve("extracted");
			unzip(input, root);
		}

		List<Path> candidates = new ArrayList<>();
		if (Files.exists(root.resolve("manifest.json"))) {
			candidates.add(root);
		}
		try (var stream = Files.list(root)) {
			for (Path p : stream.toList()) {
				if (Files.isDirectory(p) && Files.exists(p.resolve("manifest.json"))) {
					candidates.add(p);
				}
			}
		}

		Path rp = null;
		Path bp = null;
		for (Path candidate : candidates) {
			String moduleType = firstModuleType(ConverterUtil.readJson(candidate.resolve("manifest.json")));
			if ("resources".equals(moduleType)) {
				rp = candidate;
			} else if ("data".equals(moduleType) || "script".equals(moduleType)) {
				bp = candidate;
			}
		}

		if (rp == null) {
			throw new IOException("Could not find a resource pack (a manifest.json with a 'resources' module) under " + root
					+ ". Make sure you picked the Villager News .mcaddon (or its extracted folder).");
		}
		return new AddonSource(rp, bp);
	}

	private static String firstModuleType(JsonObject manifest) {
		JsonArray modules = manifest.getAsJsonArray("modules");
		if (modules == null) {
			return null;
		}
		for (var element : modules) {
			JsonObject module = element.getAsJsonObject();
			if (module.has("type")) {
				return module.get("type").getAsString();
			}
		}
		return null;
	}

	private static void unzip(Path zipFile, Path targetDir) throws IOException {
		Files.createDirectories(targetDir);
		Path normalizedTarget = targetDir.normalize();
		try (InputStream fis = Files.newInputStream(zipFile); ZipInputStream zis = new ZipInputStream(fis)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				Path outPath = normalizedTarget.resolve(entry.getName()).normalize();
				if (!outPath.startsWith(normalizedTarget)) {
					throw new IOException("Zip entry escapes target directory: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(outPath);
				} else {
					Files.createDirectories(outPath.getParent());
					Files.copy(zis, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				zis.closeEntry();
			}
		}
	}
}
