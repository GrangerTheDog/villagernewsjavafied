package com.javafied.villagernews.dialog;

import com.javafied.villagernews.behavior.BehaviorDefinitions;
import com.javafied.villagernews.behavior.BehaviorProperties;
import com.javafied.villagernews.behavior.BehaviorSensors;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.ModItems;
import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;

import com.google.gson.JsonPrimitive;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's item interactions: give a villager a hat,
 * the microphone or the moustache to wear, take it back with shears - or,
 * failing that, its nose - and hand the nose back. Plus the reaction to a
 * player wearing a villager's nose, and the handbook every player starts with.
 */
public final class VillagerItemReactions {
	/** The add-on property holding what a villager wears ("none" or an item id's path). */
	private static final String ACCESSORY = "p:mlxeez";
	/** Whether the villager still has its nose. */
	private static final String NOSE = "p:gcfsvg";
	private static final String NONE = "none";

	private static final String TAKEN_ACCESSORY = "ckjbyd";
	private static final String TAKEN_NOSE = "jktrnd";
	private static final String NOSE_RETURNED = "kxrhxt";
	private static final String ALREADY_HAS_NOSE = "akfekx";
	private static final String DRESSES_THEMSELVES = "orogba";
	private static final String SAW_MY_NOSE = "kejscw";
	/** Reaction to being given each accessory, for adults (said twice as often as {@link #DRESSES_THEMSELVES}) and babies. */
	private static final Map<Item, String> ADULT_GIFT = Map.of(ModItems.TESTIFICATE_MAN_HELMET, "wurmgu",
			ModItems.MICROPHONE, "inirxg", ModItems.MOUSTACHE, "ozxzla");
	private static final Map<Item, String> BABY_GIFT = Map.of(ModItems.MAYOR_HAT, "svdjdk",
			ModItems.TESTIFICATE_MAN_HELMET, "cxeziv", ModItems.MICROPHONE, "riezum", ModItems.MOUSTACHE, "rlkdqd");
	private static final List<Item> ACCESSORIES = List.of(ModItems.MAYOR_HAT, ModItems.TESTIFICATE_MAN_HELMET,
			ModItems.MICROPHONE, ModItems.MOUSTACHE);

	private static final Map<Player, Boolean> wearingNose = new WeakHashMap<>();

	private VillagerItemReactions() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager villager) || villager.isSleeping()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.SHEARS) && !stack.is(ModItems.VILLAGER_NOSE) && !ACCESSORIES.contains(stack.getItem())) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				BehaviorDefinitions.Definition definition = BehaviorSensors.definitionOf(villager);
				if (definition == null) {
					return InteractionResult.PASS; // add-on not converted: leave vanilla alone
				}
				interact(server, player, villager, stack, new BehaviorProperties(villager, definition));
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(VillagerItemReactions::watchNoseWearers);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveHandbookOnce(handler.getPlayer()));
	}

	private static void interact(ServerLevel level, Player player, Villager villager, ItemStack stack, BehaviorProperties properties) {
		String accessory = String.valueOf(properties.get(ACCESSORY));
		boolean hasNose = !Boolean.FALSE.equals(properties.get(NOSE));
		if (stack.is(Items.SHEARS)) {
			if (!NONE.equals(accessory)) {
				Item worn = BuiltInRegistries.ITEM.getValue(com.javafied.villagernews.VillagerNewsJavafied.id(accessory));
				dropFromHead(level, villager, new ItemStack(worn));
				properties.set(ACCESSORY, new JsonPrimitive(NONE));
				react(villager, player, TAKEN_ACCESSORY, State.ADULT);
			} else if (hasNose && !villager.isBaby()) {
				dropFromHead(level, villager, new ItemStack(ModItems.VILLAGER_NOSE));
				properties.set(NOSE, new JsonPrimitive(false));
				react(villager, player, TAKEN_NOSE, State.ADULT);
			}
			return;
		}
		if (stack.is(ModItems.VILLAGER_NOSE)) {
			if (villager.isBaby()) {
				return;
			}
			if (!hasNose) {
				stack.consume(1, player);
				properties.set(NOSE, new JsonPrimitive(true));
				react(villager, player, NOSE_RETURNED, State.ADULT);
			} else {
				DialogEngine engine = DialogEngine.get();
				DialogEngine.Speech speech = engine == null ? null : engine.speech(villager);
				if (speech == null || !speech.dialog().id().equals(ALREADY_HAS_NOSE)) {
					react(villager, player, ALREADY_HAS_NOSE, State.ADULT);
				}
			}
			return;
		}
		if (!NONE.equals(accessory)) {
			return;
		}
		Item item = stack.getItem();
		stack.consume(1, player);
		properties.set(ACCESSORY, new JsonPrimitive(BuiltInRegistries.ITEM.getKey(item).getPath()));
		if (villager.isBaby()) {
			react(villager, player, BABY_GIFT.get(item), State.BABY);
		} else {
			String special = ADULT_GIFT.get(item);
			List<String> options = item == ModItems.MICROPHONE ? List.of(special)
					: special == null ? List.of(DRESSES_THEMSELVES) : List.of(DRESSES_THEMSELVES, special, special);
			react(villager, player, options.get(ThreadLocalRandom.current().nextInt(options.size())), State.ADULT);
		}
	}

	/** The script's {@code yyofim}: said to the player, cutting off anything else, right away. */
	private static void react(Villager villager, Player player, String dialog, State state) {
		DialogEngine engine = DialogEngine.get();
		if (engine != null && dialog != null) {
			engine.request(villager, dialog, Options.DEFAULT.withStates(state).facing(player)
					.ignoringCooldowns(true, true, true).interrupting().asUrgent());
		}
	}

	private static void dropFromHead(ServerLevel level, Villager villager, ItemStack stack) {
		level.playSound(null, villager.getX(), villager.getEyeY(), villager.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1f, 1f);
		level.addFreshEntity(new ItemEntity(level, villager.getX(), villager.getEyeY(), villager.getZ(), stack));
	}

	/** Putting a villager's nose on your own head gets a nearby villager asking after it. */
	private static void watchNoseWearers(MinecraftServer server) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean wearing = !player.isSpectator() && player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.VILLAGER_NOSE);
			Boolean before = wearingNose.put(player, wearing);
			if (wearing && Boolean.FALSE.equals(before)) {
				player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(DialogEngine.RANGE)).stream()
						.sorted((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
						.filter(villager -> engine.request(villager, SAW_MY_NOSE, Options.DEFAULT.facing(player)))
						.findFirst();
			}
		}
	}

	/** The add-on hands every player the handbook the first time they join. */
	private static void giveHandbookOnce(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(ModAttachments.RECEIVED_HANDBOOK))) {
			return;
		}
		player.setAttached(ModAttachments.RECEIVED_HANDBOOK, true);
		if (!player.getInventory().contains(new ItemStack(ModItems.HANDBOOK))) {
			player.getInventory().add(new ItemStack(ModItems.HANDBOOK));
		}
	}
}
