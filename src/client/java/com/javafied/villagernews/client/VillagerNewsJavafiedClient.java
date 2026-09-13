package com.javafied.villagernews.client;

import com.javafied.villagernews.client.geckolib.WoolySheepGeoModel;
import com.javafied.villagernews.client.geckolib.WoolySheepReplacement;

import com.geckolib.renderer.GeoReplacedEntityRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.world.entity.EntityTypes;

public class VillagerNewsJavafiedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(EntityTypes.SHEEP,
				context -> new GeoReplacedEntityRenderer<>(context, new WoolySheepGeoModel(), WoolySheepReplacement.INSTANCE));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (AddonConversionManager.isConverted()) {
				AddonConversionManager.applyConvertedPack(client);
			} else {
				client.setScreenAndShow(new AddonPickerScreen());
			}
		});
	}
}
