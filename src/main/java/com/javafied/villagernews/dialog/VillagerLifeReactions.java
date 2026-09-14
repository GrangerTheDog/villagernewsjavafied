package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Hand port of the add-on script's reactions to villager life: growing up,
 * babies being born, babies playing, villagers seeing someone get hurt or a
 * fellow villager die, iron golems picking a fight with a player, and babies
 * being asked to trade.
 */
public final class VillagerLifeReactions {
	private static final String GREW_UP = "smvnbj";
	private static final String BORN = "lgjtnf";
	private static final String PARENTS_WELCOME_BABY = "fbuabj";
	private static final String BABY_SPRINTS = "vhwksn";
	private static final String BABY_SPRINTS_WEEKEND = "vbclem";
	private static final String BABIES_PLAY_CHASE = "rfnirh";
	private static final String SAW_SOMETHING_HURT = "pkvhpv";
	private static final String SAW_VILLAGER_DIE = "pmqrpb";
	private static final String IRON_GOLEM_FIGHTS_PLAYER = "qffeco";
	private static final String BABY_ASKED_TO_TRADE = "aezdiy";

	private VillagerLifeReactions() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive() || damageTaken <= 0) {
				return;
			}
			if (entity instanceof IronGolem && source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
				Reactions.nearest(level, entity.position(), IRON_GOLEM_FIGHTS_PLAYER,
						Options.DEFAULT.facing(player).ignoringCooldowns(false, false, true));
			} else if (entity instanceof ServerPlayer player && source.getEntity() instanceof IronGolem && !player.isCreative()) {
				Reactions.nearest(level, entity.position(), IRON_GOLEM_FIGHTS_PLAYER,
						Options.DEFAULT.facing(player).ignoringCooldowns(false, false, true));
			}
			Reactions.nearest(level, entity.position(), SAW_SOMETHING_HURT, Options.DEFAULT.facing(entity), Reactions.NEARBY, entity);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && Speakers.kindOf(entity) != null && Speakers.kindOf(entity) != Speakers.Kind.WOOLY) {
				Reactions.nearest(level, entity.position(), SAW_VILLAGER_DIE, Options.DEFAULT.facing(entity.position()));
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && entity instanceof Villager villager && villager.isBaby() && !villager.isSleeping()
					&& level instanceof ServerLevel) {
				var held = player.getItemInHand(hand);
				if (!held.is(Items.SHEARS) && !held.is(Items.NAME_TAG) && !held.is(Items.VILLAGER_SPAWN_EGG)) {
					Reactions.say(villager, BABY_ASKED_TO_TRADE, Options.DEFAULT.withStates(State.BABY).facing(player));
				}
			}
			return InteractionResult.PASS;
		});
	}

	/** Called (through a mixin) when a baby villager grows up. */
	public static void grewUp(Villager villager) {
		Reactions.say(villager, GREW_UP, Options.DEFAULT);
	}

	/** Called (through a mixin) the tick after two villagers had a baby. */
	public static void born(ServerLevel level, Villager baby) {
		Reactions.say(baby, BORN, Options.DEFAULT.withStates(State.BABY));
		Reactions.nearest(level, baby.position(), PARENTS_WELCOME_BABY, Options.DEFAULT.facing(baby), 2, null);
	}

	/**
	 * From the idle trigger: a baby running around in the daytime says
	 * something - "let's play chase" if another baby is running close by.
	 */
	static boolean babyAtPlay(Villager baby) {
		if (!sprinting(baby)) {
			return false;
		}
		List<Villager> others = baby.level().getEntitiesOfClass(Villager.class, baby.getBoundingBox().inflate(8),
				v -> v != baby && v.distanceTo(baby) <= 8 && sprinting(v));
		DayOfWeek day = LocalDate.now().getDayOfWeek();
		String dialog = !others.isEmpty() ? BABIES_PLAY_CHASE
				: day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? BABY_SPRINTS_WEEKEND : BABY_SPRINTS;
		return Reactions.say(baby, dialog, Options.DEFAULT.withStates(State.BABY));
	}

	/** The add-on's "running" baby: fast (0.18 blocks/tick) and in the daytime (ticks 0-11000). */
	private static boolean sprinting(Villager villager) {
		long time = Math.floorMod(villager.level().getOverworldClockTime(), 24000L);
		return villager.isBaby() && time < 11000 && villager.getDeltaMovement().horizontalDistance() >= 0.18;
	}
}
