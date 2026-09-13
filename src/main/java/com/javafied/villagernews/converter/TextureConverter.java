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
			for (Path file : stream.filter(Files::isRegularFile).filter(TextureConverter::isImage).toList()) {
				String name = file.getFileName().toString();
				String pngName = name.substring(0, name.lastIndexOf('.')) + ".png";
				Path target = outDir.resolve(texturesDir.relativize(file.getParent())).resolve(pngName);
				writePng(read(file), target);
				count++;
			}
		}
		return count;
	}

	/**
	 * Resolves a Bedrock texture reference, which never carries an extension
	 * (e.g. "textures/oreville/vn/ean"), to the actual {@code .png}/{@code .tga}.
	 */
	public static Path find(Path resourcePack, String extensionlessPath) {
		for (String ext : new String[] {".png", ".tga"}) {
			Path candidate = resourcePack.resolve(extensionlessPath + ext);
			if (Files.exists(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	public static BufferedImage read(Path file) throws IOException {
		if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tga")) {
			return TgaImage.read(file);
		}
		return toArgb(ImageIO.read(file.toFile()));
	}

	public static void writePng(BufferedImage image, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		ImageIO.write(image, "png", target.toFile());
	}

	private static boolean isImage(Path file) {
		String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return lower.endsWith(".png") || lower.endsWith(".tga");
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
