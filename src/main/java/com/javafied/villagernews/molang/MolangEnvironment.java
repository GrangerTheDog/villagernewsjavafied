package com.javafied.villagernews.molang;

import java.util.List;

/**
 * Everything a Molang expression can read or write that isn't local to the
 * expression itself: entity variables ({@code v.}), queries ({@code q.}),
 * context ({@code c.}) and the resource tables a client entity / render
 * controller defines ({@code Texture.}, {@code Geometry.}, {@code Material.},
 * {@code Array.}). Temp variables ({@code t.}) live on {@link MolangEvaluation}.
 *
 * <p>Values are {@link Double}, {@link String}, or {@code null} for "not set"
 * (which only {@code ??} can tell apart from 0).
 */
public interface MolangEnvironment {
	Object variable(String name);

	void setVariable(String name, Object value);

	Object query(String name, List<Object> args);

	default Object context(String name) {
		return null;
	}

	/** {@code Texture.x}, {@code Geometry.x}, {@code Material.x}; kind is lower-case. */
	default Object resource(String kind, String name) {
		return null;
	}

	/** {@code Array.x[i]}: the raw (unevaluated) Molang source of element {@code i}, or null. */
	default String arrayElement(String name, int index) {
		return null;
	}
}
