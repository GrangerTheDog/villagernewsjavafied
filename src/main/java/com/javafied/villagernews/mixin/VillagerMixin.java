package com.javafied.villagernews.mixin;

import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.SpecialTrades;
import com.javafied.villagernews.dialog.VillagerLifeReactions;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
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

	@Inject(method = "ageBoundaryReached", at = @At("TAIL"))
	private void villagernewsjavafied$grewUp(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby() && self.level() instanceof ServerLevel) {
			VillagerLifeReactions.grewUp(self);
		}
	}

	@Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/villager/Villager;",
			at = @At("RETURN"))
	private void villagernewsjavafied$born(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<Villager> cir) {
		Villager baby = cir.getReturnValue();
		if (baby != null) {
			// Not in the world yet: react once it's been added.
			level.getServer().schedule(new TickTask(level.getServer().getTickCount() + 1, () -> {
				if (baby.isAlive() && baby.level().getEntity(baby.getId()) == baby) {
					VillagerLifeReactions.born(level, baby);
				}
			}));
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
