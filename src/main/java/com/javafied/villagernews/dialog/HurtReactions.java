package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;
import com.javafied.villagernews.dialog.Speakers.Kind;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hand port of the add-on script's reactions to villagers being hurt: by
 * whom (a player and with what, a raider, a witch, a zombie...), by what
 * (falling, fire, lava, cactus, suffocating...), attacked at home (a witness
 * chimes in), the special characters' own lines, calming down afterwards,
 * witch potions, and players teleporting with ender pearls.
 */
public final class HurtReactions {
	private static final String BABY_HURT_BY_PLAYER = "ahcvzd";
	private static final String HIT_BY_FIREWORK = "gesjov";
	private static final String HIT_BY_SPLASH_POTION = "hmadgp";
	private static final String ATTACKED_AT_HOME = "lpuocy";
	private static final String ATTACKED_AT_HOME_WITNESSED = "slbqfwswxeva";
	private static final String WITNESS_RESPONDS = "slbqfwbayahw";
	private static final String HIT_BY_SWORD = "rueszy";
	private static final String HIT_BY_AXE = "yjctyw";
	private static final String HIT_BY_SHOVEL = "hpnsfu";
	private static final String HIT_BY_HOE = "qqyjjg";
	private static final String HIT = "vevdkl";
	private static final String HIT_BY_WITCH = "rtikom";
	private static final String HIT_BY_HARMING_POTION = "gacgtq";
	/** Hurt by a mob, and whether to wait until it's gone (or over 10 blocks away) to say it. */
	private record MobLine(String dialog, boolean waitUntilGone) {
	}

	private static final Map<String, MobLine> HIT_BY_MOB = Map.of("zoglin", new MobLine("hfmwvf", true),
			"vindicator", new MobLine("swomdw", true), "evoker", new MobLine("nkcoqb", false),
			"ravager", new MobLine("uveohs", true), "vex", new MobLine("caykki", true), "zombie", new MobLine("mytmrk", true),
			"pillager", new MobLine("qhpyaw", true), "tnt", new MobLine("fcbygh", false));
	/** Hurt by the world, and whether that line ignores the speaker's and the world's cooldowns. */
	private record CauseLine(String dialog, boolean ignoreCooldowns) {
	}

	private static final Map<ResourceKey<DamageType>, CauseLine> HIT_BY_CAUSE = Map.ofEntries(
			Map.entry(DamageTypes.FALLING_ANVIL, new CauseLine("yzqpvi", true)),
			Map.entry(DamageTypes.FALLING_STALACTITE, new CauseLine("nsxmkr", true)),
			Map.entry(DamageTypes.STALAGMITE, new CauseLine("nsxmkr", true)),
			Map.entry(DamageTypes.CACTUS, new CauseLine("rogpvp", false)),
			Map.entry(DamageTypes.SWEET_BERRY_BUSH, new CauseLine("rogpvp", false)),
			Map.entry(DamageTypes.FREEZE, new CauseLine("igebly", false)), Map.entry(DamageTypes.FALL, new CauseLine("cifbit", true)),
			Map.entry(DamageTypes.IN_FIRE, new CauseLine("etkxko", false)), Map.entry(DamageTypes.ON_FIRE, new CauseLine("etkxko", false)),
			Map.entry(DamageTypes.CAMPFIRE, new CauseLine("etkxko", false)), Map.entry(DamageTypes.LAVA, new CauseLine("elryje", false)),
			Map.entry(DamageTypes.IN_WALL, new CauseLine("vnaodx", false)));
	private static final Map<String, String> HIT_BY_PROJECTILE = Map.of("arrow", "huhcbd", "spectral_arrow", "huhcbd",
			"snowball", "dlrxes");
	private static final String FREED_FROM_SUFFOCATING = "fxbysi";
	private static final int SUFFOCATION_ESCAPE_TICKS = 30;
	private static final String CALMED_DOWN = "wbbxpo";
	private static final String CALMED_DOWN_BABY = "wsxfok";
	private static final int CALM_DOWN_TICKS = 200;
	/** Special characters hurt: by a player / by anything else. */
	private record CharacterLines(String byPlayer, String otherwise) {
	}

