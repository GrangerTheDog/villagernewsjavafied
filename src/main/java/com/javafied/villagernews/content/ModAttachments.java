package com.javafied.villagernews.content;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

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

	private ModAttachments() {
	}

	/** Forces this class (and its static registration above) to load. */
	public static void init() {
	}
}
