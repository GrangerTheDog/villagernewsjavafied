package com.javafied.villagernews.molang;

import java.util.HashMap;
import java.util.Map;

/**
 * State for one evaluation: temp variables and the pending {@code return}.
 * Bedrock shares temps across related expressions (e.g. a render
 * controller's on_fire_color sets {@code t.g} in the "r" channel and the "g"
 * channel reads it back), so callers may reuse one instance across them.
 */
public final class MolangEvaluation {
	final MolangEnvironment env;
	final Map<String, Object> temps = new HashMap<>();
	boolean returned;
	Object returnValue;

	public MolangEvaluation(MolangEnvironment env) {
		this.env = env;
	}

	Object temp(String name) {
		return temps.get(name);
	}
}
