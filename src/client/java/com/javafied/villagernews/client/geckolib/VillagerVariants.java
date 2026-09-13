package com.javafied.villagernews.client.geckolib;

import com.javafied.villagernews.content.VillagerVariantKeys;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Which converted model/texture each addon villager reskin uses.
 *
 * <p>Identifiers here were looked up once from a real conversion of the
 * add-on's client entity definitions and render controllers. For "villager"
 * itself the add-on reuses vanilla's full biome/profession texture-array
 * system (Molang-driven, same complexity as real vanilla villager skins) -
 * rather than replicate that now, it just picks one representative texture
 * from that array as a stand-in look. Every other variant here has a single
 * static skin, so those are exact.
 */
public final class VillagerVariants {
	private static final String MOD_ID = "villagernewsjavafied";

	public record Variant(Identifier model, Identifier texture) {
	}

	private static final Identifier COMMON_BODY = model("geometry_oreville_vn_-754165646");

	private static final Map<String, Variant> BY_KEY = Map.of(
			"villager", new Variant(COMMON_BODY, texture("dmk")),
			"ghibss", new Variant(COMMON_BODY, texture("dio")),
			"txczvv", new Variant(COMMON_BODY, texture("diy")),
			"vwpagn", new Variant(COMMON_BODY, texture("dja")),
			"poztxf", new Variant(COMMON_BODY, texture("dil")),
			"xcrjxf", new Variant(COMMON_BODY, texture("dja")),
			"ilvfra", new Variant(model("geometry_oreville_vn_-1377987598"), texture("dil"))
	);

	private VillagerVariants() {
	}

	public static Variant get(String key) {
		return BY_KEY.getOrDefault(key, BY_KEY.get(VillagerVariantKeys.DEFAULT));
	}

	private static Identifier model(String name) {
		return Identifier.fromNamespaceAndPath(MOD_ID, "entity/" + name);
	}

	private static Identifier texture(String name) {
		return Identifier.fromNamespaceAndPath(MOD_ID, "textures/oreville/vn/" + name + ".png");
	}
}
