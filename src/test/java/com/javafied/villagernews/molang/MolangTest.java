package com.javafied.villagernews.molang;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MolangTest {
	static final class MapEnv implements MolangEnvironment {
		final Map<String, Object> vars = new HashMap<>();
		final Map<String, Object> queries = new HashMap<>();
		final Map<String, String> textures = new HashMap<>();
		final Map<String, List<String>> arrays = new HashMap<>();

		@Override
		public Object variable(String name) {
			return vars.get(name);
		}

		@Override
		public void setVariable(String name, Object value) {
			vars.put(name, value);
		}

		@Override
		public Object query(String name, List<Object> args) {
			return queries.get(name);
		}

		@Override
		public Object resource(String kind, String name) {
			return kind.equals("texture") ? textures.get(name) : null;
		}

		@Override
		public String arrayElement(String name, int index) {
			List<String> list = arrays.get(name);
			return list == null ? null : list.get(Math.floorMod(index, list.size()));
		}
	}

	private static double num(String src, MolangEnvironment env) {
		return Molang.num(Molang.parse(src).eval(new MolangEvaluation(env)));
	}

	@Test
	void arithmeticAndPrecedence() {
		MapEnv env = new MapEnv();
		assertEquals(7, num("1 + 2 * 3", env));
		assertEquals(9, num("(1 + 2) * 3", env));
		assertEquals(1, num("2 > 1 && 3 >= 3", env));
		assertEquals(0, num("!1", env));
		assertEquals(-4, num("-2 * 2", env));
		assertEquals(0.5, num("1 / 2", env));
		assertEquals(0, num("1 / 0", env), "Molang division by zero yields 0, not infinity");
	}

	@Test
	void ternariesNestRightAssociatively() {
		MapEnv env = new MapEnv();
		env.vars.put("a", 0.0);
		env.vars.put("b", 1.0);
		assertEquals(2, num("v.a ? 1 : v.b ? 2 : 3", env));
	}

	@Test
	void undefinedVariablesAreZeroButVisibleToNullCoalescing() {
		MapEnv env = new MapEnv();
		assertEquals(0, num("v.missing", env));
		assertEquals(5, num("v.missing ?? 5", env));
		env.vars.put("present", 0.0);
		assertEquals(0, num("v.present ?? 5", env), "?? only replaces unset, not 0");
	}

	@Test
	void statementsAssignmentAndReturn() {
		MapEnv env = new MapEnv();
		assertEquals(0, num("v.x = 3; v.y = v.x * 2;", env), "complex expression without return is 0");
		assertEquals(3.0, env.vars.get("x"));
		assertEquals(6.0, env.vars.get("y"));
		assertEquals(42, num("t.a = 40; t.a > 1 ? { return t.a + 2; }; return 0;", env));
	}

	@Test
	void realVillagerSkinSelection() {
		// Lifted verbatim from the add-on's villager skin render controller.
		String texture = "v.wycgfr?{return v.ncfcpo?Array.tlpvid[v.szshwk]:Array.ssbolp[v.szshwk];};"
				+ "v.mtbwfe?{return v.ncfcpo?Array.prwiov[v.szshwk]:Array.qeqoqs[v.szshwk];};"
				+ "return v.ncfcpo?Array.iwnjgl[q.skin_id]:Array.akvyuz[q.skin_id];";
		MapEnv env = new MapEnv();
		env.arrays.put("akvyuz", List.of("Texture.adult_a", "Texture.adult_b"));
		env.arrays.put("ssbolp", List.of("Texture.special"));
		env.textures.put("adult_a", "textures/a");
		env.textures.put("adult_b", "textures/b");
		env.textures.put("special", "textures/special");

		env.queries.put("skin_id", 1.0);
		assertEquals("textures/b", Molang.parse(texture).eval(new MolangEvaluation(env)));

		env.queries.put("skin_id", 3.0);
		assertEquals("textures/b", Molang.parse(texture).eval(new MolangEvaluation(env)), "array index wraps");

		env.vars.put("wycgfr", 1.0);
		assertEquals("textures/special", Molang.parse(texture).eval(new MolangEvaluation(env)));
	}

	@Test
	void stringsAndQueryHelpers() {
		MapEnv env = new MapEnv();
		env.vars.put("epwebi", "ufernq");
		assertEquals(0, num("v.epwebi != 'ufernq'", env));
		env.queries.put("mark_variant", 5.0);
		assertEquals(1, num("q.any(q.mark_variant, 0, 2, 5)", env));
		assertEquals(0, num("q.any(q.mark_variant, 4, 6)", env));
	}

	@Test
	void tempsCarryAcrossRelatedExpressions() {
		MapEnv env = new MapEnv();
		MolangEvaluation shared = new MolangEvaluation(env);
		Molang.eval("t.r = 1; t.g = 0.4; return t.r;", shared);
		assertEquals(0.4, Molang.num(Molang.eval("t.g", shared)));
	}

	@Test
	void trigIsInDegrees() {
		assertEquals(1, num("Math.sin(90)", new MapEnv()), 1e-9);
		assertEquals(3, num("Math.lerp(1, 5, 0.5)", new MapEnv()));
	}

	@Test
	void unresolvedValuesBecomeZeroAtTheTopLevel() {
		// Like Bedrock: "unset" is only observable inside an expression (via ??).
		assertEquals(0.0, Molang.parse("Texture.nope").eval(new MolangEvaluation(new MapEnv())));
	}

	private static final Pattern LOOKS_LIKE_MOLANG =
			Pattern.compile("\\b(v|q|t|c|variable|query|temp|context|Math|Array|Texture|Geometry|Material)\\.");

	/** Every Molang string in the real add-on must parse. Skipped when the (gitignored) dev copy is absent. */
	@Test
	void parsesEveryExpressionInTheRealAddon() throws IOException {
		Path rp = Path.of("dev/addon-src/Villager News 1.0 Add-On RP");
		assumeTrue(Files.isDirectory(rp), "no local add-on copy");

		List<String> sources = new ArrayList<>();
		for (String dir : List.of("entity", "render_controllers", "animation_controllers")) {
			try (Stream<Path> files = Files.walk(rp.resolve(dir))) {
				for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
					collectStrings(JsonParser.parseString(Files.readString(file)), sources);
				}
			}
		}
		List<String> failures = new ArrayList<>();
		int checked = 0;
		for (String source : sources) {
			if (!LOOKS_LIKE_MOLANG.matcher(source).find()) {
				continue;
			}
			checked++;
			try {
				Molang.parse(source);
			} catch (MolangException e) {
				failures.add(e.getMessage());
			}
		}
		assertTrue(checked > 1000, "expected to find the add-on's Molang, found " + checked);
		assertTrue(failures.isEmpty(), failures.size() + " of " + checked + " failed, e.g.:\n"
				+ String.join("\n", failures.subList(0, Math.min(10, failures.size()))));
	}

	private static void collectStrings(JsonElement element, List<String> out) {
		// Keys are names (array definitions, bone patterns, state names), never Molang.
		if (element.isJsonObject()) {
			element.getAsJsonObject().entrySet().forEach(entry -> collectStrings(entry.getValue(), out));
		} else if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(child -> collectStrings(child, out));
		} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			out.add(element.getAsString());
		}
	}
}
