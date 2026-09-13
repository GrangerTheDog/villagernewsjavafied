package com.javafied.villagernews.client.geckolib;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Singleton "replacement skin" for every addon villager reskin, rendered on
 * top of the real vanilla Villager (no new EntityType, vanilla AI/trading
 * reused as-is). Which specific look a given instance gets is resolved per
 * render in {@link VillagerReplacementGeoModel} from a synced attachment, not
 * here - GeckoLib's replaced-entity system only ever constructs one shared
 * animatable per vanilla EntityType.
 */
public final class VillagerReplacement implements GeoReplacedEntity {
	public static final VillagerReplacement INSTANCE = new VillagerReplacement();

	private static final String IDLE_ANIMATION = "animation.oreville_vn.dapvpm";

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private VillagerReplacement() {
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("idle",
				test -> test.setAndContinue(RawAnimation.begin().thenLoop(IDLE_ANIMATION))));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
