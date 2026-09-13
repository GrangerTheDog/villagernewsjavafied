package com.javafied.villagernews.client;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.converter.ConverterCli;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the "converted addon" resource pack that lives under
 * {@code <gamedir>/resourcepacks/}, so vanilla's own folder-scanning
 * {@code RepositorySource} (already wired up by the game, no mixin needed)
 * picks it up automatically. We just make sure it's selected and applied.
 */
public final class AddonConversionManager {
	private static final String PACK_ID = "villagernewsjavafied-converted";

	private AddonConversionManager() {
	}

	public static Path convertedPackDir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK_ID);
	}

	public static boolean isConverted() {
		return Files.exists(convertedPackDir().resolve("manifest.json"));
	}

	public static void convert(Path addonSource) throws IOException {
		Path packDir = convertedPackDir();
		Files.createDirectories(packDir.getParent());
		ConverterCli.run(addonSource, packDir);
	}

	/** Selects the converted pack (if not already) and reloads resources, without requiring manual setup. */
	public static void applyConvertedPack(Minecraft client) {
		PackRepository repository = client.getResourcePackRepository();
		repository.reload();

		String discoveredId = repository.getAvailableIds().stream()
				.filter(id -> id.contains(PACK_ID))
				.findFirst()
				.orElse(null);
		if (discoveredId == null) {
			VillagerNewsJavafied.LOGGER.warn("Converted resource pack was not discovered under {}", convertedPackDir());
			return;
		}

		List<String> selected = new ArrayList<>(repository.getSelectedIds());
		if (!selected.contains(discoveredId)) {
			selected.add(discoveredId);
			repository.setSelected(selected);
		}
		client.reloadResourcePacks();
	}
}
