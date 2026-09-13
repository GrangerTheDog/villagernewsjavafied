package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.Layer;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws every layer of a {@link RenderPlan} after the first (e.g. a
 * villager's biome clothing, profession outfit and level badge on top of its
 * skin) over the same posed model, in order.
 */
final class BedrockLayersRenderLayer<O, R extends GeoRenderState> extends GeoRenderLayer<BedrockAnimatable, O, R> {
	private static final Set<Identifier> WARNED = new HashSet<>();

	BedrockLayersRenderLayer(GeoRenderer<BedrockAnimatable, O, R> renderer) {
		super(renderer);
	}

	@Override
	public void submitRenderTask(RenderPassInfo<R> pass, SubmitNodeCollector collector) {
		if (!pass.willRender()) {
			return;
		}
		RenderPlan plan = pass.getGeckolibData(BedrockGeoModel.PLAN);
		if (plan == null) {
			return;
		}
		Identifier baseModel = plan.layers().getFirst().model();
		for (int i = 1; i < plan.layers().size(); i++) {
			Layer layer = plan.layers().get(i);
			// A layer can only reuse this pass's posed bones if it's the same geometry.
			if (!layer.model().equals(baseModel)) {
				if (WARNED.add(layer.model())) {
					VillagerNewsJavafied.LOGGER.warn("Skipping layer with different geometry {} over {} (not supported yet)",
							layer.model(), baseModel);
				}
				continue;
			}
			renderer.submitRenderTasks(pass, collector.order(i), BedrockEntityRenderer.renderType(layer.texture(), layer.kind()));
		}
	}
}
