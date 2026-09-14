package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.State;
import com.javafied.villagernews.dialog.Speakers.Kind;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's trading reactions: a greeting when the
 * trade window opens (friendlier the better the player's reputation - the
 * special characters and the trader have their own), a comment on each
 * purchase, a goodbye that depends on whether anything was bought, and a
 * cheer when a villager levels up.
 */
public final class TradeReactions {
	/** Greetings by the player's reputation with the villager; the one closest to it is used. */
	private static final Map<Integer, String> GREETING_BY_REPUTATION = Map.of(100, "vlrsrn", 50, "kuhvdv", 0, "clbjww",
			-150, "qmdvft", -300, "xduuwm");

	/** Per speaker kind: own greeting (said half the time), goodbye, goodbye after buying, after not buying, per purchase. */
	private record Lines(String greeting, String bye, String boughtBye, String noPurchaseBye, String purchase) {
	}

	private static final Map<Kind, Lines> LINES = Map.of(
			Kind.VILLAGER, new Lines(null, "laztau", "czvvwy", "lilimm", "xmkwxd"),
			Kind.MAYOR, new Lines("njyapy", null, "shrrya", "bgzmea", null),
			Kind.TESTIFICATE_MAN, new Lines("mpbnsm", "ctzfzj", "xcjort", "rdugrl", "xmkwxd"),
			Kind.NUMBER_5, new Lines("sclaoa", "nfdery", "msofrj", "lilimm", "xmkwxd"),
			Kind.NUMBER_9, new Lines("snnkrl", "hvjfnk", "czvvwy", "lilimm", "xmkwxd"),
			Kind.TRADER, new Lines("yubpbb", "laztau", "uzdvsi", "erbcfn", "bvrbhy"));
	private static final String LEVELLED_UP = "fltegg";
	private static final String REACHED_MASTER = "pnvkfy";
	/** Trading dialogs don't interrupt each other (the script's zcphsg). */
	private static final Set<String> TRADE_DIALOGS = new HashSet<>(GREETING_BY_REPUTATION.values());

	static {
		for (Lines lines : LINES.values()) {
			for (String line : new String[] {lines.greeting(), lines.bye(), lines.boughtBye(), lines.noPurchaseBye(), lines.purchase()}) {
				if (line != null) {
					TRADE_DIALOGS.add(line);
				}
			}
		}
		TRADE_DIALOGS.addAll(List.of(LEVELLED_UP, REACHED_MASTER));
	}

	private static final Map<AbstractVillager, Boolean> boughtSomething = new WeakHashMap<>();
	private static final Map<AbstractVillager, Long> lastPurchaseComment = new WeakHashMap<>();

	private TradeReactions() {
	}

	public static void tradingChanged(AbstractVillager merchant, Player before, Player after) {
		Kind kind = Speakers.kindOf(merchant);
		Lines lines = LINES.get(kind);
		if (lines == null || kind == Kind.VILLAGER && !employed((Villager) merchant)) {
			return;
		}
		if (before == null && after != null) {
			boughtSomething.put(merchant, false);
			String greeting;
			if (kind == Kind.MAYOR) {
				greeting = lines.greeting();
			} else if (lines.greeting() != null && ThreadLocalRandom.current().nextBoolean()) {
				greeting = lines.greeting();
			} else {
				greeting = greeting(merchant instanceof Villager villager ? villager.getPlayerReputation(after) : 0);
			}
			say(merchant, after, greeting);
		} else if (before != null && after == null) {
			boolean bought = Boolean.TRUE.equals(boughtSomething.remove(merchant));
			if (!say(merchant, before, bought ? lines.boughtBye() : lines.noPurchaseBye())) {
				say(merchant, before, lines.bye());
			}
		}
	}

	public static void traded(AbstractVillager merchant) {
		Lines lines = LINES.get(Speakers.kindOf(merchant));
		if (lines == null || merchant.getTradingPlayer() == null) {
			return;
		}
		boughtSomething.put(merchant, true);
		DialogEngine engine = DialogEngine.get();
		Long last = lastPurchaseComment.get(merchant);
		DialogEngine.Speech speech = engine == null ? null : engine.speech(merchant);
		boolean levelling = speech != null && (speech.dialog().id().equals(LEVELLED_UP) || speech.dialog().id().equals(REACHED_MASTER));
		if (engine != null && lines.purchase() != null && !levelling && (last == null || last != engine.now())) {
			lastPurchaseComment.put(merchant, engine.now());
			say(merchant, merchant.getTradingPlayer(), lines.purchase());
		}
	}

	/** Called (through a mixin) when a villager's trading level goes up: a cheer - a bigger one at master. */
	public static void levelledUp(Villager villager) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null) {
			return;
		}
		Player player = villager.level().getNearestPlayer(villager, Reactions.NEARBY);
		Options options = Options.DEFAULT.forced().asUrgent();
		if (player != null) {
			options = options.facing(player);
		}
		if (villager.getVillagerData().level() >= 5 && engine.speakNow(villager, REACHED_MASTER, options)) {
			return;
		}
		engine.speakNow(villager, LEVELLED_UP, options);
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

	/** Said to the player right away - unless the speaker is already mid trading line. */
	private static boolean say(LivingEntity merchant, Player player, String dialog) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || dialog == null) {
			return false;
		}
		DialogEngine.Speech speech = engine.speech(merchant);
		if (speech != null && TRADE_DIALOGS.contains(speech.dialog().id())) {
			return false;
		}
		Options options = Options.DEFAULT.facing(player).forced().asUrgent();
		if (Speakers.kindOf(merchant) == Kind.MAYOR) {
			options = options.withKinds(Kind.MAYOR).withStates(State.BABY);
		}
		return engine.request(merchant, dialog, options);
	}

	/** Nitwits and the unemployed don't trade, and the add-on gives them no trading lines. */
	private static boolean employed(Villager villager) {
		String profession = Speakers.profession(villager);
		return !profession.equals("none") && !profession.equals("nitwit");
	}
}
