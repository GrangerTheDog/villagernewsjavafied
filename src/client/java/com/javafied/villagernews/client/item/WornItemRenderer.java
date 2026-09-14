package com.javafied.villagernews.client.item;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockGeoModel;
import com.javafied.villagernews.client.bedrock.BedrockRuntime;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.Pose;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;
import com.javafied.villagernews.content.WearableItem;
import com.javafied.villagernews.names.AddonNames;

import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Draws a worn item with the add-on's 3D model: {@code geckolib/models/item/<id>}
 * (re-rooted by the converter under {@code armorHead}) and
 * {@code textures/attachable/<id>.png} - animated by its attachable as the
 * wearer has it on, so the villager nose swings on its spring.
 */
final class WornItemRenderer extends GeoArmorRenderer<WearableItem, HumanoidRenderState> {
	/** The converter's bone for GeckoLib's armour placement; it follows the wearer's head, not the add-on's poses. */
	private static final String ARMOR_BONE_PREFIX = "armor";

	private final String attachable;

	WornItemRenderer(WearableItem item) {
		super(new Model(BuiltInRegistries.ITEM.getKey(item).getPath()));
		this.attachable = AddonNames.item(BuiltInRegistries.ITEM.getKey(item).getPath());
	}

	@Override
	public void captureDefaultRenderState(WearableItem item, RenderData data, HumanoidRenderState state, float partialTick) {
		super.captureDefaultRenderState(item, data, state, partialTick);
		RenderPlan plan = data.entity() == null ? null : BedrockRuntime.evaluateWorn(data.entity(), attachable, partialTick);
		if (plan != null) {
			state.addGeckolibData(BedrockGeoModel.PLAN, plan);
		}
	}

	/** The add-on's poses, as {@code BedrockEntityRenderer} applies them. */
	@Override
	public void adjustModelBonesForRender(RenderPassInfo<HumanoidRenderState> pass, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(pass, snapshots);
		RenderPlan plan = pass.getGeckolibData(BedrockGeoModel.PLAN);
		if (plan == null) {
			return;
		}
		for (String bone : pass.model().boneLookup().get().keySet()) {
			Pose pose = bone.startsWith(ARMOR_BONE_PREFIX) ? null : plan.poses().get(bone.toLowerCase(Locale.ROOT));
			if (pose != null) {
				snapshots.ifPresent(bone, snapshot -> {
					snapshot.setRotation((float) -Math.toRadians(pose.rx), (float) -Math.toRadians(pose.ry), (float) Math.toRadians(pose.rz));
					snapshot.setTranslation((float) pose.px, (float) pose.py, (float) pose.pz);
					snapshot.setScale((float) pose.sx, (float) pose.sy, (float) pose.sz);
				});
			}
		}
	}

	private static final class Model extends GeoModel<WearableItem> {
		private final Identifier model;
		private final Identifier texture;

		Model(String path) {
			this.model = VillagerNewsJavafied.id("item/" + path);
			this.texture = VillagerNewsJavafied.id("textures/attachable/" + path + ".png");
		}

		@Override
		public Identifier getModelResource(GeoRenderState renderState) {
			return model;
		}

		@Override
		public Identifier getTextureResource(GeoRenderState renderState) {
			return texture;
		}

		@Override
		public Identifier getAnimationResource(WearableItem animatable) {
			return model;
		}

		/** Not converted yet: GeckoLib's placeholder instead of an error every frame. */
		@Override
		public BakedGeoModel getBakedModel(Identifier location) {
			try {
				return super.getBakedModel(location);
			} catch (RuntimeException e) {
				return BakedModelCache.MISSINGNO.get();
			}
		}
	}
}
