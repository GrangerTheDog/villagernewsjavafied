package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand port of the add-on script's reactions to players using blocks:
 * workstations and containers, lighting or putting out campfires and
 * candles, cooking, composting, carving pumpkins, playing records, doors and
 * gates, and opening chests - especially one in a villager's house.
 */
public final class BlockUseReactions {
	private static final String USED_CAULDRON = "gtqcjn";
	private static final String USED_COMPOSTER = "mfsmim";
	private static final String COOKED_ON_CAMPFIRE = "nrzcxn";
	private static final String LIT_CAMPFIRE = "tavxec";
	private static final String PUT_OUT_CAMPFIRE = "uvgkny";
	private static final String LIT_CANDLE = "lfwbvu";
	private static final String PUT_OUT_CANDLE = "repalq";
	private static final String USED_SHELVES = "hojlbk";
	private static final String CARVED_PUMPKIN = "xemdbt";
	private static final String PLAYED_RECORD = "dyvwqv";
	private static final String OPENED_GATE = "wykqdb";
	private static final String CLOSED_GATE = "pvwkxp";
	private static final String USED_DOOR = "jexbze";
	private static final String OPENED_CHEST = "vgysma";
	private static final String OPENED_CHEST_IN_HOUSE = "qfhrlh";
	private static final String USED_SHULKER_BOX = "lifrrj";
	private static final Map<String, String> USED_BLOCK = Map.ofEntries(Map.entry("brewing_stand", "myylcq"),
			Map.entry("beacon", "xzpfwa"), Map.entry("campfire", "xbttae"), Map.entry("soul_campfire", "xbttae"),
			Map.entry("crafter", "mjmgwj"), Map.entry("crafting_table", "pgwqkg"), Map.entry("ender_chest", "ykycil"),
			Map.entry("dispenser", "lnlwnl"), Map.entry("dropper", "thmfsh"), Map.entry("grindstone", "tgggsl"),
			Map.entry("enchanting_table", "crvciv"), Map.entry("anvil", "xwcoip"), Map.entry("smithing_table", "ididel"),
			Map.entry("furnace", "gzhbtb"), Map.entry("cartography_table", "rykqwl"), Map.entry("loom", "bpgyfa"),
			Map.entry("stonecutter", "knjvae"), Map.entry("chiseled_bookshelf", "iqvgzk"));
	private static final Set<String> COOKABLE = Set.of("beef", "chicken", "porkchop", "mutton", "rabbit", "cod", "salmon",
			"potato", "kelp");

	/** What a player was about to use, captured before the use so its result can be compared. */
	private record Use(Player player, BlockPos pos, BlockState before, ItemStack held, boolean sneakingWithItem) {
	}

	private static final List<Use> pending = new ArrayList<>();

