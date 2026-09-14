package com.javafied.villagernews.dialog;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /villagernews debug}: for players who turn it on, an overlay about
 * the villager they look at - what it's saying, its cooldowns, what it's
 * waiting to say, and why its last reaction didn't happen.
 */
public final class DialogDebug {
	private static final int INTERVAL = 5;
	/** How closely a villager must be in the middle of the view to count as looked at (cosine). */
	private static final double LOOK_CONE = 0.97;
	private static final Set<UUID> watching = new HashSet<>();

	private DialogDebug() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DialogDebug::tick);
	}

	/** @return whether debug is now on for this player */
	public static boolean toggle(ServerPlayer player) {
		if (watching.remove(player.getUUID())) {
			ServerPlayNetworking.send(player, new DialogPayloads.Debug(List.of()));
			return false;
		}
		watching.add(player.getUUID());
		return true;
	}

	private static void tick(MinecraftServer server) {
		DialogEngine engine = DialogEngine.get();
		if (watching.isEmpty() || engine == null || server.getTickCount() % INTERVAL != 0) {
			return;
		}
		for (UUID id : Set.copyOf(watching)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				watching.remove(id);
				continue;
			}
			Villager villager = lookedAt(player);
			List<String> lines = new ArrayList<>();
			if (villager != null) {
				lines.add(villager.getName().getString() + " (" + villager.getAttachedOrElse(
						com.javafied.villagernews.content.ModAttachments.VILLAGER_VARIANT, "villager") + ")");
				lines.addAll(engine.describe(villager));
			}
			ServerPlayNetworking.send(player, new DialogPayloads.Debug(lines));
		}
	}

	private static Villager lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Villager best = null;
		double bestDot = LOOK_CONE;
		for (Villager villager : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(DialogEngine.RANGE))) {
			Vec3 to = villager.getBoundingBox().getCenter().subtract(eye);
			double dot = look.dot(to.normalize());
			if (to.length() <= DialogEngine.RANGE && dot > bestDot) {
				best = villager;
				bestDot = dot;
			}
		}
		return best;
	}
}
