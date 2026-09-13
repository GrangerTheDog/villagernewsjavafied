package com.javafied.villagernews.molang;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Entry point for evaluating Bedrock Molang. Sources are compiled once and
 * cached; a source that fails to parse is reported once to
 * {@link #setErrorReporter} and then behaves as the constant 0, the same way
 * Bedrock treats an expression it can't make sense of.
 */
public final class Molang {
	private static final Map<String, MolangNode> CACHE = new ConcurrentHashMap<>();
	private static final MolangNode ZERO = new MolangNode.Program(java.util.List.of(new MolangNode.Num(0)), false);
	private static volatile Consumer<String> errorReporter = message -> {
	};

	private Molang() {
	}

	public static void setErrorReporter(Consumer<String> reporter) {
		errorReporter = reporter;
	}

	/** Parses without caching, throwing on bad input; for tests and diagnostics. */
	public static MolangNode parse(String source) {
		return MolangParser.parse(source);
	}

	public static MolangNode compile(String source) {
		return CACHE.computeIfAbsent(source, s -> {
			try {
				return MolangParser.parse(s);
			} catch (MolangException e) {
				errorReporter.accept(e.getMessage());
				return ZERO;
			}
		});
	}

	public static Object eval(String source, MolangEnvironment env) {
		return compile(source).eval(new MolangEvaluation(env));
	}

	public static Object eval(String source, MolangEvaluation evaluation) {
		evaluation.returned = false;
		evaluation.returnValue = null;
		return compile(source).eval(evaluation);
	}

	public static double evalNumber(String source, MolangEnvironment env) {
		return num(eval(source, env));
	}

	public static boolean evalBoolean(String source, MolangEnvironment env) {
		return truthy(eval(source, env));
	}

	public static double num(Object value) {
		return value instanceof Double d ? d : 0.0;
	}

	public static boolean truthy(Object value) {
		if (value instanceof Double d) {
			return d != 0;
		}
		return value instanceof String s && !s.isEmpty();
	}

	static boolean equal(Object a, Object b) {
		if (a instanceof String || b instanceof String) {
			return a != null && a.equals(b);
		}
		return num(a) == num(b);
	}
}
