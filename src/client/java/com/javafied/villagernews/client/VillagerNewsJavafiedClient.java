package com.javafied.villagernews.client;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockAnimatable;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions;
import com.javafied.villagernews.client.bedrock.BedrockEntityRenderer;
import com.javafied.villagernews.client.bedrock.BedrockGeoModel;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.VillagerVariantKeys;
import com.javafied.villagernews.molang.MolangProgram;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EntityTypes;

public class VillagerNewsJavafiedClient implements ClientModInitializer {
	/** The add-on's one sheep reskin. Hand-picked for now; mapping vanilla mobs to reskins from the manifest comes later. */
	private static final String SHEEP_CLIENT_ENTITY = "mlkxjo";

	private boolean promptedThisSession = false;

	@Override
	public void onInitializeClient() {
		MolangProgram.setErrorReporter(VillagerNewsJavafied.LOGGER::warn);
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(BedrockDefinitions.ID, new BedrockDefinitions());
		DialogClient.init();

		EntityRendererRegistry.register(EntityTypes.SHEEP, context -> new BedrockEntityRenderer<>(context,
				new BedrockGeoModel(entity -> SHEEP_CLIENT_ENTITY), new BedrockAnimatable()));
		EntityRendererRegistry.register(EntityTypes.VILLAGER, context -> new BedrockEntityRenderer<>(context,
				new BedrockGeoModel(entity -> entity.getAttachedOrElse(ModAttachments.VILLAGER_VARIANT, VillagerVariantKeys.DEFAULT)),
				new BedrockAnimatable()));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (AddonConversionManager.isConverted()) {
				AddonConversionManager.applyConvertedPack(client);
			}
		});

		// CLIENT_STARTED fires before Minecraft's own initial-screen queue (EULA/
		// ban notices/title screen) has run, so setting our screen there gets
		// immediately clobbered. Waiting for the title screen to actually show
		// is the point it's safe to take over.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (promptedThisSession || AddonConversionManager.isConverted()) {
				return;
			}
			if (client.gui.screen() instanceof TitleScreen) {
				promptedThisSession = true;
				client.setScreenAndShow(new AddonPickerScreen());
			}
		});
	}
}
