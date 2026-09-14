package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Hand port of the add-on script's reactions to what players do near
 * villagers: placing and breaking blocks, changing game mode, dying, flying,
 * carrying a nearly broken tool, lighting TNT, their status effects, standing
 * still, and staring at a villager. Each is said by the nearest villager
 * that can ({@link Reactions#nearest}), facing the player.
 */
public final class PlayerActionReactions {
	// Placing blocks: an exact block first, then categories in the add-on's order, then the generic line.
	private static final Map<String, String> PLACED_BLOCK = Map.ofEntries(Map.entry("melon", "veaotb"),
			Map.entry("beacon", "fazcvg"), Map.entry("bookshelf", "nziize"), Map.entry("chiseled_bookshelf", "nziize"),
			Map.entry("jack_o_lantern", "ahabbf"), Map.entry("end_stone", "nocwsk"), Map.entry("bricks", "fwiopr"),
			Map.entry("jukebox", "dgrgul"), Map.entry("lever", "piupkf"), Map.entry("observer", "nwiopk"),
			Map.entry("chest", "ktkmhn"), Map.entry("trapped_chest", "jtsycy"), Map.entry("tripwire_hook", "wwmhos"),
			Map.entry("detector_rail", "edjqet"), Map.entry("daylight_detector", "adtdit"), Map.entry("sculk_sensor", "wsjvjd"),
			Map.entry("calibrated_sculk_sensor", "wsjvjd"), Map.entry("redstone_wire", "qbuuop"), Map.entry("repeater", "cdltxm"),
			Map.entry("redstone_torch", "bimuve"), Map.entry("redstone_wall_torch", "bimuve"), Map.entry("redstone_lamp", "hlxzen"),
			Map.entry("copper_block", "gnbpco"), Map.entry("lapis_block", "lnhdzn"), Map.entry("emerald_block", "afdrdd"),
			Map.entry("diamond_block", "clpxov"), Map.entry("pumpkin", "ydfscf"), Map.entry("carved_pumpkin", "ydfscf"),
			Map.entry("snow_block", "whbvvh"), Map.entry("snow", "whbvvh"), Map.entry("crafting_table", "nxveql"),
			Map.entry("furnace", "fpcepp"), Map.entry("powder_snow", "ehtaer"));
	private static final String PLACED_LIGHTNING_ROD = "ntnicx";
	private static final String PLACED_IRON_GOLEM_FRAME = "gjsote";

	private record Category(Predicate<BlockState> test, String dialog) {
	}

	private static final List<Category> PLACED_CATEGORIES = List.of(
			new Category(BlockCategories::light, "yckvyp"),
			new Category(state -> state.is(BlockTags.BUTTONS), "rtmqmc"),
			// The add-on checks a suffix its minifier mangled, so in Bedrock this one never fires; restored.
			new Category(state -> state.is(BlockTags.PRESSURE_PLATES), "jggged"),
			new Category(BlockCategories::redstone, "hqmkpb"),
			new Category(state -> state.is(BlockTags.WOOL), "fvdzot"),
			new Category(BlockCategories::ocean, "rseoxb"),
			new Category(BlockCategories::ice, "gzzzpj"),
			new Category(BlockCategories::purpur, "ejbqqc"),
			new Category(BlockCategories::end, "qyvelv"),
			new Category(BlockCategories::nether, "kodgox"),
			new Category(BlockCategories::glass, "sbolpm"),
			new Category(BlockCategories::concrete, "zqzrsm"),
			new Category(BlockCategories::concretePowder, "negvfb"),
			new Category(state -> state.is(BlockTags.DIRT), "xezbvo"),
			new Category(BlockCategories::terracotta, "gxepwi"),
			new Category(state -> false, PLACED_IRON_GOLEM_FRAME), // needs the position: see placed()
			new Category(state -> state.is(Blocks.IRON_BLOCK), "vgufsp"),
			new Category(BlockCategories::valuable, "oimgrg"),
			new Category(BlockCategories::workstation, "ujkoue"),
			new Category(state -> state.is(BlockTags.BEDS), "kdfaao"),
			new Category(BlockCategories::wood, "mxmrpn"),
			new Category(BlockCategories::gravity, "tqnwzp"),
			new Category(BlockCategories::creativeOnly, "fdqdok"),
			new Category(BlockCategories::plant, "vubtsn"),
			new Category(BlockCategories::gold, "vxhugl"),
			new Category(BlockCategories::glazedTerracotta, "cudgjr"),
			new Category(state -> true, "knywuy"));

	// Breaking blocks.
	private static final String BROKE_MANY = "awappv";
	private static final String BROKE_BELL = "rptjbd";
	private static final String BROKE_BED = "osbwpz";
	private static final String BROKE_DOOR = "qimink";
	private static final String BROKE_WORKSTATION = "mabmbl";
	private static final String BROKE_WOOD = "ejvpis";
	private static final String HARVESTED_CROPS = "tdomqw";
	private static final String HARVESTED_NEAR_FARMER = "gytgzn";
	private static final String BROKE_STONE = "clzrea";
	private static final String BROKE_DECORATION = "pclmft";
	private static final String BROKE_BLOCK = "zejman";
	private static final int QUICK_BREAK_TICKS = 40;

	private static final String CHANGED_GAME_MODE = "fhhqxg";
	private static final String SWITCHED_TO_CREATIVE = "ohtblt";
	private static final String PLAYER_DIED = "hzjycq";
	private static final String PLAYER_DIED_AGAIN = "dxcjqn";
	private static final String PLAYER_DIED_HARDCORE = "elcjbb";
	private static final int DIED_AGAIN_TICKS = 6000;
	private static final String GLIDING = "hiuxvo";
	private static final String FLYING = "lqxmlx";
	private static final String NEARLY_BROKEN_ITEM = "nwkgqg";
	private static final String LIT_TNT = "oziqss";
	private static final String HERO_OF_THE_VILLAGE = "gnetsk";
	private static final String HERO_OF_THE_VILLAGE_BABY = "fzyrfm";
	private static final String MANY_EFFECTS = "fqbjfv";
	private static final String STANDING_STILL = "zsmvzb";
	private static final int STANDING_STILL_TICKS = 2400;
	private static final String TRAMPLED_CROPS = "pizztd";
	private static final String ATE_FOOD = "akerwb";
	private static final String SHEARED_SHEEP = "edrtbe";
	private static final String USED_LEAD = "elexev";
	private static final String STARING = "cavwps";
	private static final double STARE_RANGE = 8;
	private static final double STARE_CONE = 0.97;
	private static final int STARE_TICKS = 80;

	private static final Map<Player, Long> lastBreak = new WeakHashMap<>();
	private static final Map<Player, Integer> quickBreaks = new WeakHashMap<>();
	/** By UUID: a respawned player is a new entity. */
	private static final Map<java.util.UUID, Long> lastDeath = new java.util.HashMap<>();
	private static final Map<Player, Vec3> stillAt = new WeakHashMap<>();
	private static final Map<Player, Long> stillSince = new WeakHashMap<>();

	/** A player looking at a villager: since when, and whether they held a sword throughout (then no comment). */
	private static final class Stare {
		final LivingEntity villager;
		final long since;
		boolean armed = true;

		Stare(LivingEntity villager, long since) {
			this.villager = villager;
			this.since = since;
		}
	}

	private static final Map<Player, Stare> stares = new WeakHashMap<>();

	private PlayerActionReactions() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server) {
				broke(server, player, state);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				died(player);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PrimedTnt tnt && tnt.getFuse() >= 79) {
				litTnt(level, tnt);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PlayerActionReactions::tick);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && level instanceof ServerLevel server
					&& player.getItemInHand(hand).is(net.minecraft.world.item.Items.LEAD)) {
				if (entity instanceof LivingEntity living && Speakers.kindOf(living) != null) {
					Reactions.say(living, USED_LEAD, toward(player));
				} else if (entity instanceof net.minecraft.world.entity.Leashable) {
					Reactions.nearest(server, player.position(), USED_LEAD, toward(player));
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
	}

	/** Called (through a mixin) when farmland under a crop is trampled back to dirt. */
	public static void trampled(ServerLevel level, BlockPos farmland) {
		Reactions.nearest(level, Vec3.atCenterOf(farmland), TRAMPLED_CROPS, Options.DEFAULT.facing(Vec3.atCenterOf(farmland.above())));
	}

	/** Called (through a mixin) when a player finishes eating. */
	public static void ate(ServerPlayer player) {
		Reactions.nearest(player.level(), player.position(), ATE_FOOD, toward(player));
	}

	/** Called (through a mixin) when an ordinary sheep is sheared. */
	public static void sheared(ServerLevel level, LivingEntity sheep) {
		if (Speakers.kindOf(sheep) == null) {
			Reactions.nearest(level, sheep.position(), SHEARED_SHEEP, Options.DEFAULT.facing(sheep));
		}
	}

	private static Options toward(Player player) {
		return Options.DEFAULT.facing(player);
	}

	/** Called (through a mixin) when a player places a block. */
	public static void placed(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		List<String> options = new ArrayList<>();
		String id = BlockCategories.id(state);
		String exact = id.endsWith("lightning_rod") ? PLACED_LIGHTNING_ROD : PLACED_BLOCK.get(id);
		if (exact != null) {
			options.add(exact);
		}
		for (Category category : PLACED_CATEGORIES) {
			boolean matches = category.dialog().equals(PLACED_IRON_GOLEM_FRAME)
					? state.is(Blocks.IRON_BLOCK) && BlockCategories.completesIronGolemFrame(level, pos)
					: category.test().test(state);
			if (matches) {
				options.add(category.dialog());
			}
		}
		for (String dialog : options) {
			if (Reactions.nearest(level, player.position(), dialog, toward(player)) != null) {
				return;
			}
		}
	}

	private static void broke(ServerLevel level, Player player, BlockState state) {
		long now = level.getServer().getTickCount();
		Long last = lastBreak.get(player);
		int streak = last != null && now < last + QUICK_BREAK_TICKS ? quickBreaks.getOrDefault(player, 0) + 1 : 0;
		lastBreak.put(player, now);
		quickBreaks.put(player, streak);
		if (state.is(BlockTags.CROPS)) {
			level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, player.getBoundingBox().inflate(12),
							v -> "farmer".equals(Speakers.profession(v)) && v.distanceTo(player) <= 12).stream()
					.sorted((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player))).limit(3)
					.forEach(farmer -> Reactions.say(farmer, HARVESTED_NEAR_FARMER, toward(player)));
		}
		String dialog = streak > 3 ? BROKE_MANY
				: state.is(Blocks.BELL) ? BROKE_BELL
				: state.is(BlockTags.BEDS) ? BROKE_BED
				: BlockCategories.door(state) ? BROKE_DOOR
				: BlockCategories.workstation(state) ? BROKE_WORKSTATION
				: BlockCategories.wood(state) ? BROKE_WOOD
				: state.is(BlockTags.CROPS) ? HARVESTED_CROPS
				: BlockCategories.stone(state) ? BROKE_STONE
				: BlockCategories.decorative(state) ? BROKE_DECORATION
				: BROKE_BLOCK;
		Reactions.nearest(level, player.position(), dialog, toward(player));
	}

	/** Called (through a mixin) when a player's game mode changes. */
	public static void gameModeChanged(ServerPlayer player, GameType mode) {
		Reactions.nearest(player.level(), player.position(), mode == GameType.CREATIVE ? SWITCHED_TO_CREATIVE : CHANGED_GAME_MODE,
				toward(player));
	}

	private static void died(ServerPlayer player) {
		long now = player.level().getServer().getTickCount();
		Long last = lastDeath.put(player.getUUID(), now);
		String dialog = player.level().getLevelData().isHardcore() ? PLAYER_DIED_HARDCORE
				: last != null && last + DIED_AGAIN_TICKS > now ? PLAYER_DIED_AGAIN : PLAYER_DIED;
		Reactions.nearest(player.level(), player.position(), dialog, Options.DEFAULT.facing(player.position()));
	}

	private static void litTnt(ServerLevel level, PrimedTnt tnt) {
		Player player = level.getNearestPlayer(tnt, 6);
		if (player != null) {
			Reactions.nearest(level, tnt.position(), LIT_TNT, toward(player));
		}
	}

	private static void tick(MinecraftServer server) {
		if (DialogEngine.get() == null) {
			return;
		}
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (tick % 10 == 0) {
				stare(player, tick, tick % 40 == 0);
			}
			if (tick % 40 == 0) {
				if (player.isFallFlying()) {
					Reactions.nearest(level, player.position(), GLIDING, toward(player));
				} else if (player.getAbilities().flying && !player.isSpectator()) {
					Reactions.nearest(level, player.position(), FLYING, toward(player));
				}
			}
			if (tick % 60 == 0 && !player.isSpectator()) {
				if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
					Reactions.nearestByAge(level, player.position(), HERO_OF_THE_VILLAGE, HERO_OF_THE_VILLAGE_BABY,
							toward(player), Reactions.NEARBY, null);
				}
				if (player.getActiveEffects().size() > 1) {
					Reactions.nearest(level, player.position(), MANY_EFFECTS, toward(player));
				}
			}
			if (tick % 200 == 0) {
				ItemStack held = player.getMainHandItem();
				if (held.isDamageableItem() && held.getDamageValue() > 0.8 * held.getMaxDamage()) {
					Reactions.nearest(level, player.position(), NEARLY_BROKEN_ITEM, toward(player));
				}
			}
			if (tick % 400 == 0) {
				standingStill(player, tick);
			}
		}
	}

	private static void standingStill(ServerPlayer player, long now) {
		Vec3 at = player.position();
		if (!at.equals(stillAt.get(player))) {
			stillAt.put(player, at);
			stillSince.put(player, now);
		} else if (stillSince.getOrDefault(player, now) + STANDING_STILL_TICKS <= now) {
			Reactions.nearest(player.level(), at, STANDING_STILL, Options.DEFAULT);
		}
	}

	/**
	 * Looking straight at a villager within 8 blocks for 4 seconds gets a
	 * comment - unless you held a sword the whole time.
	 */
	private static void stare(ServerPlayer player, long now, boolean lookForNew) {
		Stare stare = stares.get(player);
		if (stare != null) {
			if (player.isSpectator() || !looksAt(player, stare.villager)) {
				stares.remove(player);
				return;
			}
			if (stare.armed && !player.getMainHandItem().is(ItemTags.SWORDS)) {
				stare.armed = false;
			}
			if (stare.since + STARE_TICKS <= now) {
				if (!stare.armed) {
					Reactions.say(stare.villager, STARING, toward(player));
				}
				stares.remove(player);
			}
		} else if (lookForNew && !player.isSpectator()) {
			for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(STARE_RANGE), e -> Speakers.kindOf(e) != null)) {
				if (looksAt(player, candidate)) {
					stares.put(player, new Stare(candidate, now));
					break;
				}
			}
		}
	}

	private static boolean looksAt(Player player, LivingEntity target) {
		if (!target.isAlive() || target.level() != player.level()) {
			return false;
		}
		Vec3 to = target.position().add(0, 1, 0).subtract(player.getEyePosition());
		double distance = to.length();
		return distance > 0 && distance <= STARE_RANGE && player.getLookAngle().dot(to.scale(1 / distance)) >= STARE_CONE;
	}
}
