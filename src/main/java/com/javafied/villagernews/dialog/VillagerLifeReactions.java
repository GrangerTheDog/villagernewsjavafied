package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
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
	private static final String SPAWNED_BY_EGG = "vskjkl";
	private static final String SPAWNED_BY_EGG_BABY = "abfwiv";
	private static final String CELEBRATING = "wbbxpo";
	private static final String CELEBRATING_BABY = "wsxfok";
	private static final String CANNOT_TRADE = "zalmof";
	private static final String NITWIT_CANNOT_TRADE = "nukxsf";
	private static final String UNEMPLOYED_CANNOT_TRADE = "nlbhku";
	// Time jumping (sleeping through the night, /time set): day, night, or just "time skipped" (adult/baby).
	private static final String SKIPPED_TO_DAY = "mgmzeh";
	private static final String SKIPPED_TO_DAY_BABY = "wkwcrf";
	private static final String SKIPPED_TO_NIGHT = "ohdwnz";
	private static final String SKIPPED_TO_NIGHT_BABY = "msemoe";
	private static final String TIME_SKIPPED = "uqwdqn";
	private static final String TIME_SKIPPED_BABY = "durjjd";
	private static final int TIME_SKIP_THRESHOLD = 4000;

	private static final java.util.Set<Villager> celebrating = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
	private static long lastTimeOfDay = -1;

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
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager villager) || villager.isSleeping()
					|| !(level instanceof ServerLevel) || Speakers.kindOf(villager) != Speakers.Kind.VILLAGER) {
				return InteractionResult.PASS;
			}
			var held = player.getItemInHand(hand);
			if (villager.isBaby()) {
				if (!held.is(Items.SHEARS) && !held.is(Items.NAME_TAG) && !held.is(Items.VILLAGER_SPAWN_EGG)) {
					Reactions.say(villager, BABY_ASKED_TO_TRADE, Options.DEFAULT.withStates(State.BABY).facing(player));
				}
			} else {
				String profession = Speakers.profession(villager);
				if (profession.equals("nitwit") || profession.equals("none")) {
					String special = profession.equals("nitwit") ? NITWIT_CANNOT_TRADE : UNEMPLOYED_CANNOT_TRADE;
					String dialog = java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? CANNOT_TRADE : special;
					Reactions.say(villager, dialog, Options.DEFAULT.facing(player));
				}
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (DialogEngine.get() == null) {
				return;
			}
			if (server.getTickCount() % 25 == 0) {
				timeSkip(server);
			}
			if (server.getTickCount() % 20 == 0) {
				celebrations(server);
			}
		});
	}

	/** Called (through a mixin) the tick after an ordinary villager was spawned from an egg or by command. */
	public static void spawnedByPlayer(Villager villager) {
		if (Speakers.kindOf(villager) == Speakers.Kind.VILLAGER) {
			Reactions.sayByAge(villager, SPAWNED_BY_EGG, SPAWNED_BY_EGG_BABY, Options.DEFAULT.forced().asUrgent());
		}
	}

	/** After a raid is won, celebrating villagers say so. */
	private static void celebrations(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Reactions.NEARBY))) {
					boolean now = villager.getBrain().isActive(Activity.CELEBRATE);
					if (now && celebrating.add(villager)) {
						Reactions.sayByAge(villager, CELEBRATING, CELEBRATING_BABY, Options.DEFAULT);
					} else if (!now) {
						celebrating.remove(villager);
					}
				}
			}
		}
	}

	/** A jump of more than 4000 ticks in the time of day gets comments: about the new day or night, and the skip itself. */
	private static void timeSkip(MinecraftServer server) {
		long time = Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
		long previous = lastTimeOfDay;
		lastTimeOfDay = time;
		if (previous < 0) {
			return;
		}
		long jump = Math.abs(time - previous);
		if (Math.min(jump, 24000 - jump) <= TIME_SKIP_THRESHOLD) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Options options = Options.DEFAULT.facing(player);
			boolean day = time < 11000;
			boolean night = time > 13500 && time < 22500;
			if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() <= 0.7 && (day || night)) {
				Reactions.nearestByAge(player.level(), player.position(), day ? SKIPPED_TO_DAY : SKIPPED_TO_NIGHT,
						day ? SKIPPED_TO_DAY_BABY : SKIPPED_TO_NIGHT_BABY, options, Reactions.NEARBY, null);
			}
			Reactions.nearestByAge(player.level(), player.position(), TIME_SKIPPED, TIME_SKIPPED_BABY, options, Reactions.NEARBY, null);
		}
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
