package com.javafied.villagernews.mixin;

import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.SpecialTrades;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ServerLevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The add-on's special characters trade from its own tables instead of
 * vanilla's; and villagers placed by village generation are marked, since
 * the add-on turns one of those into a special character.
 */
@Mixin(Villager.class)
abstract class VillagerMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void villagernewsjavafied$markVillageVillager(ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason reason, SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
		if (reason == EntitySpawnReason.STRUCTURE) {
			((Villager) (Object) this).setAttached(ModAttachments.FROM_VILLAGE_GENERATION, true);
		}
	}

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
