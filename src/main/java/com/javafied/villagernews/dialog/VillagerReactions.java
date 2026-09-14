package com.javafied.villagernews.dialog;

import com.javafied.villagernews.dialog.DialogEngine.Options;
import com.javafied.villagernews.dialog.DialogEngine.Speech;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hand port of the add-on script's villager triggers - the reasons a villager
 * speaks up. Dialog ids are the add-on's own (see {@link DialogLibrary}).
 */
public final class VillagerReactions {
	// Idle chatter, by context (the script's txggyx).
	private static final String OTHER_DIMENSION = "ycynep";
	private static final String IN_THE_END = "wwcbib";
	private static final String IN_THE_NETHER = "zpjrtq";
	private static final String UP_HIGH = "qarzxp";
	private static final String UNDERGROUND = "mgiaiw";
	private static final String COLD_BIOME = "tftmbe";
	private static final String HOT_BIOME = "felign";
	private static final String NITWIT = "uookqp";
	private static final String UNEMPLOYED = "gbxzxv";
	private static final String EMPLOYED = "lvigit";
	// Real-world calendar chatter (the script's siigqd).
	private static final Map<DayOfWeek, String> WEEKDAY = Map.of(DayOfWeek.SUNDAY, "uzvatl", DayOfWeek.MONDAY, "gkvlqc",
			DayOfWeek.TUESDAY, "dkpihl", DayOfWeek.WEDNESDAY, "gwakiz", DayOfWeek.THURSDAY, "zglkgp",
			DayOfWeek.FRIDAY, "ypyumu", DayOfWeek.SATURDAY, "ildosa");
	private static final String WEEKEND = "bkyidl";
	private static final String OCTOBER = "mltyge";
	private static final String DECEMBER = "tkkegl";
	private static final String APRIL_FOOLS = "obitls";
	private static final String NEW_YEARS_EVE = "xljknt";
	/** Conversations both villagers can have with their noses on (the others are about losing one). */
	private static final String NOSED_CONVERSATIONS = "gmrypk";
	// Dialog tags the hurt reaction respects.
	private static final String TAG_NO_HURT_VOICE = "ouqfaa";
	private static final String TAG_KEEPS_TALKING = "auevko";

	private static final Set<String> COLD_BIOMES = Set.of("snowy_beach", "snowy_taiga", "deep_cold_ocean",
			"deep_frozen_ocean", "frozen_ocean", "frozen_peaks", "frozen_river", "snowy_plains", "ice_spikes",
			"jagged_peaks", "snowy_slopes");
	private static final Set<String> HOT_BIOMES = Set.of("desert", "badlands", "wooded_badlands", "eroded_badlands");

	/** The add-on's idle timer re-arms every 19-37 seconds per villager. */
	private static final int CHATTER_MIN_TICKS = 19 * 20;
	private static final int CHATTER_MAX_TICKS = 37 * 20;
	private static final double CONVERSATION_DISTANCE = 2.5;
	private static final int CONVERSATION_REST_TICKS = 1400;

	private static final Map<Villager, Long> nextChatter = new WeakHashMap<>();
	private static final Map<Villager, Conversation> conversations = new WeakHashMap<>();
	private static final Map<Villager, Long> lastConversation = new WeakHashMap<>();

	/** Two villagers taking turns through a conversation's parts. */
	private record Conversation(Villager first, Villager second, List<String> parts) {
		Villager other(Villager v) {
			return v == first ? second : first;
		}

		void end() {
			conversations.remove(first, this);
			conversations.remove(second, this);
		}
	}

