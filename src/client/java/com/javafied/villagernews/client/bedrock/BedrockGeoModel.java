package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;

import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.function.Function;

/**
 * Resolves model and texture per entity from its {@link RenderPlan}: the first
 * layer is the base pass, the rest are drawn by {@link BedrockLayersRenderLayer}.
 */
public final class BedrockGeoModel extends GeoModel<BedrockAnimatable> {
	public static final DataTicket<RenderPlan> PLAN = DataTicket.create("villagernewsjavafied_render_plan", RenderPlan.class);

	/** Used until the add-on is converted; resolves to GeckoLib's placeholder without logging every frame. */
	private static final Identifier NOT_CONVERTED = VillagerNewsJavafied.id("entity/not_converted");

	private final Function<Entity, String> clientEntity;

	/** @param clientEntity which add-on client entity (e.g. "oreville_vn:villager") a given Java entity is drawn as */
	public BedrockGeoModel(Function<Entity, String> clientEntity) {
		this.clientEntity = clientEntity;
	}

	@Override
	public void addAdditionalStateData(BedrockAnimatable animatable, Object relatedObject, GeoRenderState renderState) {
		if (relatedObject instanceof Entity entity) {
			RenderPlan plan = BedrockRuntime.evaluate(entity, clientEntity.apply(entity), renderState.getPartialTick());
			if (plan != null && !plan.layers().isEmpty()) {
				renderState.addGeckolibData(PLAN, plan);
			}
		}
	}

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		RenderPlan plan = renderState.getGeckolibData(PLAN);
		return plan == null ? NOT_CONVERTED : plan.layers().getFirst().model();
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		RenderPlan plan = renderState.getGeckolibData(PLAN);
		return plan == null ? NOT_CONVERTED : plan.layers().getFirst().texture();
	}

	@Override
	public Identifier getAnimationResource(BedrockAnimatable animatable) {
		return NOT_CONVERTED;
	}

	@Override
	public BakedGeoModel getBakedModel(Identifier location) {
		return location.equals(NOT_CONVERTED) ? BakedModelCache.MISSINGNO.get() : super.getBakedModel(location);
	}
}
