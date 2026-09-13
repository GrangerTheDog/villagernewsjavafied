package com.javafied.villagernews.client.geckolib;

import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.VillagerVariantKeys;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Resolves the right converted model/texture per villager instance. GeckoLib
 * only ever constructs one shared {@link VillagerReplacement} for the whole
 * vanilla EntityType, so per-instance variation has to travel through the
 * render state instead: {@link #addAdditionalStateData} stashes the real
 * entity's synced variant tag into a data ticket, which the resource getters
 * below then read back.
 */
public final class VillagerReplacementGeoModel extends GeoModel<VillagerReplacement> {
	private static final String MOD_ID = "villagernewsjavafied";
	private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(MOD_ID, "entity/8b886b15");
	private static final DataTicket<String> VARIANT = DataTicket.create("villager_variant", String.class);

	@Override
	public void addAdditionalStateData(VillagerReplacement animatable, Object relatedObject, GeoRenderState renderState) {
		String variant = relatedObject instanceof Entity entity
				? entity.getAttachedOrElse(ModAttachments.VILLAGER_VARIANT, VillagerVariantKeys.DEFAULT)
				: VillagerVariantKeys.DEFAULT;
		renderState.addGeckolibData(VARIANT, variant);
	}

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return VillagerVariants.get(renderState.getOrDefaultGeckolibData(VARIANT, VillagerVariantKeys.DEFAULT)).model();
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return VillagerVariants.get(renderState.getOrDefaultGeckolibData(VARIANT, VillagerVariantKeys.DEFAULT)).texture();
	}

	@Override
	public Identifier getAnimationResource(VillagerReplacement animatable) {
		return ANIMATION;
	}
}
