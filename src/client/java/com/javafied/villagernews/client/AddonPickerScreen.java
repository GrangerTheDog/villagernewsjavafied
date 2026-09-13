package com.javafied.villagernews.client;

import com.javafied.villagernews.VillagerNewsJavafied;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shown once, on first launch, when no converted addon assets are found yet.
 * Lets the user point at their own legally-owned Villager News .mcaddon; the
 * mod never bundles or downloads that content itself.
 *
 * <p>Uses a plain text field rather than a native file-chooser dialog: AWT
 * considers the game's launch environment headless in some setups (it did in
 * dev testing here), which makes {@code JFileChooser} throw instead of
 * opening - a Minecraft-native widget avoids that entirely.
 */
public final class AddonPickerScreen extends Screen {
	private EditBox pathBox;
	private Button convertButton;
	private StringWidget status;

	public AddonPickerScreen() {
		super(Component.literal("Villager News: Javafied - First-time setup"));
	}

	@Override
	protected void init() {
		int boxWidth = Math.min(360, width - 40);
		int centerX = width / 2;
		int y = height / 2 - 30;

		addRenderableWidget(new StringWidget(centerX - boxWidth / 2, y,
				boxWidth, 20,
				Component.literal("Paste the full path to your Villager News .mcaddon file:"), font));

		pathBox = addRenderableWidget(new EditBox(font, centerX - boxWidth / 2, y + 22, boxWidth, 20,
				Component.literal("mcaddon path")));
		pathBox.setHint(Component.literal("/home/you/Downloads/Villager News 1.0 Add-On RP.mcaddon"));
		pathBox.setMaxLength(1024);
		pathBox.setValue(AddonConversionManager.loadLastAddonPath());

		convertButton = addRenderableWidget(Button
				.builder(Component.literal("Convert"), btn -> convert())
				.bounds(centerX - 50, y + 48, 100, 20)
				.build());

		status = addRenderableWidget(new StringWidget(centerX - boxWidth / 2, y + 74,
				boxWidth, 20, Component.literal(""), font));
	}

	private void convert() {
		String text = pathBox.getValue().trim();
		if (text.isEmpty()) {
			status.setMessage(Component.literal("Enter a path first."));
			return;
		}

		Path source = Path.of(text);
		if (!Files.exists(source)) {
			status.setMessage(Component.literal("No such file: " + text));
			return;
		}
		AddonConversionManager.saveLastAddonPath(text);

		convertButton.active = false;
		status.setMessage(Component.literal("Converting - this can take a little while the first time..."));

		try {
			AddonConversionManager.convert(source);
			AddonConversionManager.applyConvertedPack(minecraft);
			onClose();
		} catch (Exception e) {
			VillagerNewsJavafied.LOGGER.error("Failed to convert Villager News add-on", e);
			status.setMessage(Component.literal("Conversion failed: " + e.getMessage()));
			convertButton.active = true;
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
