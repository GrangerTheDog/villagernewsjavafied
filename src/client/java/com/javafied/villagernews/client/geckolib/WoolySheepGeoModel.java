package com.javafied.villagernews.client.geckolib;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

import net.minecraft.resources.Identifier;

/**
 * Points at the specific converted geo/texture/animation files for "Wooly The
 * Sheep" (identifiers looked up once from a real conversion of the addon).
 * The full generic, manifest-driven model (any converted entity, not just
 * this one hardcoded variant) is follow-up work - see the plan.
 */
public final class WoolySheepGeoModel extends GeoModel<WoolySheepReplacement> {
	private static final String MOD_ID = "villagernewsjavafied";
	// GeckoLib's own loader prepends "geo/"/"animations/" itself (it told us so via
	// a "superfluous prefix" error when we included it) - these identifiers are
	// just "entity/<name>", not the literal file path under assets/.
	private static final Identifier MODEL =
			Identifier.fromNamespaceAndPath(MOD_ID, "entity/geometry_oreville_vn_-650401518");
	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath(MOD_ID, "textures/oreville/vn/diw.png");
	private static final Identifier ANIMATION =
			Identifier.fromNamespaceAndPath(MOD_ID, "entity/8b886b15");

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(WoolySheepReplacement animatable) {
		return ANIMATION;
	}
}
