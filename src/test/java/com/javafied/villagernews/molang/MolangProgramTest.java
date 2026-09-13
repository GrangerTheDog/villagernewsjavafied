package com.javafied.villagernews.molang;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.ArrayValue;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MolangProgramTest {
	private static double num(String source) {
		return MolangProgram.of(source).evalNumber(MolangProgram.newScope());
	}

	@Test
	void everyMathFunctionTheAddonUses() {
		assertEquals(2, num("Math.abs(-2)"));
		assertEquals(1, num("Math.floor(1.7)"));
		assertEquals(2, num("Math.round(1.5)"));
		assertEquals(3, num("Math.sqrt(9)"));
		assertEquals(2, num("Math.mod(5, 3)"));
		assertEquals(3, num("Math.clamp(5, 0, 3)"));
		assertEquals(3, num("Math.lerp(1, 5, 0.5)"));
		assertEquals(4, num("Math.random_integer(4, 4)"));
		boolean sawUpperBound = false;
		for (int i = 0; i < 200 && !sawUpperBound; i++) {
			sawUpperBound = num("Math.random_integer(0, 1)") == 1;
		}
		assertTrue(sawUpperBound, "Bedrock's random_integer includes its upper bound");
		double r = num("Math.random(2, 3)");
		assertTrue(r >= 2 && r <= 3, "random out of range: " + r);
		assertEquals(1, num("Math.sin(90)"), 1e-9, "Molang trig is in degrees");
		assertEquals(1, num("Math.cos(0)"), 1e-9);
	}

	@Test
	void picksTheRightVillagerSkin() {
		// Verbatim from the add-on's villager skin render controller.
		MolangProgram texture = MolangProgram.of(
				"v.wycgfr?{return v.ncfcpo?Array.tlpvid[v.szshwk]:Array.ssbolp[v.szshwk];};"
						+ "v.mtbwfe?{return v.ncfcpo?Array.prwiov[v.szshwk]:Array.qeqoqs[v.szshwk];};"
						+ "return v.ncfcpo?Array.iwnjgl[q.skin_id]:Array.akvyuz[q.skin_id];");

		MutableObjectBinding array = new MutableObjectBinding();
		array.set("akvyuz", ArrayValue.of(new Value[] {StringValue.of("textures/a"), StringValue.of("textures/b")}));
		array.set("ssbolp", ArrayValue.of(new Value[] {StringValue.of("textures/special")}));
		MutableObjectBinding variables = new MutableObjectBinding();
		MutableObjectBinding queries = new MutableObjectBinding();
		queries.set("skin_id", Value.of(1));

		Scope scope = MolangProgram.newScope();
		scope.set("array", array);
		scope.set("v", variables);
		scope.set("q", queries);

		assertEquals("textures/b", texture.eval(scope).getAsString());
		queries.set("skin_id", Value.of(3));
		assertEquals("textures/b", texture.eval(scope).getAsString(), "array index wraps");
		variables.set("wycgfr", Value.of(1));
		assertEquals("textures/special", texture.eval(scope).getAsString());
	}

	@Test
	void functionQueriesAndStringComparison() {
		MutableObjectBinding queries = new MutableObjectBinding();
		queries.set("mark_variant", Value.of(5));
		queries.set("any", (Function<Object>) (ctx, args) -> {
			Value subject = args.next().eval();
			for (int i = 1; i < args.length(); i++) {
				if (args.next().eval().getAsNumber() == subject.getAsNumber()) {
					return Value.of(1);
				}
			}
			return Value.nil();
		});
		MutableObjectBinding variables = new MutableObjectBinding();
		variables.set("epwebi", StringValue.of("ufernq"));
		Scope scope = MolangProgram.newScope();
		scope.set("q", queries);
		scope.set("v", variables);

		assertEquals(1, MolangProgram.of("q.any(q.mark_variant, 0, 2, 5)").evalNumber(scope));
		assertEquals(0, MolangProgram.of("q.any(q.mark_variant, 4, 6)").evalNumber(scope));
		assertEquals(0, MolangProgram.of("v.epwebi != 'ufernq'").evalNumber(scope));
	}

	@Test
	void scriptsWriteVariablesAndTemps() {
		MutableObjectBinding variables = new MutableObjectBinding();
		Scope scope = MolangProgram.newScope();
		scope.set("v", variables);
		scope.set("t", new MutableObjectBinding());
		MolangProgram.of("t.a = 40; v.x = t.a + 2; v.y = v.x > 1 ? 1 : 0;").eval(scope);
		assertEquals(42, variables.get("x").getAsNumber());
		assertEquals(1, variables.get("y").getAsNumber());
	}

	private static final Pattern LOOKS_LIKE_MOLANG =
			Pattern.compile("\\b(v|q|t|c|variable|query|temp|context|Math|Array|Texture|Geometry|Material)\\.");

	/** Every Molang string in the real add-on must parse. Skipped when the (gitignored) dev copy is absent. */
	@Test
	void parsesEveryExpressionInTheRealAddon() throws IOException {
		Path rp = Path.of("dev/addon-src/Villager News 1.0 Add-On RP");
		assumeTrue(Files.isDirectory(rp), "no local add-on copy");

		List<String> failures = new ArrayList<>();
		MolangProgram.setErrorReporter(failures::add);
		int checked = 0;
		for (String dir : List.of("entity", "render_controllers", "animation_controllers")) {
			try (Stream<Path> files = Files.walk(rp.resolve(dir))) {
				for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
					List<String> strings = new ArrayList<>();
					collectStrings(JsonParser.parseString(Files.readString(file)), strings);
					for (String source : strings) {
						if (LOOKS_LIKE_MOLANG.matcher(source).find()) {
							checked++;
							MolangProgram.of(source);
						}
					}
				}
			}
		}
		MolangProgram.setErrorReporter(message -> {
		});
		assertTrue(checked > 2000, "expected to find the add-on's Molang, found " + checked);
		assertTrue(failures.isEmpty(), failures.size() + " of " + checked + " failed, e.g.:\n"
				+ String.join("\n", failures.subList(0, Math.min(10, failures.size()))));
	}

	/** Keys are names (array definitions, bone patterns, state names), never Molang. */
	private static void collectStrings(JsonElement element, List<String> out) {
		if (element.isJsonObject()) {
			element.getAsJsonObject().entrySet().forEach(entry -> collectStrings(entry.getValue(), out));
		} else if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(child -> collectStrings(child, out));
		} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			out.add(element.getAsString());
		}
	}
}