	private static final Map<Kind, CharacterLines> CHARACTER_HURT = Map.of(Kind.MAYOR, new CharacterLines("ltdnvy", "ssbhiv"),
			Kind.TRADER, new CharacterLines("vevdkl", null), Kind.TESTIFICATE_MAN, new CharacterLines("fzoqwd", "fzoqwd"),
			Kind.NUMBER_5, new CharacterLines("behifz", "behifz"), Kind.NUMBER_9, new CharacterLines("asuufu", "wrbvvp"),
			Kind.WOOLY, new CharacterLines("ncyeaw", "eyiraw"));
	/** Witches' potions taking effect on a villager, by effect id. */
	private static final Map<String, String> WITCH_POTION = Map.of("weakness", "yebifs", "slowness", "xemyaj", "poison", "onindz");
	private static final String ENDER_PEARL_TELEPORT = "mewsnd";

	private static final Map<LivingEntity, Long> lastHurt = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> lastSuffocation = new WeakHashMap<>();
	/** A villager attacked at home in front of a neighbour -> that neighbour, who answers afterwards. */
	private static final Map<Villager, Villager> witnesses = new WeakHashMap<>();

	private HurtReactions() {
	}

	public static void init() {
		DialogEngine.onFinished((speech, completed) -> {
			if (speech.dialog().id().equals(ATTACKED_AT_HOME_WITNESSED) && speech.speaker() instanceof Villager victim) {
				Villager witness = witnesses.remove(victim);
				DialogEngine engine = DialogEngine.get();
				if (witness != null && witness.isAlive() && engine != null && !engine.isTalking(witness)) {
					hurtLine(witness, WITNESS_RESPONDS, victim, true);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0 && entity.isAlive() && entity.level() instanceof ServerLevel level && DialogEngine.get() != null) {
				hurt(level, entity, source);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
					&& BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("ender_pearl")
					&& entity instanceof Projectile pearl && pearl.getOwner() instanceof ServerPlayer player) {
				var landed = pearl.position();
				later(level.getServer(), 1, () -> {
					if (player.isAlive() && !player.isSpectator() && player.position().distanceTo(landed) <= 2) {
						Reactions.nearest(player.level(), landed, ENDER_PEARL_TELEPORT, Options.DEFAULT.facing(player));
					}
				});
			}
		});
	}

	private static void hurt(ServerLevel level, LivingEntity entity, DamageSource source) {
		Kind kind = Speakers.kindOf(entity);
		if (kind == null) {
			return;
		}
		long now = level.getServer().getTickCount();
		lastHurt.put(entity, now);
		if (kind == Kind.VILLAGER) {
			later(level.getServer(), CALM_DOWN_TICKS, () -> {
				if (entity.isAlive() && lastHurt.getOrDefault(entity, 0L) == now) {
					Reactions.sayByAge(entity, CALMED_DOWN, CALMED_DOWN_BABY, Options.DEFAULT);
				}
			});
			if (source.is(DamageTypes.IN_WALL)) {
				lastSuffocation.put(entity, now);
				later(level.getServer(), SUFFOCATION_ESCAPE_TICKS, () -> {
					if (entity.isAlive() && lastSuffocation.getOrDefault(entity, 0L) == now) {
						hurtLine(entity, FREED_FROM_SUFFOCATING, null, true);
					}
				});
			}
			villagerHurt(level, (Villager) entity, source);
			return;
		}
		CharacterLines lines = CHARACTER_HURT.get(kind);
		if (lines != null) {
			Player player = attackingPlayer(source);
			if (player != null && lines.byPlayer() != null) {
				hurtLine(entity, lines.byPlayer(), player, true);
			} else if (lines.otherwise() != null) {
				hurtLine(entity, lines.otherwise(), null, true);
			}
		}
	}

	private static void villagerHurt(ServerLevel level, Villager villager, DamageSource source) {
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();
		boolean magic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
		boolean projectile = direct != null && direct != attacker && !magic;
		if (projectile) {
			String line = attacker instanceof Player ? HIT_BY_PROJECTILE.get(type(direct)) : null;
			if (line != null) {
				hurtLine(villager, line, attacker, true);
			}
		} else if (attacker instanceof Player player) {
			Reactions.say(villager, BABY_HURT_BY_PLAYER, Options.DEFAULT.withStates(State.BABY, State.EVEN_IN_DANGER)
					.facing(player).forced().asUrgent());
			if (source.is(DamageTypes.FIREWORKS)) {
				hurtLine(villager, HIT_BY_FIREWORK, player, true);
			} else if (magic) {
				hurtLine(villager, HIT_BY_SPLASH_POTION, player, true);
			} else if (!attackedAtHome(level, villager, player)) {
				hurtLine(villager, weaponLine(player.getMainHandItem()), player, true);
			}
		} else if (attacker != null) {
			String type = type(attacker);
			if (type.equals("witch")) {
				hurtLine(villager, magic ? HIT_BY_HARMING_POTION : HIT_BY_WITCH, attacker, true);
				return;
			}
			MobLine line = HIT_BY_MOB.get(type);
			if (line != null) {
				Options options = Options.DEFAULT.withStates(State.ADULT, State.EVEN_IN_DANGER).forced().asUrgent().waitingAtMost(100);
				if (line.waitUntilGone()) {
					options = options.readyWhen(v -> !attacker.isAlive() || v.distanceTo(attacker) > 10);
				}
				Reactions.say(villager, line.dialog(), options);
			}
		} else {
			for (Map.Entry<ResourceKey<DamageType>, CauseLine> cause : HIT_BY_CAUSE.entrySet()) {
				if (source.is(cause.getKey())) {
					hurtLine(villager, cause.getValue().dialog(), null, cause.getValue().ignoreCooldowns());
					return;
				}
			}
		}
	}

	/**
	 * Hit in a village with a roof overhead - "in their home". A neighbour
	 * within 4 blocks also at home makes it the witnessed version, and they
	 * answer once it's been said.
	 */
	private static boolean attackedAtHome(ServerLevel level, Villager villager, Player player) {
		if (!atHome(level, villager)) {
			return false;
		}
		Villager witness = level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(4),
				v -> v != villager && v.distanceTo(villager) <= 4 && atHome(level, v)).stream().findFirst().orElse(null);
		if (witness != null) {
			hurtLine(villager, ATTACKED_AT_HOME_WITNESSED, player, true);
			witnesses.put(villager, witness);
		} else {
			hurtLine(villager, ATTACKED_AT_HOME, player, true);
		}
		return true;
	}

