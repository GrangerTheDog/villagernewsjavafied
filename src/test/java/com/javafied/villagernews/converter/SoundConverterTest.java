package com.javafied.villagernews.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SoundConverterTest {
	private static final Path ASSETS = Path.of("dev/converted/assets");

	/** Java rejects the whole sounds.json over one bad entry, and then every vanilla villager voice is back. */
	@Test
	void silencedVanillaVoicesAreValidJavaSounds() throws IOException {
		Path file = ASSETS.resolve("minecraft/sounds.json");
		assumeTrue(Files.exists(file), "no converted add-on");
		JsonObject events = ConverterUtil.readJson(file);
		for (String event : new String[] {"entity.villager.ambient", "entity.villager.no", "entity.villager.yes",
				"entity.villager.trade", "entity.wandering_trader.ambient", "entity.wandering_trader.no"}) {
			assertTrue(events.has(event), event + " isn't silenced");
		}
		for (Map.Entry<String, JsonElement> event : events.entrySet()) {
			for (JsonElement sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
				JsonObject s = sound.getAsJsonObject();
				assertTrue(s.get("volume").getAsDouble() > 0, event.getKey() + ": Java needs a volume above 0");
				String[] id = s.get("name").getAsString().split(":", 2);
				assertTrue(Files.exists(ASSETS.resolve(id[0]).resolve("sounds").resolve(id[1] + ".ogg")),
						event.getKey() + ": no such sound file " + s.get("name"));
			}
		}
	}
}
