package com.javafied.villagernews;

import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.ModItems;
import com.javafied.villagernews.content.VillagerCommand;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerNewsJavafied implements ModInitializer {
	public static final String MOD_ID = "villagernewsjavafied";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Villager News: Javafied initializing");
		ModAttachments.init();
		ModItems.init();
		VillagerCommand.init();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
