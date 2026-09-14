package com.javafied.villagernews.dialog;

import com.javafied.villagernews.ConvertedPack;
import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.dialog.DialogLibrary.Dialog;
import com.javafied.villagernews.dialog.DialogLibrary.Line;
import com.javafied.villagernews.dialog.DialogLibrary.TagCooldown;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Hand port of the add-on script's dialog system: who may say what, when.
 *
 * <p>A trigger {@link #request requests} a dialog for a speaker. The request
 * waits (up to 2 seconds) in a queue drained every 3 ticks, which keeps at
 * most {@value #MAX_SPEAKERS} villagers talking at once and starts at most
 * one line every {@value #START_GAP_TICKS} ticks. When it starts, one of the
 * dialog's lines is picked (by weight, never the one it said last time), sent
 * to nearby clients - which play the voice, lip sync and subtitles - and the
 * dialog's cooldowns kick in: per dialog, per speaker and per tag, globally
 * and for that speaker. While talking, the speaker stands still and looks at
 * whoever it's talking to.
 *
 * <p>Numbers are the script's, at its default "Chattiness" setting.
 */
public final class DialogEngine {
	/** Which speakers a request accepts; the script's {@code states} list. */
	public enum State {
		ADULT, BABY,
		/** Allowed while asleep (otherwise sleepers stay quiet). */
		SLEEPING,
		/** Allowed right after being hurt or with a monster close by (otherwise it waits). */
		EVEN_IN_DANGER
	}

	/**
	 * @param interrupt            cut off whatever the speaker is saying
	 * @param ignoreEntityCooldown ...and the speaker's own cooldowns
	 * @param ignoreGlobalCooldown ...and the world-wide ones
	 * @param ignoreTagCooldown    ...and the tag ones
	 * @param facing               who the speaker turns to while talking
	 * @param urgent               skip the "3 speakers / 10 ticks apart" pacing
	 */
	public record Options(Set<State> states, boolean interrupt, boolean ignoreEntityCooldown, boolean ignoreGlobalCooldown,
			boolean ignoreTagCooldown, Entity facing, boolean urgent) {
		public static final Options DEFAULT = new Options(EnumSet.of(State.ADULT), false, false, false, false, null, false);

		public Options withStates(State first, State... rest) {
			return new Options(EnumSet.of(first, rest), interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing, urgent);
		}

		public Options facing(Entity target) {
			return new Options(states, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, target, urgent);
		}

		public Options ignoringCooldowns(boolean entity, boolean global, boolean tags) {
			return new Options(states, interrupt, entity, global, tags, facing, urgent);
		}

		public Options interrupting() {
			return new Options(states, true, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing, urgent);
		}

		public Options asUrgent() {
			return new Options(states, interrupt, ignoreEntityCooldown, ignoreGlobalCooldown, ignoreTagCooldown, facing, true);
		}
	}

	/** A line being spoken; the speaker counts as busy until {@link #releaseTick}. */
	public record Speech(LivingEntity speaker, Dialog dialog, int line, long endTick, long releaseTick, Entity facing,
			Vec3 frozenAt) {
		public boolean talking(long now) {
			return now < endTick;
		}
	}

	private record Request(LivingEntity speaker, Dialog dialog, Options options, long expires) {
	}

	/** Why a speaker's last request didn't turn into a line - for the debug view. */
	private record Refusal(String dialog, String reason, long tick) {
	}

	/** Blocked-until ticks. */
	private static final class Cooldowns {
		long any;
		final Map<String, Long> dialogs = new HashMap<>();
		final Map<String, Long> tags = new HashMap<>();
	}

	public static final double RANGE = 16;
	private static final int MAX_SPEAKERS = 3;
	private static final int START_GAP_TICKS = 10;
	private static final int QUEUE_INTERVAL = 3;
	private static final int REQUEST_TIMEOUT = 40;
	/** After its line ends, a speaker stays busy this long (the script clears subtitles then). */
	private static final int LINGER_TICKS = 10;
	private static final int HURT_SILENCE_TICKS = 40;
	private static final double MONSTER_RADIUS = 8;
	/** A player this close to someone already talking won't hear a second villager start. */
	private static final double LISTENER_RADIUS = 10;
	private static final double OVERLAP_RADIUS = 5;
	/** The "Chattiness" multiplier: cooldowns are divided by it. */
	private static final double CHATTINESS = 1;

	private static DialogEngine current;
	/** Registered once at startup; every server's engine calls them. */
	private static final List<BiConsumer<Speech, Boolean>> LISTENERS = new ArrayList<>();

	private final MinecraftServer server;
	private final DialogLibrary library;
	private final Cooldowns global = new Cooldowns();
	private final Map<Entity, Cooldowns> speakerCooldowns = new WeakHashMap<>();
	private final Map<Entity, Long> lastHurt = new WeakHashMap<>();
	private final Map<Entity, Speech> speeches = new HashMap<>();
	private final Map<String, Integer> lastLine = new HashMap<>();
	private final Map<Entity, Refusal> lastRefusal = new WeakHashMap<>();
	private final List<Request> queue = new ArrayList<>();
	private long lastStart = -START_GAP_TICKS;

	private DialogEngine(MinecraftServer server, DialogLibrary library) {
		this.server = server;
		this.library = library;
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			DialogLibrary library;
			try {
				library = DialogLibrary.load(ConvertedPack.serverData("dialogs.json"));
			} catch (IOException | RuntimeException e) {
				VillagerNewsJavafied.LOGGER.error("Couldn't read the converted add-on's dialogs; villagers will stay quiet", e);
				library = DialogLibrary.EMPTY;
			}
			VillagerNewsJavafied.LOGGER.info("Loaded {} villager dialogs", library.size());
			current = new DialogEngine(server, library);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = null);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (current != null && current.server == server) {
				current.tick();
			}
		});
	}

	/** Called when a line ends: {@code true} if it played out, {@code false} if it was cut short. */
	public static void onFinished(BiConsumer<Speech, Boolean> listener) {
		LISTENERS.add(listener);
	}

	/** Null while no server is running. */
	public static DialogEngine get() {
		return current;
	}

	public DialogLibrary library() {
		return library;
	}

	public long now() {
		return server.getTickCount();
	}

	/**
	 * Queues {@code dialogId} for {@code speaker}; it starts once pacing allows,
	 * or is dropped after 2 seconds. False if it can't be said at all right now.
	 */
	public boolean request(LivingEntity speaker, String dialogId, Options options) {
		Dialog dialog = library.get(dialogId);
		String refusal = dialog == null ? "not in the add-on" : whyNot(speaker, options);
		if (refusal == null) {
			refusal = cooldownBlocking(speaker, dialog, options);
		}
		if (refusal != null) {
			return refuse(speaker, dialogId, refusal);
		}
		queue.add(new Request(speaker, dialog, options, now() + REQUEST_TIMEOUT));
		return true;
	}

	private boolean refuse(Entity speaker, String dialogId, String reason) {
		lastRefusal.put(speaker, new Refusal(dialogId, reason, now()));
		return false;
	}

	/** Starts {@code dialogId} right away if it can, skipping the queue (replies in a conversation). */
	public boolean speakNow(LivingEntity speaker, String dialogId, Options options) {
		Dialog dialog = library.get(dialogId);
		String refusal = dialog == null ? "not in the add-on" : whyNot(speaker, options);
		return refusal == null ? start(speaker, dialog, options) : refuse(speaker, dialogId, refusal);
	}

	/** What the speaker is currently saying, if anything. */
	public Speech speech(Entity speaker) {
		Speech speech = speeches.get(speaker);
		return speech != null && speech.talking(now()) ? speech : null;
	}

	public boolean isTalking(Entity speaker) {
		return speech(speaker) != null;
	}

	/** Cuts the speaker off (the voice stops for everyone listening). */
	public void stop(Entity speaker) {
		Speech speech = speeches.remove(speaker);
		if (speech == null) {
			return;
		}
		for (ServerPlayer player : PlayerLookup.tracking(speaker)) {
			if (ServerPlayNetworking.canSend(player, DialogPayloads.Stop.TYPE)) {
				ServerPlayNetworking.send(player, new DialogPayloads.Stop(speaker.getId()));
			}
		}
		LISTENERS.forEach(listener -> listener.accept(speech, false));
	}

	/** A few lines about the speaker's dialog state, for the debug view. */
	public List<String> describe(LivingEntity speaker) {
		long now = now();
		List<String> lines = new ArrayList<>();
		Speech speech = speeches.get(speaker);
		if (speech != null && speech.talking(now)) {
			lines.add("Talking: " + speech.dialog().id() + " (line " + (speech.line() + 1) + " of "
					+ speech.dialog().lines().size() + ", " + seconds(speech.endTick() - now) + " left)");
		} else {
			lines.add("Quiet (" + speakers() + " of " + MAX_SPEAKERS + " villagers talking nearby)");
		}
		Cooldowns own = speakerCooldowns.get(speaker);
		List<String> cooldowns = new ArrayList<>();
		if (own != null) {
			if (own.any > now) {
				cooldowns.add("any " + seconds(own.any - now));
			}
			own.dialogs.forEach((id, until) -> {
				if (until > now) {
					cooldowns.add(id + " " + seconds(until - now));
				}
			});
			own.tags.forEach((tag, until) -> {
				if (until > now) {
					cooldowns.add("tag " + tag + " " + seconds(until - now));
				}
			});
		}
		lines.add(cooldowns.isEmpty() ? "No cooldowns" : "Cooldowns: " + String.join(", ", cooldowns));
		List<String> waiting = new ArrayList<>();
		for (Request request : queue) {
			if (request.speaker() == speaker) {
				waiting.add(request.dialog().id() + " " + seconds(request.expires() - now));
			}
		}
		if (!waiting.isEmpty()) {
			lines.add("Waiting: " + String.join(", ", waiting));
		}
		Refusal refusal = lastRefusal.get(speaker);
		if (refusal != null) {
			lines.add("Last refused: " + refusal.dialog() + " - " + refusal.reason() + " (" + seconds(now - refusal.tick()) + " ago)");
		}
		return lines;
	}

	private static String seconds(long ticks) {
		return String.format(java.util.Locale.ROOT, "%.1fs", ticks / 20.0);
	}

	/** Villagers keep quiet for 2 seconds after being hurt. */
	public void markHurt(Entity speaker) {
		lastHurt.put(speaker, now());
	}

	/** The script's per-speaker checks ({@code ihylcx}): age, sleep, danger. Null if it may talk, else why not. */
	private String whyNot(LivingEntity speaker, Options options) {
		if (!speaker.isAlive()) {
			return "dead";
		}
		if (!options.states().contains(speaker.isBaby() ? State.BABY : State.ADULT)) {
			return speaker.isBaby() ? "a line for adults" : "a line for babies";
		}
		if (speaker.isSleeping() && !options.states().contains(State.SLEEPING)) {
			return "asleep";
		}
		if (options.states().contains(State.EVEN_IN_DANGER)) {
			return null;
		}
		Long hurt = lastHurt.get(speaker);
		if (hurt != null && now() <= hurt + HURT_SILENCE_TICKS) {
			return "just got hurt";
		}
		boolean monster = !speaker.level().getEntitiesOfClass(Mob.class, speaker.getBoundingBox().inflate(MONSTER_RADIUS),
				mob -> mob instanceof Enemy && mob.distanceTo(speaker) <= MONSTER_RADIUS).isEmpty();
		return monster ? "monster nearby" : null;
	}

	/** Null if no cooldown blocks this dialog for this speaker, else which one does. */
	private String cooldownBlocking(LivingEntity speaker, Dialog dialog, Options options) {
		long now = now();
		Cooldowns own = speakerCooldowns.get(speaker);
		if (!options.ignoreEntityCooldown() && own != null) {
			if (own.any > now) {
				return "speaker cooldown";
			}
			if (own.dialogs.getOrDefault(dialog.id(), 0L) > now) {
				return "speaker cooldown for this dialog";
			}
		}
		if (!options.ignoreGlobalCooldown()) {
			if (global.any > now) {
				return "world cooldown";
			}
			if (global.dialogs.getOrDefault(dialog.id(), 0L) > now) {
				return "world cooldown for this dialog";
			}
		}
		if (!options.ignoreTagCooldown()) {
			for (String tag : dialog.tags().keySet()) {
				if (global.tags.getOrDefault(tag, 0L) > now || own != null && own.tags.getOrDefault(tag, 0L) > now) {
					return "cooldown on tag " + tag;
				}
			}
		}
		return null;
	}

	private void tick() {
		long now = now();
		for (Iterator<Speech> it = speeches.values().iterator(); it.hasNext(); ) {
			Speech speech = it.next();
			LivingEntity speaker = speech.speaker();
			if (!speaker.isAlive() || speaker.isRemoved()) {
				it.remove();
				continue;
			}
			if (now >= speech.releaseTick()) {
				it.remove();
				LISTENERS.forEach(listener -> listener.accept(speech, true));
				continue;
			}
			holdStill(speech);
		}
		if (now % QUEUE_INTERVAL == 0) {
			drainQueue(now);
		}
	}

	/** While talking: face the listener, and don't wander off mid-sentence. */
	private static void holdStill(Speech speech) {
		LivingEntity speaker = speech.speaker();
		Entity facing = speech.facing();
		if (facing != null && facing.isAlive() && facing.level() == speaker.level()) {
			speaker.lookAt(EntityAnchorArgument.Anchor.EYES, facing.getEyePosition());
			if (speaker instanceof Mob mob) {
				mob.getLookControl().setLookAt(facing, 30, 30);
			}
		}
		if (speech.frozenAt() != null) {
			if (speaker instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			Vec3 motion = speaker.getDeltaMovement();
			speaker.setDeltaMovement(0, Math.min(motion.y, 0), 0);
		}
	}

	private void drainQueue(long now) {
		for (Iterator<Request> it = queue.iterator(); it.hasNext(); ) {
			Request request = it.next();
			LivingEntity speaker = request.speaker();
			if (now >= request.expires() || !speaker.isAlive()) {
				it.remove();
				if (speaker.isAlive()) {
					refuse(speaker, request.dialog().id(), "gave up waiting for its turn");
				}
				continue;
			}
			boolean paced = request.options().urgent() || now - lastStart >= START_GAP_TICKS && speakers() < MAX_SPEAKERS;
			if (paced && steady(speaker) && start(speaker, request.dialog(), request.options())) {
				it.remove();
			}
		}
	}

	/** On the ground (or riding), head above water. */
	private static boolean steady(LivingEntity speaker) {
		return (speaker.onGround() || speaker.isPassenger()) && !speaker.isEyeInFluid(FluidTags.WATER);
	}

	private int speakers() {
		return speeches.size();
	}

	/** The script's {@code izbnnu}: final checks, pick a line, broadcast it, start the cooldowns. */
	private boolean start(LivingEntity speaker, Dialog dialog, Options options) {
		if (!speaker.isAlive() || dialog.lines().isEmpty()) {
			return false;
		}
		long now = now();
		if (isTalking(speaker)) {
			if (!options.interrupt()) {
				return refuse(speaker, dialog.id(), "already talking");
			}
			stop(speaker);
		}
		ServerLevel level = (ServerLevel) speaker.level();
		Player nearest = level.getNearestPlayer(speaker, RANGE);
		if (nearest == null) {
			return refuse(speaker, dialog.id(), "no player within " + (int) RANGE + " blocks");
		}
		String cooldown = cooldownBlocking(speaker, dialog, options);
		if (cooldown != null) {
			return refuse(speaker, dialog.id(), cooldown);
		}
		if (!options.interrupt()) {
			if (speakers() >= MAX_SPEAKERS) {
				return refuse(speaker, dialog.id(), MAX_SPEAKERS + " villagers already talking");
			}
			if (nearest.distanceTo(speaker) < LISTENER_RADIUS && someoneElseTalkingNear(nearest, speaker)) {
				return refuse(speaker, dialog.id(), "someone else is talking to that player");
			}
		}
		if (global.any > now) {
			return refuse(speaker, dialog.id(), "world cooldown");
		}

		int index = pickLine(dialog);
		Line line = dialog.lines().get(index);
		lastLine.put(dialog.id(), index);
		lastStart = now;
		speeches.put(speaker, new Speech(speaker, dialog, index, now + line.durationTicks(),
				now + line.durationTicks() + LINGER_TICKS, options.facing(), speaker.onGround() ? speaker.position() : null));
		startCooldowns(speaker, dialog, line);

		DialogPayloads.Line payload = new DialogPayloads.Line(speaker.getId(), line.sound(), line.animation(),
				line.durationTicks(), line.subtitles());
		for (ServerPlayer player : PlayerLookup.tracking(speaker)) {
			if (ServerPlayNetworking.canSend(player, DialogPayloads.Line.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
		return true;
	}

	private boolean someoneElseTalkingNear(Player listener, Entity speaker) {
		for (Speech speech : speeches.values()) {
			LivingEntity other = speech.speaker();
			if (other != speaker && other.level() == listener.level() && other.distanceTo(listener) < OVERLAP_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** By weight, never repeating the dialog's previous line when it has others. */
	private int pickLine(Dialog dialog) {
		List<Line> lines = dialog.lines();
		Integer previous = lastLine.get(dialog.id());
		int skip = previous == null || lines.size() <= 1 ? -1 : previous;
		double total = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (i != skip) {
				total += Math.max(0, lines.get(i).weight());
			}
		}
		if (total <= 0) {
			return skip == 0 && lines.size() > 1 ? 1 : 0;
		}
		double roll = ThreadLocalRandom.current().nextDouble() * total;
		for (int i = 0; i < lines.size(); i++) {
			if (i != skip && (roll -= Math.max(0, lines.get(i).weight())) <= 0) {
				return i;
			}
		}
		return skip == 0 && lines.size() > 1 ? 1 : 0;
	}

	private void startCooldowns(LivingEntity speaker, Dialog dialog, Line line) {
		long now = now();
		Cooldowns own = speakerCooldowns.computeIfAbsent(speaker, e -> new Cooldowns());
		if (dialog.globalCooldown().any() > 0) {
			global.any = now + cooldownTicks(dialog.globalCooldown().any(), line);
		}
		if (dialog.globalCooldown().same() > 0) {
			global.dialogs.put(dialog.id(), now + cooldownTicks(dialog.globalCooldown().same(), line));
		}
		if (dialog.entityCooldown().any() > 0) {
			own.any = now + cooldownTicks(dialog.entityCooldown().any(), line);
		}
		if (dialog.entityCooldown().same() > 0) {
			own.dialogs.put(dialog.id(), now + cooldownTicks(dialog.entityCooldown().same(), line));
		}
		for (Map.Entry<String, TagCooldown> tag : dialog.tags().entrySet()) {
			if (tag.getValue().global() > 0) {
				global.tags.put(tag.getKey(), now + cooldownTicks(tag.getValue().global(), line));
			}
			if (tag.getValue().entity() > 0) {
				own.tags.put(tag.getKey(), now + cooldownTicks(tag.getValue().entity(), line));
			}
		}
	}

	/** A cooldown counts from the end of the line: its length in ticks, plus the line's own. */
	private static long cooldownTicks(double seconds, Line line) {
		return (long) Math.floor(20 * seconds / CHATTINESS) + line.durationTicks();
	}
}
