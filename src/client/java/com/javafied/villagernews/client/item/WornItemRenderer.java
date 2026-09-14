package com.javafied.villagernews.client.item;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.content.WearableItem;

import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Draws a worn item with the add-on's 3D model: {@code geckolib/models/item/<id>}
 * (re-rooted by the converter under {@code armorHead}) and
 * {@code textures/attachable/<id>.png}.
 */
final class WornItemRenderer extends GeoArmorRenderer<WearableItem, HumanoidRenderState> {
	WornItemRenderer(WearableItem item) {
		super(new Model(BuiltInRegistries.ITEM.getKey(item).getPath()));
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
