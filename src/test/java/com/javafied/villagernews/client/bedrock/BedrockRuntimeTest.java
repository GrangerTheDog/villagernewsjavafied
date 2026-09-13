package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.client.bedrock.BedrockRuntime.Layer;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the real add-on's client logic (as converted into dev/converted by the runConverter task). */
class BedrockRuntimeTest {
	private static final Path ASSETS = Path.of("dev/converted/assets/villagernewsjavafied");
	private static BedrockDefinitions.Snapshot defs;

	@BeforeAll
	static void load() throws IOException {
		Path bedrock = ASSETS.resolve("bedrock");
		assumeTrue(Files.isDirectory(bedrock), "run ./gradlew runConverter first");
		defs = BedrockDefinitions.parse(read(bedrock.resolve("entity")), read(bedrock.resolve("render_controllers")),
				read(bedrock.resolve("materials")), List.of(json(bedrock.resolve("properties.json"))));
	}

	/** An adult, plains-biome farmer with skin 2 - what a freshly spawned vanilla villager could look like. */
	private static MutableObjectBinding queries(String identifier, int profession) {
		Map<String, JsonElement> properties = defs.properties(identifier);
		MutableObjectBinding q = new MutableObjectBinding();
		q.set("is_alive", Value.of(1));
		q.set("skin_id", Value.of(2));
		q.set("variant", Value.of(profession));
		q.set("mark_variant", Value.of(0));
		q.set("graphics_mode_is_any", (Function<Object>) (ctx, args) -> Value.of(1));
		q.set("is_name_any", (Function<Object>) (ctx, args) -> Value.nil());
		q.set("has_property", (Function<Object>) (ctx, args) -> Value.of(properties.containsKey(args.next().eval().getAsString())));
		q.set("property", (Function<Object>) (ctx, args) -> {
			JsonElement v = properties.get(args.next().eval().getAsString());
			if (v == null) {
				return Value.nil();
			}
			if (v.getAsJsonPrimitive().isBoolean()) {
				return Value.of(v.getAsBoolean());
			}
			return v.getAsJsonPrimitive().isNumber() ? Value.of(v.getAsDouble()) : StringValue.of(v.getAsString());
		});
		q.set("any", (Function<Object>) (ctx, args) -> {
			double subject = args.next().eval().getAsNumber();
			for (int i = 1; i < args.length(); i++) {
				if (args.next().eval().getAsNumber() == subject) {
					return Value.of(1);
				}
			}
			return Value.nil();
		});
		q.set("position", (Function<Object>) (ctx, args) -> Value.nil());
		return q;
	}

	private static RenderPlan plan(String path, int profession) {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity(path);
		RenderPlan plan = BedrockRuntime.plan(defs, ce, new MutableObjectBinding(), true, queries(ce.identifier(), profession));
		System.out.println(path + " (profession " + profession + "): scale " + plan.scale());
		plan.layers().forEach(layer -> System.out.println("  " + layer));
		return plan;
	}

	@Test
	void villagerIsSkinPlusClothingPlusProfession() {
		RenderPlan plan = plan("villager", 1);
		assertTrue(plan.layers().size() >= 3, "expected skin + biome clothing + profession layers");
		assertEquals("textures/oreville/vn/dil.png", plan.layers().getFirst().texture().getPath(),
				"normal adult villagers use the dil skin, not a special-state one");
		assertEquals(0.9375f, plan.scale(), 1e-6);
		assertTexturesExist(plan);
	}

	@Test
	void unemployedVillagerHasNoProfessionOrBadgeLayer() {
		assertTrue(plan("villager", 0).layers().size() < plan("villager", 1).layers().size());
	}

	@Test
	void sheepShowsWoolUnlessSheared() {
		RenderPlan plan = plan("mlkxjo", 0);
		assertTexturesExist(plan);
		assertTrue(plan.isBoneVisible("oggd_head"), "wool bones visible on an unsheared sheep");
		assertTrue(plan.isBoneVisible("body"));
	}

	@Test
	void everyVillagerVariantProducesALayeredPlan() {
		for (String variant : List.of("villager", "ghibss", "txczvv", "vwpagn", "poztxf", "xcrjxf", "ilvfra")) {
			RenderPlan plan = plan(variant, 1);
			assertFalse(plan.layers().isEmpty(), variant + " produced no layers");
			assertTexturesExist(plan);
		}
	}

	private static void assertTexturesExist(RenderPlan plan) {
		for (Layer layer : plan.layers()) {
			assertTrue(Files.exists(ASSETS.resolve(layer.texture().getPath())), "missing texture " + layer.texture());
			assertTrue(Files.exists(ASSETS.resolve("geckolib/models/" + layer.model().getPath() + ".geo.json")),
					"missing model " + layer.model());
		}
	}

	private static List<JsonObject> read(Path dir) throws IOException {
		List<JsonObject> out = new ArrayList<>();
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				out.add(json(file));
			}
		}
		return out;
	}

	private static JsonObject json(Path file) throws IOException {
		return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
	}
}
