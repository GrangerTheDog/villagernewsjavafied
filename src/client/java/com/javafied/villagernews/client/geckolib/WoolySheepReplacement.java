package com.javafied.villagernews.client.geckolib;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Vertical-slice proof that a converted Bedrock reskin (here: "Wooly The
 * Sheep") renders through GeckoLib's replaced-entity system on top of the
 * real vanilla Sheep - no new EntityType, no custom AI, matching how Bedrock
 * itself only reskins the vanilla mob for this entity.
 *
 * <p>Hardcoded to one converted raw animation for now. The addon's actual
 * animation-controller state machine (idle/walk/run/jeb_-rainbow etc, driven
 * by Molang) and per-instance variant selection are follow-up work - this
 * only proves the converted geometry/texture/animation pipeline renders.
 */
public final class WoolySheepReplacement implements GeoReplacedEntity {
	public static final WoolySheepReplacement INSTANCE = new WoolySheepReplacement();

	private static final String IDLE_ANIMATION = "animation.oreville_vn.hgejgd";

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private WoolySheepReplacement() {
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
