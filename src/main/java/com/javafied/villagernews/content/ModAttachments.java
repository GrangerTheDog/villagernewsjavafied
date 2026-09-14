package com.javafied.villagernews.content;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

import java.util.Map;

/**
 * Per-entity data the vanilla entities we render through don't otherwise
 * carry - which addon reskin a given vanilla Villager instance should look
 * like. Synced to all nearby players since rendering happens client-side.
 */
public final class ModAttachments {
	public static final AttachmentType<String> VILLAGER_VARIANT = AttachmentRegistry.create(
			VillagerNewsJavafied.id("villager_variant"),
			builder -> builder.persistent(Codec.STRING)
					.initializer(() -> VillagerVariantKeys.DEFAULT)
					.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/**
	 * The add-on's behavior properties ({@code p:...}) that differ from their
	 * defaults, e.g. whether a villager has seen the difficulty change. Server
	 * side only for now.
	 */
	public static final AttachmentType<Map<String, String>> BEHAVIOR_PROPERTIES = AttachmentRegistry.create(
			VillagerNewsJavafied.id("behavior_properties"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	private ModAttachments() {
	}

	/** Forces this class (and its static registration above) to load. */
	public static void init() {
	}
}
