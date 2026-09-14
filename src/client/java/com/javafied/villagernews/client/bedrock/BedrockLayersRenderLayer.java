package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.Layer;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws every layer of a {@link RenderPlan} after the first (e.g. a
 * villager's biome clothing, profession outfit and level badge on top of its
 * skin) over the same posed model, in order. A layer with its own geometry
 * (what a villager wears) gets its own model, posed the same way.
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
			SignTextures.ensureLoaded(layer.texture());
			RenderType renderType = BedrockEntityRenderer.renderType(layer.texture(), layer.kind());
			RenderPassInfo<R> layerPass = pass;
			if (!layer.model().equals(baseModel)) {
				BakedGeoModel model = renderer.getGeoModel().getBakedModel(layer.model());
				if (model.isMissingno()) {
					if (WARNED.add(layer.model())) {
						VillagerNewsJavafied.LOGGER.warn("Missing layer geometry {}", layer.model());
					}
					continue;
				}
				ExtraModelPass<R> extra = new ExtraModelPass<>(pass, model);
				extra.addBoneUpdater(renderer::adjustModelBonesForRender);
				extra.captureModelRenderPose();
				layerPass = extra;
			}
			if (layer.uOffset() == 0 && layer.vOffset() == 0) {
				renderer.submitRenderTasks(layerPass, collector.order(i), renderType);
			} else {
				submitScrolled(layerPass, collector.order(i), renderType, layer.uOffset(), layer.vOffset());
			}
		}
	}

	/**
	 * What {@code GeoRenderer#submitRenderTasks} does, with the texture
	 * coordinates shifted: Bedrock's {@code uv_anim} (e.g. which of the 87
	 * messages on a villager's sign faces out).
	 */
	private static <R extends GeoRenderState> void submitScrolled(RenderPassInfo<R> pass, OrderedSubmitNodeCollector collector,
			RenderType renderType, float uOffset, float vOffset) {
		int light = pass.packedLight();
		int overlay = pass.packedOverlay();
		int color = pass.renderColor();
		collector.submitCustomGeometry(pass.poseStack(), renderType, (pose, vertices) -> {
			PoseStack poseStack = pass.poseStack();
			poseStack.pushPose();
			poseStack.last().set(pose);
			pass.renderPosed(() -> pass.model().render(pass, new ScrolledVertices(vertices, uOffset, vOffset), light, overlay, color));
			poseStack.popPose();
		});
	}

	private record ScrolledVertices(VertexConsumer delegate, float uOffset, float vOffset) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			delegate.setColor(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			delegate.setColor(argb);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u + uOffset, v + vOffset);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			delegate.setNormal(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			delegate.setLineWidth(width);
			return this;
		}
	}
}
