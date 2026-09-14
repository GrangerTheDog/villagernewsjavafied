package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;
import com.javafied.villagernews.names.AddonNames;

import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;

import java.util.function.Function;

/**
 * Resolves model and texture per entity from its {@link RenderPlan}: the first
 * layer is the base pass, the rest are drawn by {@link BedrockLayersRenderLayer}.
 */
public final class BedrockGeoModel extends GeoModel<BedrockAnimatable> {
	public static final DataTicket<RenderPlan> PLAN = DataTicket.create("villagernewsjavafied_render_plan", RenderPlan.class);
	/** On every render state this model filled, so it can be routed back to the add-on renderer. */
	public static final DataTicket<Boolean> FILLED = DataTicket.create("villagernewsjavafied_filled", Boolean.class);
	/** A dyeable entity's colour (a sheep's wool), for layers drawn with Bedrock's {@code sheep} material. */
	public static final DataTicket<Integer> DYE_COLOR = DataTicket.create("villagernewsjavafied_dye_color", Integer.class);

	/** Used until the add-on is converted; resolves to GeckoLib's placeholder without logging every frame. */
	private static final Identifier NOT_CONVERTED = VillagerNewsJavafied.id("entity/not_converted");

	private final Function<Entity, String> clientEntity;

	/** @param clientEntity which character (readable name, e.g. "mayor") a given Java entity is drawn as */
	public BedrockGeoModel(Function<Entity, String> clientEntity) {
		this.clientEntity = clientEntity;
	}

	@Override
	public void addAdditionalStateData(BedrockAnimatable animatable, Object relatedObject, GeoRenderState renderState) {
		renderState.addGeckolibData(FILLED, true);
		if (relatedObject instanceof Entity entity) {
			RenderPlan plan = BedrockRuntime.evaluate(entity, AddonNames.character(clientEntity.apply(entity)), renderState.getPartialTick());
			if (plan != null && !plan.layers().isEmpty()) {
				renderState.addGeckolibData(PLAN, plan);
			}
			if (entity instanceof Sheep sheep) {
				renderState.addGeckolibData(DYE_COLOR, sheep.getColor().getTextureDiffuseColor() | 0xFF000000);
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
