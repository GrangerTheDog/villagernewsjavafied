package com.javafied.villagernews.content;

import com.javafied.villagernews.dialog.DialogEngine;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /villagernews summon <variant>} - spawns a real vanilla Villager
 * tagged to render as one of the addon's reskins.
 * {@code /villagernews say <dialog>} - makes the villager you look at (or the
 * nearest one) say a dialog right away, ignoring cooldowns: for testing.
 */
public final class VillagerCommand {
	private VillagerCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			SuggestionProvider<CommandSourceStack> suggestVariants = (ctx, builder) -> {
				VillagerVariantKeys.ALL.forEach(builder::suggest);
				return builder.buildFuture();
			};

			SuggestionProvider<CommandSourceStack> suggestDialogs = (ctx, builder) -> {
				DialogEngine engine = DialogEngine.get();
				if (engine != null) {
					engine.library().ids().stream().filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
				}
				return builder.buildFuture();
			};

			dispatcher.register(Commands.literal("villagernews")
					.then(Commands.literal("summon")
							.then(Commands.argument("variant", StringArgumentType.word())
									.suggests(suggestVariants)
									.executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "variant")))))
					.then(Commands.literal("say")
							.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
							.then(Commands.argument("dialog", StringArgumentType.word())
									.suggests(suggestDialogs)
									.executes(ctx -> say(ctx.getSource(), StringArgumentType.getString(ctx, "dialog"))))));
		});
	}

	private static int say(CommandSourceStack source, String dialog) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || engine.library().get(dialog) == null) {
			source.sendFailure(Component.literal("Unknown dialog '" + dialog + "' (is the add-on converted?)"));
			return 0;
		}
		Entity looker = source.getEntity();
		Vec3 from = source.getPosition();
		Villager speaker = null;
		double best = Double.MAX_VALUE;
		for (Villager villager : source.getLevel().getEntitiesOfClass(Villager.class, new AABB(from, from).inflate(DialogEngine.RANGE))) {
			double score = villager.distanceToSqr(from);
			if (looker != null) {
				Vec3 toVillager = villager.getEyePosition().subtract(looker.getEyePosition()).normalize();
				score *= 2 - looker.getLookAngle().dot(toVillager); // prefer the one in view
			}
			if (score < best) {
				best = score;
				speaker = villager;
			}
		}
		if (speaker == null) {
			source.sendFailure(Component.literal("No villager within " + (int) DialogEngine.RANGE + " blocks"));
			return 0;
		}
		DialogEngine.Options options = DialogEngine.Options.DEFAULT
				.withStates(DialogEngine.State.ADULT, DialogEngine.State.BABY, DialogEngine.State.SLEEPING, DialogEngine.State.EVEN_IN_DANGER)
				.ignoringCooldowns(true, true, true).interrupting().asUrgent().facing(looker);
		if (!engine.request(speaker, dialog, options)) {
			source.sendFailure(Component.literal("That villager can't speak right now"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Asked a villager to say '" + dialog + "'"), false);
		return 1;
	}

	private static int summon(CommandSourceStack source, String variant) {
		if (!VillagerVariantKeys.isValid(variant)) {
			source.sendFailure(Component.literal("Unknown variant '" + variant + "'. Known: " + VillagerVariantKeys.ALL));
			return 0;
		}

		Villager villager = EntityTypes.VILLAGER.create(source.getLevel(), EntitySpawnReason.COMMAND);
		if (villager == null) {
			source.sendFailure(Component.literal("Could not create a villager"));
			return 0;
		}

		Vec3 pos = source.getPosition();
		villager.setPos(pos.x, pos.y, pos.z);
		villager.setAttached(ModAttachments.VILLAGER_VARIANT, variant);
		if (SpecialTrades.hasOwnTrades(variant)) {
			SpecialTrades.makeTrader(source.getLevel(), villager);
		}
		source.getLevel().addFreshEntity(villager);

		source.sendSuccess(() -> Component.literal("Summoned villager variant '" + variant + "'"), true);
		return 1;
	}
}
