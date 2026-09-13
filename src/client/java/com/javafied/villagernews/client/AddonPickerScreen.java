package com.javafied.villagernews.client;

import com.javafied.villagernews.VillagerNewsJavafied;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Shown once, on first launch, when no converted addon assets are found yet.
 * Lets the user point at their own legally-owned Villager News .mcaddon; the
 * mod never bundles or downloads that content itself.
 */
public final class AddonPickerScreen extends Screen {
	private Button pickButton;

	public AddonPickerScreen() {
		super(Component.literal("Villager News: Javafied - First-time setup"));
	}

	@Override
	protected void init() {
		pickButton = addRenderableWidget(Button
				.builder(Component.literal("Select Villager News .mcaddon..."), btn -> pickFile())
				.bounds(width / 2 - 100, height / 2 - 10, 200, 20)
				.build());
	}

	private void pickFile() {
		pickButton.active = false;

		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Villager News add-on (.mcaddon)", "mcaddon"));
		int result = chooser.showOpenDialog(null);
		if (result != JFileChooser.APPROVE_OPTION) {
			pickButton.active = true;
			return;
		}

		Path chosen = chooser.getSelectedFile().toPath();
		try {
			AddonConversionManager.convert(chosen);
			AddonConversionManager.applyConvertedPack(minecraft);
			onClose();
		} catch (IOException e) {
			VillagerNewsJavafied.LOGGER.error("Failed to convert Villager News add-on", e);
			pickButton.active = true;
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
