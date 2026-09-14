package com.javafied.villagernews.mixin;

import com.javafied.villagernews.dialog.PlayerActionReactions;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers comment on players eating. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Shadow
	public abstract net.minecraft.world.item.ItemStack getUseItem();

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void villagernewsjavafied$ate(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && getUseItem().has(DataComponents.FOOD)) {
			PlayerActionReactions.ate(player);
		}
	}
}
