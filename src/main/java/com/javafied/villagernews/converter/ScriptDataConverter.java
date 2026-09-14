package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the data tables out of the add-on's behavior script, so the mod's
 * hand-ported logic can use them without shipping any of them.
 *
 * <p>The script is minified and its property names are obfuscated, so the
 * keys below are the ones add-on 1.0 uses. Output ({@code server/dialogs.json},
 * outside {@code assets/} since only the server reads it):
 * <pre>{@code
 * { "dialogs": { "<id>": { "lines": [ { "animation", "sound", "duration", "weight",
 *                                        "subtitles": [ { "time", "text" } ] } ],
 *                           "global_cooldown": { "any", "same" },   // optional, seconds
 *                           "entity_cooldown": { "any", "same" },   // optional, seconds
 *                           "tags": { "<tag>": { "global", "entity" } } } },
 *   "hurt_sounds": { "adult": [ "<sound>" ], "baby": [ "<sound>" ] },
 *   "conversations": [ [ "<dialog id>", ... ] ] }   // parts, spoken alternately by two villagers
 * }</pre>
 */
public final class ScriptDataConverter {
	/** {@code name=Dialog.<register>({...})}: every dialog the script defines. */
	private static final Pattern DIALOG = Pattern.compile("([A-Za-z_$][\\w$]*)=Dialog\\.[A-Za-z_$][\\w$]*\\((?=\\{id:)");
	/** The hurt-reaction table: {@code {<state>:[{soundId:..,<animation>:..}, ..], ..}}. */
	private static final Pattern HURT_SOUNDS = Pattern.compile("=(?=\\{[A-Za-z_$][\\w$]*:\\[\\{soundId:\"[^\"]*\",[A-Za-z_$][\\w$]*:\"[^\"]*\"\\})");

	/** The two-villager conversation table: {@code [{1:<dialog>,2:<dialog>,..},..]}. */
	private static final Pattern CONVERSATIONS = Pattern.compile("=(?=\\[\\{1:[A-Za-z_$][\\w$]*,2:[A-Za-z_$][\\w$]*)");

	private static final String LINES = "slhkqn";
	private static final String SUBTITLES = "aswuwr";
	private static final String SUBTITLE_TEXT = "ysyeto";
	private static final String GLOBAL_COOLDOWN = "jqgklx";
	private static final String ENTITY_COOLDOWN = "csiavd";
	private static final String COOLDOWN_ANY = "iqirsz";
	private static final String COOLDOWN_SAME = "didrid";
	private static final String TAG_GLOBAL = "plgoli";
	private static final String TAG_ENTITY = "andugb";
	/** The script's names for "adult" and "baby" entity states. */
	private static final Map<String, String> STATES = Map.of("jgrldl", "adult", "jiixbx", "baby");

	private ScriptDataConverter() {
	}

	/** @return how many dialogs were extracted */
	public static int convert(Path behaviorPack, Path outputDir) throws IOException {
		Path scripts = behaviorPack == null ? null : behaviorPack.resolve("scripts");
		JsonObject dialogs = new JsonObject();
		JsonObject hurtSounds = new JsonObject();
		JsonArray conversations = new JsonArray();
		if (scripts != null && Files.isDirectory(scripts)) {
			try (var stream = Files.walk(scripts)) {
				for (Path file : stream.filter(p -> p.toString().endsWith(".js")).toList()) {
					extract(Files.readString(file), dialogs, hurtSounds, conversations);
				}
			}
		}
		JsonObject root = new JsonObject();
		root.add("dialogs", dialogs);
		root.add("hurt_sounds", hurtSounds);
		root.add("conversations", conversations);
		ConverterUtil.writeJson(outputDir.resolve("server").resolve("dialogs.json"), root);
		return dialogs.size();
	}