	private VillagerReactions() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(VillagerReactions::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof Villager villager && damageTaken > 0) {
				hurt(villager);
			}
		});
		DialogEngine.onFinished(VillagerReactions::continueConversation);
	}

	private static void tick(MinecraftServer server) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || engine.library().isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		long now = engine.now();
		for (ServerLevel level : server.getAllLevels()) {
			Set<Villager> nearPlayers = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				nearPlayers.addAll(level.getEntitiesOfClass(Villager.class,
						player.getBoundingBox().inflate(DialogEngine.RANGE)));
			}
			for (Villager villager : nearPlayers) {
				Long due = nextChatter.get(villager);
				if (due == null || now >= due) {
					nextChatter.put(villager, now + ThreadLocalRandom.current().nextInt(CHATTER_MIN_TICKS, CHATTER_MAX_TICKS + 1));
					if (due != null) {
						chatter(engine, villager);
					}
				}
			}
		}
	}

	/** The script's idle trigger ({@code vszwnq}): the odd calendar remark, else a chat with a neighbour or a comment. */
	private static void chatter(DialogEngine engine, Villager villager) {
		if (villager.isBaby()) {
			VillagerLifeReactions.babyAtPlay(villager);
			return;
		}
		if (villager.getVehicle() instanceof AbstractBoat || VillagerRoutineReactions.eveningGathering(engine, villager)) {
			return;
		}
		if (ThreadLocalRandom.current().nextDouble() > 0.8) {
			String dialog = pick(calendarDialogs(LocalDate.now()));
			if (dialog != null) {
				engine.request(villager, dialog, Options.DEFAULT.ignoringCooldowns(false, true, false));
			}
			return;
		}
		if (conversations.containsKey(villager)) {
			return;
		}
		Villager partner = partner(engine, villager);
		Long last = lastConversation.get(villager);
		if (partner != null && (last == null || engine.now() - last >= CONVERSATION_REST_TICKS)) {
			startConversation(engine, villager, partner);
			return;
		}
		engine.request(villager, contextDialog(villager), Options.DEFAULT.ignoringCooldowns(false, true, false));
	}

	/** Weekday remarks, weekend, October, December, and a couple of special dates. The add-on meant to; see below. */
	public static List<String> calendarDialogs(LocalDate date) {
		// The add-on's minifier renamed the weekday and date keys of these tables but not the strings
		// looked up in them, so in Bedrock only the October/December/weekend ones ever play.
		// The weekday and date ones are restored here as intended.
		List<String> dialogs = new ArrayList<>();
		dialogs.add(WEEKDAY.get(date.getDayOfWeek()));
		if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			dialogs.add(WEEKEND);
		}
		if (date.getMonth() == Month.OCTOBER) {
			dialogs.add(OCTOBER);
		}
		if (date.getMonth() == Month.DECEMBER) {
			dialogs.add(DECEMBER);
		}
		if (date.getMonth() == Month.APRIL && date.getDayOfMonth() == 1) {
			dialogs.add(APRIL_FOOLS);
		}
		if (date.getMonth() == Month.DECEMBER && date.getDayOfMonth() == 31) {
			dialogs.add(NEW_YEARS_EVE);
		}
		return dialogs;
	}

	/** Where the villager is and what it does for a living (the script's {@code txggyx}). */
	private static String contextDialog(Villager villager) {
		List<String> options = new ArrayList<>();
		Level level = villager.level();
		ResourceKey<Level> dimension = level.dimension();
		if (dimension != Level.OVERWORLD) {
			options.add(OTHER_DIMENSION);
			if (dimension == Level.END) {
				options.add(IN_THE_END);
			} else if (dimension == Level.NETHER) {
				options.add(IN_THE_NETHER);
			}
		}
		BlockPos pos = villager.blockPosition();
		if (villager.getY() > 190) {
			options.add(UP_HIGH);
		} else if (villager.getY() < 60 && !level.canSeeSky(pos.above())) {
			options.add(UNDERGROUND);
		}
		String biome = level.getBiome(pos).unwrapKey().map(ResourceKey::identifier).map(id -> id.getPath()).orElse("");
		if (COLD_BIOMES.contains(biome)) {
			options.add(COLD_BIOME);
		} else if (HOT_BIOMES.contains(biome)) {
			options.add(HOT_BIOME);
		}
		options.add(switch (profession(villager)) {
			case "nitwit" -> NITWIT;
			case "none" -> UNEMPLOYED;
			default -> EMPLOYED;
		});
		return pick(options);
	}

	/**
	 * A villager within 2.5 blocks who's at work (the script's {@code guicgv}),
	 * not busy talking and not already in a conversation.
	 */
	private static Villager partner(DialogEngine engine, Villager villager) {
		Villager nearest = null;
		double best = Double.MAX_VALUE;
		for (Villager other : villager.level().getEntitiesOfClass(Villager.class,
				villager.getBoundingBox().inflate(CONVERSATION_DISTANCE))) {
			double distance = other.distanceTo(villager);
			if (other != villager && distance <= CONVERSATION_DISTANCE && distance < best) {
				nearest = other;
				best = distance;
			}
		}
		if (nearest == null || nearest.isBaby() || engine.isTalking(nearest) || conversations.containsKey(nearest)
				|| !atWork(nearest)) {
			return null;
		}
		return nearest;
	}

	/** The script's working hours: day ticks 0-8000 and 10000-12000 (unemployed 10000-11000, nitwits 2000-12000). */
	private static boolean atWork(Villager villager) {
		long time = villager.level().getOverworldClockTime() % 24000;
		return switch (profession(villager)) {
			case "none" -> time < 8000 || time >= 10000 && time < 11000;
			case "nitwit" -> time >= 2000 && time < 12000;
			default -> time < 8000 || time >= 10000 && time < 12000;
		};
	}

	private static void startConversation(DialogEngine engine, Villager villager, Villager partner) {
		List<List<String>> starters = engine.library().conversations().stream()
				.filter(parts -> parts.getFirst().startsWith(NOSED_CONVERSATIONS)).toList();
		if (starters.isEmpty()) {
			return;
		}
		startConversation(engine, villager, partner, starters.get(ThreadLocalRandom.current().nextInt(starters.size())).getFirst());
	}

	/** Starts the conversation beginning with this dialog (or just that one line, if it starts none). */
	static void startConversation(DialogEngine engine, Villager villager, Villager partner, String first) {
		List<String> parts = engine.library().conversations().stream().filter(c -> c.getFirst().equals(first)).findFirst()
				.orElse(List.of(first));
		if (engine.speakNow(villager, first, Options.DEFAULT.facing(partner))) {
			Conversation conversation = new Conversation(villager, partner, parts);
			conversations.put(villager, conversation);
			conversations.put(partner, conversation);
			lastConversation.put(villager, engine.now());
			lastConversation.put(partner, engine.now());
		}
	}

	/**
	 * When one part of a conversation ends, the other villager answers with the
	 * next. (The add-on looks the next part up by a numbered dialog id its own
	 * minifier renamed, so in Bedrock the answer never comes; restored here.)
	 */
	private static void continueConversation(Speech speech, boolean completed) {
		if (!(speech.speaker() instanceof Villager speaker)) {
			return;
		}
		Conversation conversation = conversations.get(speaker);
		if (conversation == null) {
			return;
		}
		int part = conversation.parts().indexOf(speech.dialog().id());
		Villager listener = conversation.other(speaker);
		DialogEngine engine = DialogEngine.get();
		boolean answered = completed && engine != null && part >= 0 && part + 1 < conversation.parts().size()
				&& listener.isAlive() && listener.distanceTo(speaker) <= CONVERSATION_DISTANCE
				&& engine.speakNow(listener, conversation.parts().get(part + 1),
				Options.DEFAULT.facing(speaker).ignoringCooldowns(true, true, false));
		if (!answered) {
			conversation.end();
		}
	}

	/** A pained voice line, and whatever it was saying gets cut off (the script's entityHurt handler). */
	private static void hurt(Villager villager) {
		DialogEngine engine = DialogEngine.get();
		if (engine == null || !villager.isAlive()) {
			return;
		}
		engine.markHurt(villager);
		Speech speech = engine.speech(villager);
		if (speech == null || !speech.dialog().tags().containsKey(TAG_NO_HURT_VOICE)) {
			String sound = pick(engine.library().hurtSounds(villager.isBaby()));
			if (sound != null) {
				villager.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(),
						BedrockSoundIds.holder(sound), SoundSource.NEUTRAL, 1f, 1f);
			}
		}
		if (speech != null && !speech.dialog().tags().containsKey(TAG_KEEPS_TALKING)) {
			engine.stop(villager);
		}
	}

	/** Bedrock profession names mapped from Java's ("none" = unemployed). */
	private static String profession(Villager villager) {
		return villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
	}

	private static <T> T pick(List<T> options) {
		return options.isEmpty() ? null : options.get(ThreadLocalRandom.current().nextInt(options.size()));
	}
}
