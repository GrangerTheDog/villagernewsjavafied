package com.javafied.villagernews.client.bedrock;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

/**
 * A second model drawn in an entity's render pass - same entity, pose stack
 * and camera, different geometry. The add-on's accessory geometries repeat
 * the villager's skeleton, so the same per-bone poses fit them.
 */
final class ExtraModelPass<R extends GeoRenderState> extends RenderPassInfo<R> {
	ExtraModelPass(RenderPassInfo<R> base, BakedGeoModel model) {
		super(base.renderer(), base.renderState(), base.poseStack(), model, base.cameraState(), base.willRender());
	}
}
