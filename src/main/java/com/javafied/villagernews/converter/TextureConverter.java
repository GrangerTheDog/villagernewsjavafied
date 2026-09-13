package com.javafied.villagernews.converter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Copies Bedrock {@code textures/**} through, decoding {@code .tga} to
 * {@code .png} (the only format vanilla Java resource loading accepts) while
 * keeping every other relative path identical.
 */
public final class TextureConverter {
	private TextureConverter() {
	}

	public static int convert(Path resourcePack, Path outputAssetsDir) throws IOException {
		Path texturesDir = resourcePack.resolve("textures");
		if (!Files.isDirectory(texturesDir)) {
			return 0;
		}

		Path outDir = outputAssetsDir.resolve("textures");
		int count = 0;

		try (var stream = Files.walk(texturesDir)) {
			for (Path file : stream.filter(Files::isRegularFile).toList()) {
				String name = file.getFileName().toString();
				String lower = name.toLowerCase(Locale.ROOT);
				Path relativeDir = texturesDir.relativize(file.getParent());
				Path targetDir = outDir.resolve(relativeDir);
				Files.createDirectories(targetDir);

				if (lower.endsWith(".png")) {
					Files.copy(file, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
					count++;
				} else if (lower.endsWith(".tga")) {
					BufferedImage image = TgaImage.read(file);
					String pngName = name.substring(0, name.length() - 4) + ".png";
					ImageIO.write(image, "png", targetDir.resolve(pngName).toFile());
					count++;
				}
			}
		}
		return count;
	}
}
