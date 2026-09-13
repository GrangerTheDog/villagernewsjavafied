package com.javafied.villagernews.content;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /villagernews summon <variant>} - spawns a real vanilla Villager
 * tagged to render as one of the addon's reskins. Stands in for proper
 * spawn-egg items until the item pipeline covers them.
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

			dispatcher.register(Commands.literal("villagernews")
					.then(Commands.literal("summon")
							.then(Commands.argument("variant", StringArgumentType.word())
									.suggests(suggestVariants)
									.executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "variant"))))));
		});
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
		source.getLevel().addFreshEntity(villager);

		source.sendSuccess(() -> Component.literal("Summoned villager variant '" + variant + "'"), true);
		return 1;
	}
}
