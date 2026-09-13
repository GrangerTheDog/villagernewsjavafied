package com.javafied.villagernews.client;

import com.javafied.villagernews.client.geckolib.VillagerReplacement;
import com.javafied.villagernews.client.geckolib.VillagerReplacementGeoModel;
import com.javafied.villagernews.client.geckolib.WoolySheepGeoModel;
import com.javafied.villagernews.client.geckolib.WoolySheepReplacement;

import com.geckolib.renderer.GeoReplacedEntityRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.EntityTypes;

public class VillagerNewsJavafiedClient implements ClientModInitializer {
	private boolean promptedThisSession = false;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(EntityTypes.SHEEP,
				context -> new GeoReplacedEntityRenderer<>(context, new WoolySheepGeoModel(), WoolySheepReplacement.INSTANCE));
		EntityRendererRegistry.register(EntityTypes.VILLAGER,
				context -> new GeoReplacedEntityRenderer<>(context, new VillagerReplacementGeoModel(), VillagerReplacement.INSTANCE));

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
