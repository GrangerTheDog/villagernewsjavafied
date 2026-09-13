package com.javafied.villagernews.content;

import java.util.List;

/**
 * The addon's villager-family reskins that share vanilla's real Villager
 * entity (confirmed by identical component-group fingerprints in the
 * add-on's behavior pack, aside from "villager" and "txczvv" which fingerprint
 * identically to each other too - Bedrock's "Wandering Trader" here is itself
 * just a Villager reskin, not the distinct wandering_trader mob).
 *
 * <p>Hand-picked for this vertical slice; a full manifest-driven registry
 * (matching however many entities a given addon actually has) is follow-up
 * work - see the plan.
 */
public final class VillagerVariantKeys {
	public static final String DEFAULT = "villager";

	public static final List<String> ALL = List.of(
			"villager",
			"ghibss",
			"txczvv",
			"vwpagn",
			"poztxf",
			"xcrjxf",
			"ilvfra"
	);

	private VillagerVariantKeys() {
	}

	public static boolean isValid(String key) {
		return ALL.contains(key);
	}
}
