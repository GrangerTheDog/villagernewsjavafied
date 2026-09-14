package com.javafied.villagernews.content;

import com.javafied.villagernews.VillagerNewsJavafied;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.codec.ByteBufCodecs;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
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
	 * defaults - what a villager wears, whether it still has its nose, whether
	 * it has seen the difficulty change. Synced: the add-on's client logic
	 * reads several of them through {@code q.property}.
	 */
	public static final AttachmentType<Map<String, String>> BEHAVIOR_PROPERTIES = AttachmentRegistry.create(
			VillagerNewsJavafied.id("behavior_properties"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING))
					.syncWith(ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(HashMap::new,
							ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), AttachmentSyncPredicate.all()));

	/**
	 * Set while a villager runs from something - panicking, or the Untouchable
	 * keeping away from players: Bedrock's {@code q.is_avoiding_mobs}, which
	 * the add-on's running animations read on the client.
	 */
	public static final AttachmentType<Boolean> AVOIDING = AttachmentRegistry.create(
			VillagerNewsJavafied.id("avoiding"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Whether this player was already handed the add-on's handbook. */
	public static final AttachmentType<Boolean> RECEIVED_HANDBOOK = AttachmentRegistry.create(
			VillagerNewsJavafied.id("received_handbook"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Set on villagers placed by village generation until they first load (see {@link SpecialCharacters}). */
	public static final AttachmentType<Boolean> FROM_VILLAGE_GENERATION = AttachmentRegistry.create(
			VillagerNewsJavafied.id("from_village_generation"),
			builder -> builder.persistent(Codec.BOOL));

	/** On the overworld: add-on character id -> "uuid,x,z" of where each special character appeared. */
	public static final AttachmentType<Map<String, String>> SPECIAL_CHARACTERS = AttachmentRegistry.create(
			VillagerNewsJavafied.id("special_characters"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	private ModAttachments() {
	}

	/** Forces this class (and its static registration above) to load. */
	public static void init() {
	}
}