	private BlockUseReactions() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel && DialogEngine.get() != null) {
				ItemStack held = player.getItemInHand(hand);
				pending.add(new Use(player, hit.getBlockPos(), level.getBlockState(hit.getBlockPos()), held.copy(),
						player.isSecondaryUseActive() && !held.isEmpty()));
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pending.isEmpty()) {
				return;
			}
			List<Use> uses = List.copyOf(pending);
			pending.clear();
			for (Use use : uses) {
				if (use.player().isAlive() && use.player().level() instanceof ServerLevel level) {
					used(level, use, level.getBlockState(use.pos()));
				}
			}
		});
	}

	/** The script's handlers, in its order; the first that applies decides. */
	private static void used(ServerLevel level, Use use, BlockState after) {
		BlockState before = use.before();
		String id = BlockCategories.id(before);
		ItemStack held = use.held();
		String dialog = null;
		if (before.is(BlockTags.CAULDRONS)) {
			dialog = USED_CAULDRON;
		} else if (before.is(Blocks.COMPOSTER)) {
			if (ComposterBlock.COMPOSTABLES.containsKey(held.getItem())
					&& !(after.is(Blocks.COMPOSTER) && after.getValue(ComposterBlock.LEVEL) == ComposterBlock.MAX_LEVEL)) {
				dialog = USED_COMPOSTER;
			}
		} else if (before.is(BlockTags.CAMPFIRES) && (dialog = campfire(before, after, held)) != null) {
			// decided
		} else if (before.is(BlockTags.CANDLES) && after.is(before.getBlock())) {
			dialog = litChange(before, after, igniter(held), true, LIT_CANDLE, PUT_OUT_CANDLE);
		} else if (id.endsWith("_shelf")) {
			dialog = USED_SHELVES;
		} else if (held.is(Items.SHEARS) && before.is(Blocks.PUMPKIN) && after.is(Blocks.CARVED_PUMPKIN)) {
			dialog = CARVED_PUMPKIN;
		} else if (before.is(Blocks.JUKEBOX) && !use.sneakingWithItem() && held.has(DataComponents.JUKEBOX_PLAYABLE)) {
			dialog = PLAYED_RECORD;
		} else if (before.is(BlockTags.FENCE_GATES) && !use.sneakingWithItem() && after.hasProperty(BlockStateProperties.OPEN)) {
			dialog = after.getValue(BlockStateProperties.OPEN) ? OPENED_GATE : CLOSED_GATE;
		} else if (before.is(BlockTags.DOORS) && !use.sneakingWithItem()) {
			dialog = USED_DOOR;
		} else if (USED_BLOCK.containsKey(id) && !use.sneakingWithItem()) {
			dialog = USED_BLOCK.get(id);
		} else if (before.is(Blocks.CHEST) && !use.sneakingWithItem()) {
			openedChest(level, use.player());
			return;
		} else if (before.is(BlockTags.SHULKER_BOXES) && !use.player().isShiftKeyDown()) {
			dialog = USED_SHULKER_BOX;
		}
		if (dialog != null) {
			Reactions.nearest(level, use.player().position(), dialog, Options.DEFAULT.facing(use.player()));
		}
	}

	private static String campfire(BlockState before, BlockState after, ItemStack held) {
		if (COOKABLE.contains(BuiltInRegistries.ITEM.getKey(held.getItem()).getPath())) {
			return COOKED_ON_CAMPFIRE;
		}
		if (!after.is(before.getBlock())) {
			return null;
		}
		boolean putOutWith = held.is(ItemTags.SHOVELS) || held.is(Items.WATER_BUCKET);
		String changed = litChange(before, after, igniter(held), putOutWith, LIT_CAMPFIRE, PUT_OUT_CAMPFIRE);
		return changed;
	}

	/** Unlit to lit with something that lights it, or lit to unlit with something that puts it out. */
	private static String litChange(BlockState before, BlockState after, boolean lighter, boolean extinguisher, String lit,
			String putOut) {
		if (!before.hasProperty(BlockStateProperties.LIT) || !after.hasProperty(BlockStateProperties.LIT)) {
			return null;
		}
		boolean was = before.getValue(BlockStateProperties.LIT);
		boolean is = after.getValue(BlockStateProperties.LIT);
		return !was && is && lighter ? lit : was && !is && extinguisher ? putOut : null;
	}

	private static boolean igniter(ItemStack held) {
		return held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)
				|| held.getEnchantments().keySet().stream().anyMatch(e -> e.is(Enchantments.FIRE_ASPECT));
	}

	/**
	 * The nearest free villager comments - more pointedly if it's in a
	 * village and there's a roof over the chest (the add-on's "their house").
	 */
	private static void openedChest(ServerLevel level, Player player) {
		Villager villager = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Reactions.NEARBY),
						v -> !v.isSleeping() && v.distanceTo(player) <= Reactions.NEARBY
								&& (DialogEngine.get() == null || !DialogEngine.get().isTalking(v)))
				.stream().min(Comparator.comparingDouble(v -> v.distanceTo(player))).orElse(null);
		if (villager == null) {
			return;
		}
		Options options = Options.DEFAULT.facing(player);
		boolean theirHouse = level.isVillage(villager.blockPosition()) && !level.canSeeSky(player.blockPosition().above());
		if (!(theirHouse && Reactions.say(villager, OPENED_CHEST_IN_HOUSE, options))) {
			Reactions.say(villager, OPENED_CHEST, options);
		}
	}
}
