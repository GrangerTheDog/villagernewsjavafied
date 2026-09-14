package com.javafied.villagernews.client.bedrock;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

/**
 * A second model drawn in an entity's render pass - same entity, pose stack
 * and camera, different geometry. The add-on's accessory geometries repeat
 * the villager's skeleton, so the same per-bone poses fit them. Optionally
 * tinted (a sheep's dye colour over its wool).
 */
final class ExtraModelPass<R extends GeoRenderState> extends RenderPassInfo<R> {
	private final int tint;

	ExtraModelPass(RenderPassInfo<R> base, BakedGeoModel model) {
		this(base, model, 0xFFFFFFFF);
	}

	ExtraModelPass(RenderPassInfo<R> base, BakedGeoModel model, int tint) {
		super(base.renderer(), base.renderState(), base.poseStack(), model, base.cameraState(), base.willRender());
		this.tint = tint;
	}

	@Override
	public int renderColor() {
		int base = super.renderColor();
		if (tint == 0xFFFFFFFF) {
			return base;
		}
		int a = (base >>> 24) * (tint >>> 24) / 255;
		int r = (base >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
		int g = (base >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
		int b = (base & 0xFF) * (tint & 0xFF) / 255;
		return a << 24 | r << 16 | g << 8 | b;
	}
}
