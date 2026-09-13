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
				read(bedrock.resolve("materials")), List.of(json(bedrock.resolve("properties.json"))),
				read(bedrock.resolve("animations")), read(bedrock.resolve("animation_controllers")));
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
		RenderPlan plan = BedrockRuntime.plan(defs, ce, new BedrockRuntime.EntityState(), 0, queries(ce.identifier(), profession), 0);
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
		assertTrue(plan.isBoneVisible("hat"), "the hat bone carries most job outfits");
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

	@Test
	void villagerAnimatesWhileWalkingAndLooking() {
		BedrockDefinitions.ClientEntity ce = defs.clientEntity("villager");
		MutableObjectBinding q = queries(ce.identifier(), 1);
		q.set("is_on_ground", Value.of(1));
		q.set("modified_move_speed", Value.of(0.8));
		q.set("target_x_rotation", Value.of(30));
		q.set("target_y_rotation", Value.of(25));
		BedrockRuntime.EntityState state = new BedrockRuntime.EntityState();
		RenderPlan plan = null;
		for (int frame = 0; frame <= 40; frame++) {
			double time = frame * 0.05;
			q.set("life_time", Value.of(time));
			plan = BedrockRuntime.plan(defs, ce, state, time, q, 0);
		}
		System.out.println("posed bones after 2s: " + plan.poses().size());
		plan.poses().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(40).forEach(e -> {
			BedrockRuntime.Pose p = e.getValue();
			System.out.printf("  %-18s rot(%7.2f %7.2f %7.2f) pos(%6.2f %6.2f %6.2f) scale(%4.2f %4.2f %4.2f)%n",
					e.getKey(), p.rx, p.ry, p.rz, p.px, p.py, p.pz, p.sx, p.sy, p.sz);
		});
		assertFalse(plan.poses().isEmpty(), "nothing animated");
		for (BedrockRuntime.Pose p : plan.poses().values()) {
			for (double v : new double[] {p.rx, p.ry, p.rz, p.px, p.py, p.pz, p.sx, p.sy, p.sz}) {
				assertTrue(Double.isFinite(v), "non-finite bone value");
			}
		}
		assertTrue(plan.poses().values().stream().anyMatch(p -> Math.abs(p.rx) + Math.abs(p.ry) > 1),
				"expected the head/body to turn towards the target");
		// v.dzpjns ("mid-gesture") gates the walk cycle; it must settle back to 0 when no line is playing.
		assertEquals(0, state.variables().get("dzpjns").getAsNumber(), "villager stuck in the gesture state");

		// The addon's "offset" animation drops the root to undo the script's puppet teleport;
		// with the ported lift applied, the model should end up standing on the villager's own feet.
		double rootDropWorldPixels = plan.poses().get("root").py * plan.scale();
		assertEquals(0, rootDropWorldPixels + VillagerPuppetPort.ADULT_LIFT * 16, 1.0,
				"offset animation and puppet lift should cancel out");
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
