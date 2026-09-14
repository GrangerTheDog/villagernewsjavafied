package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hand port of the add-on script's trading reactions: a greeting when the
 * trade window opens (friendlier the better the player's reputation), a
 * comment on each purchase, and a goodbye that depends on whether anything
 * was bought.
 */
public final class TradeReactions {
	/** Greetings by the player's reputation with the villager; the one closest to it is used. */
	private static final Map<Integer, String> GREETING_BY_REPUTATION = Map.of(100, "vlrsrn", 50, "kuhvdv", 0, "clbjww",
			-150, "qmdvft", -300, "xduuwm");
	private static final String PURCHASE = "xmkwxd";
	private static final String BYE_AFTER_BUYING = "czvvwy";
	private static final String BYE_WITHOUT_BUYING = "lilimm";
	private static final String BYE = "laztau";
	/** Trading dialogs don't interrupt each other (the script's zcphsg). */
	private static final Set<String> TRADE_DIALOGS = Set.of("vlrsrn", "kuhvdv", "clbjww", "qmdvft", "xduuwm", PURCHASE,
			BYE_AFTER_BUYING, BYE_WITHOUT_BUYING, BYE, "fltegg", "pnvkfy");

	private static final Map<AbstractVillager, Boolean> boughtSomething = new WeakHashMap<>();
	private static final Map<AbstractVillager, Long> lastPurchaseComment = new WeakHashMap<>();

	private TradeReactions() {
	}

	public static void tradingChanged(AbstractVillager merchant, Player before, Player after) {
		if (!(merchant instanceof Villager villager) || !employed(villager)) {
			return;
		}
		if (before == null && after != null) {
			boughtSomething.put(villager, false);
			say(villager, after, greeting(villager.getPlayerReputation(after)));
		} else if (before != null && after == null) {
			boolean bought = Boolean.TRUE.equals(boughtSomething.remove(villager));
			if (!say(villager, before, bought ? BYE_AFTER_BUYING : BYE_WITHOUT_BUYING)) {
				say(villager, before, BYE);
			}
		}
	}

	public static void traded(AbstractVillager merchant) {
		if (!(merchant instanceof Villager villager) || merchant.getTradingPlayer() == null) {
			return;
		}
		boughtSomething.put(villager, true);
		DialogEngine engine = DialogEngine.get();
		Long last = lastPurchaseComment.get(villager);
		if (engine != null && (last == null || last != engine.now())) {
			lastPurchaseComment.put(villager, engine.now());
			say(villager, merchant.getTradingPlayer(), PURCHASE);
		}
	}

	static String greeting(int reputation) {
		int best = 0;
		for (int threshold : GREETING_BY_REPUTATION.keySet()) {
			if (Math.abs(reputation - threshold) < Math.abs(reputation - best)) {
				best = threshold;
			}
		}
		return GREETING_BY_REPUTATION.get(best);
	}

	/** Said to the player right away - unless the villager is already mid trading line. */
	private static boolean say(Villager villager, Player player, String dialog) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null) {
			return false;
		}
		DialogEngine.Speech speech = engine.speech(villager);
		if (speech != null && TRADE_DIALOGS.contains(speech.dialog().id())) {
			return false;
		}
		return engine.request(villager, dialog, Options.DEFAULT.facing(player).ignoringCooldowns(true, true, true)
				.interrupting().asUrgent());
	}

	/** Nitwits and the unemployed don't trade, and the add-on gives them no trading lines. */
	private static boolean employed(Villager villager) {
		String profession = villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
		return !profession.equals("none") && !profession.equals("nitwit");
	}
}