	private static boolean atHome(ServerLevel level, Villager villager) {
		return level.isVillage(villager.blockPosition()) && !level.canSeeSky(villager.blockPosition().above());
	}

	private static String weaponLine(ItemStack held) {
		return held.is(ItemTags.SWORDS) ? HIT_BY_SWORD : held.is(ItemTags.AXES) ? HIT_BY_AXE : held.is(ItemTags.SHOVELS)
				? HIT_BY_SHOVEL : held.is(ItemTags.HOES) ? HIT_BY_HOE : HIT;
	}

	/** The script's {@code wlslxd}: said even in danger, cutting off anything else, right away. */
	private static void hurtLine(LivingEntity speaker, String dialog, Entity facing, boolean ignoreCooldowns) {
		Kind kind = Speakers.kindOf(speaker);
		Options options = Options.DEFAULT.ignoringCooldowns(ignoreCooldowns, ignoreCooldowns, true).interrupting().asUrgent();
		if (facing != null) {
			options = options.facing(facing);
		}
		if (kind == Kind.MAYOR) {
			options = options.withKinds(Kind.MAYOR).withStates(State.BABY, State.EVEN_IN_DANGER);
		} else if (kind == Kind.WOOLY) {
			options = options.withKinds(Kind.WOOLY).withStates(State.ADULT, State.EVEN_IN_DANGER);
		} else {
			options = options.withStates(State.ADULT, State.EVEN_IN_DANGER);
		}
		Reactions.say(speaker, dialog, options);
	}

	/** Called (through a mixin) when an effect is added to a villager by something. */
	public static void effectAdded(LivingEntity entity, Holder<MobEffect> effect, Entity source) {
		if (!(entity instanceof Villager villager) || source == null) {
			return;
		}
		Entity thrower = source instanceof Projectile potion ? potion.getOwner() : source;
		String line = WITCH_POTION.get(effect.unwrapKey().map(k -> k.identifier().getPath()).orElse(""));
		if (line != null && thrower != null && type(thrower).equals("witch")) {
			later(villager.level().getServer(), 3, () -> hurtLine(villager, line, null, true));
		}
	}

	private static Player attackingPlayer(DamageSource source) {
		return source.getEntity() instanceof Player player ? player : null;
	}

	private static String type(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
	}

	private static void later(MinecraftServer server, int ticks, Runnable task) {
		server.schedule(new TickTask(server.getTickCount() + ticks, task));
	}
}
