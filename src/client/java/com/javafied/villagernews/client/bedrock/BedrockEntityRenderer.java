package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.client.bedrock.BedrockRuntime.Pose;
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

import java.util.Locale;

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

	/** The first layer's material; invisible entities as GeckoLib has them (hidden, or see-through to spectators). */
	@Override
	public RenderType getRenderType(R renderState, Identifier texture) {
		RenderPlan plan = renderState.getGeckolibData(BedrockGeoModel.PLAN);
		return plan == null || renderState.isInvisible ? super.getRenderType(renderState, texture)
				: renderType(texture, plan.layers().getFirst().kind());
	}

	/**
	 * Applies the runtime's animation poses and part visibility. Bedrock
	 * values map onto GeckoLib's snapshot exactly as GeckoLib's own animation
	 * loader maps them: rotation in radians with X and Y negated, position in
	 * model pixels (GeckoLib negates X itself when translating), scale as-is.
	 * Snapshots are offsets from the bind pose, like Bedrock animation values.
	 */
	@Override
	public void adjustModelBonesForRender(RenderPassInfo<R> pass, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(pass, snapshots);
		RenderPlan plan = pass.getGeckolibData(BedrockGeoModel.PLAN);
		if (plan == null) {
			return;
		}
		for (String bone : pass.model().boneLookup().get().keySet()) {
			Pose pose = plan.poses().get(bone.toLowerCase(Locale.ROOT));
			boolean hidden = !plan.isBoneVisible(bone);
			if (pose == null && !hidden) {
				continue;
			}
			snapshots.ifPresent(bone, snapshot -> {
				if (pose != null) {
					snapshot.setRotation((float) -Math.toRadians(pose.rx), (float) -Math.toRadians(pose.ry), (float) Math.toRadians(pose.rz));
					snapshot.setTranslation((float) pose.px, (float) pose.py, (float) pose.pz);
					snapshot.setScale((float) pose.sx, (float) pose.sy, (float) pose.sz);
				}
				if (hidden) {
					snapshot.skipRender(true);
				}
			});
		}
	}

	@Override
	public void scaleModelForRender(RenderPassInfo<R> pass, float widthScale, float heightScale) {
		RenderPlan plan = pass.getGeckolibData(BedrockGeoModel.PLAN);
		if (plan == null) {
			super.scaleModelForRender(pass, widthScale, heightScale);
			return;
		}
		// Before scaling, so the lift is in world blocks like the script's teleport.
		pass.poseStack().translate(0, plan.lift(), 0);
		super.scaleModelForRender(pass, widthScale * plan.scale(), heightScale * plan.scale());
	}
}
