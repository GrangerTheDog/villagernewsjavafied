package com.javafied.villagernews.client.dev;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.guide.GuideBook;
import com.javafied.villagernews.client.guide.GuideScreen;
import com.javafied.villagernews.content.ModAttachments;
import com.javafied.villagernews.content.ModItems;
import com.javafied.villagernews.names.AddonNames;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development aid: with a {@code villagernewsjavafied-devshots} file in the
 * game directory, the client (once in a world) holds the add-on's items in
 * each view and walks the handbook, taking a screenshot of each
 * ({@code screenshots/devshot-<n>-<step>.png}), then quits. The file is
 * removed first, so it runs once per request. Does nothing otherwise.
 */
public final class DevShots {
	private static final String FLAG = VillagerNewsJavafied.MOD_ID + "-devshots";
	private static final int SETTLE_TICKS = 40;

	private record Step(String name, Runnable action) {
	}

	private static final List<Step> steps = new ArrayList<>();
	private static int step = -1;
	private static int wait;
	private static GuideScreen guide;

	private DevShots() {
	}

	public static void init() {
		Path flag = FabricLoader.getInstance().getGameDir().resolve(FLAG);
		if (!Files.exists(flag)) {
			return;
		}
		try {
			Files.delete(flag);
		} catch (Exception e) {
			return;
		}
		steps.add(new Step("scene", DevShots::scene));
		hold("mic-third-front", ModItems.MICROPHONE, ModItems.HANDBOOK, CameraType.THIRD_PERSON_FRONT);
		hold("mic-third-back", ModItems.MICROPHONE, ModItems.HANDBOOK, CameraType.THIRD_PERSON_BACK);
		hold("mic-first", ModItems.MICROPHONE, ModItems.HANDBOOK, CameraType.FIRST_PERSON);
		hold("book-third-front", ModItems.HANDBOOK, ModItems.MICROPHONE, CameraType.THIRD_PERSON_FRONT);
		hold("book-first", ModItems.HANDBOOK, ModItems.MICROPHONE, CameraType.FIRST_PERSON);
		steps.add(new Step("guide-home", () -> GuideBook.load().ifPresent(book -> {
			guide = new GuideScreen(book);
			Minecraft.getInstance().setScreenAndShow(guide);
		})));
		page("guide-menu", "guide");
		page("guide-overview", "overview");
		page("guide-triggers", "triggers");
		steps.add(new Step("guide-search", () -> guide.search("shear")));
		page("guide-settings", "settings");
		steps.add(new Step("done", () -> Minecraft.getInstance().stop()));
		ClientTickEvents.END_CLIENT_TICK.register(DevShots::tick);
	}

	/**
	 * Faces the player south, wearing the Mayor Hat, with a Mayor, a
	 * red-dyed Wooly and a plain villager standing still a few blocks ahead.
	 */
	private static void scene() {
		Minecraft minecraft = Minecraft.getInstance();
		var server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) {
			return;
		}
		minecraft.player.setYRot(0);
		minecraft.player.setXRot(10);
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			var player = server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				return;
			}
			var level = player.level();
			player.setYRot(0);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.MAYOR_HAT));
			spawnVillager(level, player.getX() - 1.5, player.getY(), player.getZ() + 3.5, "mayor");
			spawnVillager(level, player.getX() + 1.5, player.getY(), player.getZ() + 3.5, "villager");
			var wooly = net.minecraft.world.entity.EntityTypes.SHEEP.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (wooly != null) {
				wooly.setPos(player.getX(), player.getY(), player.getZ() + 5);
				wooly.setAttached(ModAttachments.VILLAGER_VARIANT, "wooly");
				wooly.setColor(net.minecraft.world.item.DyeColor.RED);
				wooly.setNoAi(true);
				wooly.setYRot(180);
				level.addFreshEntity(wooly);
			}
		});
	}

	private static void spawnVillager(net.minecraft.server.level.ServerLevel level, double x, double y, double z, String character) {
		var villager = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (villager == null) {
			return;
		}
		villager.setPos(x, y, z);
		villager.setAttached(ModAttachments.VILLAGER_VARIANT, character);
		villager.setNoAi(true);
		villager.setYRot(180);
		villager.setYHeadRot(180);
		villager.setYBodyRot(180);
		level.addFreshEntity(villager);
	}

	private static void hold(String name, Item main, Item off, CameraType camera) {
		steps.add(new Step(name, () -> {
			Minecraft minecraft = Minecraft.getInstance();
			var server = minecraft.getSingleplayerServer();
			if (server != null && minecraft.player != null) {
				var uuid = minecraft.player.getUUID();
				server.execute(() -> {
					var player = server.getPlayerList().getPlayer(uuid);
					if (player != null) {
						player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
						player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(off));
					}
				});
				minecraft.player.setXRot(10);
				minecraft.player.setYRot(0);
			}
			minecraft.options.setCameraType(camera);
		}));
	}

	private static void page(String name, String page) {
		steps.add(new Step(name, () -> {
			if (guide != null) {
				String id = AddonNames.current().id(AddonNames.Kind.GUIDE_PAGE, page);
				guide.openPage(id != null ? id : page);
			}
		}));
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (step >= 0 && step < steps.size() && !steps.get(step).name().equals("done")) {
			String file = "devshot-" + step + "-" + steps.get(step).name() + ".png";
			Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
			});
		}
		step++;
		if (step >= steps.size()) {
			return;
		}
		steps.get(step).action().run();
		wait = step == 0 ? SETTLE_TICKS * 3 : SETTLE_TICKS;
	}
}