	static void extract(String script, JsonObject dialogsOut, JsonObject hurtSoundsOut, JsonArray conversationsOut) {
		JsLiteral js = new JsLiteral(script);
		// A dialog referenced from inside a literal (e.g. a table of dialogs) resolves to its id.
		Map<String, String> dialogVariables = new java.util.HashMap<>();
		Matcher names = DIALOG.matcher(script);
		while (names.find()) {
			Matcher id = Pattern.compile("\\{id:\"([^\"]+)\"").matcher(script).region(names.end(), script.length());
			if (id.lookingAt()) {
				dialogVariables.put(names.group(1), id.group(1));
			}
		}
		js.setResolver(name -> dialogVariables.containsKey(name) ? new JsonPrimitive(dialogVariables.get(name)) : null);

		Matcher m = DIALOG.matcher(script);
		while (m.find()) {
			JsonObject raw = js.parseAt(m.end()).getAsJsonObject();
			dialogsOut.add(raw.get("id").getAsString(), dialog(raw));
		}

		Matcher hurt = HURT_SOUNDS.matcher(script);
		if (hurt.find()) {
			JsonObject table = js.parseAt(hurt.end()).getAsJsonObject();
			for (Map.Entry<String, JsonElement> state : table.entrySet()) {
				JsonArray sounds = new JsonArray();
				for (JsonElement entry : state.getValue().getAsJsonArray()) {
					sounds.add(entry.getAsJsonObject().get("soundId"));
				}
				hurtSoundsOut.add(STATES.getOrDefault(state.getKey(), state.getKey()), sounds);
			}
		}

		Matcher conversations = CONVERSATIONS.matcher(script);
		while (conversations.find()) {
			JsonElement table = js.parseAt(conversations.end());
			for (JsonElement conversation : table.getAsJsonArray()) {
				JsonObject parts = conversation.getAsJsonObject();
				JsonArray ordered = new JsonArray();
				for (int part = 1; parts.has(Integer.toString(part)); part++) {
					ordered.add(parts.get(Integer.toString(part)));
				}
				conversationsOut.add(ordered);
			}
		}
	}

	private static JsonObject dialog(JsonObject raw) {
		JsonObject out = new JsonObject();
		JsonArray lines = new JsonArray();
		for (JsonElement element : raw.getAsJsonArray(LINES)) {
			JsonObject line = element.getAsJsonObject();
			JsonObject outLine = new JsonObject();
			outLine.add("animation", line.get("animationName"));
			outLine.add("sound", line.get("soundId"));
			outLine.add("duration", line.get("duration"));
			outLine.addProperty("weight", line.has("weight") ? line.get("weight").getAsDouble() : 1);
			JsonArray subtitles = new JsonArray();
			if (line.has(SUBTITLES)) {
				List<Map.Entry<String, JsonElement>> timed = line.getAsJsonObject(SUBTITLES).entrySet().stream()
						.sorted((a, b) -> Double.compare(Double.parseDouble(a.getKey()), Double.parseDouble(b.getKey())))
						.toList();
				for (Map.Entry<String, JsonElement> entry : timed) {
					JsonObject subtitle = new JsonObject();
					subtitle.addProperty("time", Double.parseDouble(entry.getKey()));
					subtitle.add("text", entry.getValue().getAsJsonObject().get(SUBTITLE_TEXT));
					subtitles.add(subtitle);
				}
			}
			outLine.add("subtitles", subtitles);
			lines.add(outLine);
		}
		out.add("lines", lines);
		cooldown(raw, GLOBAL_COOLDOWN, "global_cooldown", out);
		cooldown(raw, ENTITY_COOLDOWN, "entity_cooldown", out);
		if (raw.has("tags")) {
			JsonObject tags = new JsonObject();
			for (Map.Entry<String, JsonElement> tag : raw.getAsJsonObject("tags").entrySet()) {
				JsonObject in = tag.getValue().getAsJsonObject();
				JsonObject t = new JsonObject();
				if (in.has(TAG_GLOBAL)) {
					t.add("global", in.get(TAG_GLOBAL));
				}
				if (in.has(TAG_ENTITY)) {
					t.add("entity", in.get(TAG_ENTITY));
				}
				tags.add(tag.getKey(), t);
			}
			out.add("tags", tags);
		}
		return out;
	}

	private static void cooldown(JsonObject raw, String key, String outKey, JsonObject out) {
		if (!raw.has(key)) {
			return;
		}
		JsonObject in = raw.getAsJsonObject(key);
		JsonObject c = new JsonObject();
		if (in.has(COOLDOWN_ANY)) {
			c.add("any", in.get(COOLDOWN_ANY));
		}
		if (in.has(COOLDOWN_SAME)) {
			c.add("same", in.get(COOLDOWN_SAME));
		}
		out.add(outKey, c);
	}
}
