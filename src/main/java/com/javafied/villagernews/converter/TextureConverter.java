package com.javafied.villagernews.converter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Copies Bedrock {@code textures/**} through as plain RGBA {@code .png}
 * files (the only form vanilla Java resource loading reliably accepts),
 * decoding {@code .tga} and re-encoding every {@code .png} too - Bedrock/
 * Blockbench sometimes exports palette-indexed PNGs, which Minecraft's own
 * texture loader silently fails to load (surfaces as "Missing textures").
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
					BufferedImage image = toArgb(ImageIO.read(file.toFile()));
					ImageIO.write(image, "png", targetDir.resolve(name).toFile());
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

	private static BufferedImage toArgb(BufferedImage source) {
		if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
			return source;
		}
		BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		converted.getGraphics().drawImage(source, 0, 0, null);
		return converted;
	}
}
