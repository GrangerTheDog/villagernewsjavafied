package com.javafied.villagernews.client.bedrock;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

/**
 * GeckoLib's replaced-entity system builds one shared animatable per vanilla
 * EntityType; everything that differs per entity (which add-on client entity,
 * its variables, layers) lives in {@link BedrockRuntime} and reaches the
 * renderer through the render state instead.
 */
public final class BedrockAnimatable implements GeoReplacedEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Animation controllers come with the next stage of the runtime.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
