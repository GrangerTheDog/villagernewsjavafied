package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;

import com.geckolib.renderer.GeoReplacedEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Draws a vanilla entity as an add-on client entity, following the plan
 * {@link BedrockRuntime} computes each frame: base layer here, the rest via
 * {@link BedrockLayersRenderLayer}, render controller part visibility, and
 * the client entity's {@code scale} script.
 */
public class BedrockEntityRenderer<E extends Entity, R extends EntityRenderState>
		extends GeoReplacedEntityRenderer<BedrockAnimatable, E, R> {
	public BedrockEntityRenderer(EntityRendererProvider.Context context, BedrockGeoModel model, BedrockAnimatable animatable) {
		super(context, model, animatable);
		withRenderLayer(new BedrockLayersRenderLayer<>(this));
	}

	static RenderType renderType(Identifier texture, BedrockMaterials.Kind kind) {
		return switch (kind) {
			case TRANSLUCENT -> RenderTypes.entityTranslucent(texture);
			case EMISSIVE -> RenderTypes.entityTranslucentEmissive(texture);
			default -> RenderTypes.entityCutout(texture);
		};
	}

	@Override
	public RenderType getRenderType(R renderState, Identifier texture) {
		RenderPlan plan = renderState.getGeckolibData(BedrockGeoModel.PLAN);
		return plan == null ? super.getRenderType(renderState, texture) : renderType(texture, plan.layers().getFirst().kind());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<R> pass, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(pass, snapshots);
		RenderPlan plan = pass.getGeckolibData(BedrockGeoModel.PLAN);
		if (plan == null || plan.boneVisibility().isEmpty()) {
			return;
		}
		for (String bone : pass.model().boneLookup().get().keySet()) {
			if (!plan.isBoneVisible(bone)) {
				snapshots.ifPresent(bone, snapshot -> snapshot.skipRender(true));
			}
		}
	}

	@Override
	public void scaleModelForRender(RenderPassInfo<R> pass, float widthScale, float heightScale) {
		RenderPlan plan = pass.getGeckolibData(BedrockGeoModel.PLAN);
		float scale = plan == null ? 1f : plan.scale();
		super.scaleModelForRender(pass, widthScale * scale, heightScale * scale);
	}
}
