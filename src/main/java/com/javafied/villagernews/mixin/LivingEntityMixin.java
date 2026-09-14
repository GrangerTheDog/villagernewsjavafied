package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.HurtReactions;
import com.javafied.villagernews.dialog.PlayerActionReactions;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers comment on players eating, and on witches' potions taking effect on them. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Shadow
	public abstract net.minecraft.world.item.ItemStack getUseItem();

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("RETURN"))
	private void villagernewsjavafied$effectAdded(MobEffectInstance effect, Entity source,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (cir.getReturnValueZ() && !self.level().isClientSide()) {
			HurtReactions.effectAdded(self, effect.getEffect(), source);
		}
	}

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void villagernewsjavafied$ate(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && getUseItem().has(DataComponents.FOOD)) {
			PlayerActionReactions.ate(player);
		}
	}
}
