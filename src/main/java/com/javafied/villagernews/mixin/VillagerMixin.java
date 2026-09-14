package com.javafied.villagernews.mixin;

import com.javafied.villagernews.content.SpecialTrades;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** The add-on's special characters trade from its own tables instead of vanilla's. */
@Mixin(Villager.class)
abstract class VillagerMixin {
	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void villagernewsjavafied$specialTrades(ServerLevel level, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		List<MerchantOffer> offers = SpecialTrades.offers(self);
		if (!offers.isEmpty()) {
			self.getOffers().addAll(offers);
			ci.cancel();
		}
	}
}
