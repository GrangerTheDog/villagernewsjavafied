package com.javafied.villagernews.client.dev;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.guide.GuideBook;
import com.javafied.villagernews.client.guide.GuideScreen;
import com.javafied.villagernews.content.ModItems;

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
		hold("mic-third-front", ModItems.MICROPHONE, ModItems.HANDBOOK, CameraType.THIRD_PERSON_FRONT);
		hold("mic-third-back", ModItems.MICROPHONE, ModItems.HANDBOOK, CameraType.THIRD_PERSON_BACK);
		hold("mic-first", ModItems.MICROPHONE, ModItems.HANDBOOK, CameraType.FIRST_PERSON);
		hold("book-third-front", ModItems.HANDBOOK, ModItems.MICROPHONE, CameraType.THIRD_PERSON_FRONT);
		hold("book-first", ModItems.HANDBOOK, ModItems.MICROPHONE, CameraType.FIRST_PERSON);
		steps.add(new Step("guide-home", () -> GuideBook.load().ifPresent(book -> {
			guide = new GuideScreen(book);
			Minecraft.getInstance().setScreenAndShow(guide);
		})));
		page("guide-menu", "sxjosu");
		page("guide-overview", "xrcvxx");
		page("guide-triggers", "ynokwd");
		steps.add(new Step("guide-search", () -> guide.search("shear")));
		page("guide-settings", "settings");
		steps.add(new Step("done", () -> Minecraft.getInstance().stop()));
		ClientTickEvents.END_CLIENT_TICK.register(DevShots::tick);
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
			}
			minecraft.options.setCameraType(camera);
		}));
	}

	private static void page(String name, String page) {
		steps.add(new Step(name, () -> {
			if (guide != null) {
				guide.openPage(page);
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
