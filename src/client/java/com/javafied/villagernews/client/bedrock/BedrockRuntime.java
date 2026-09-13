package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.BonePattern;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.ClientEntity;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.ControllerRef;
import com.javafied.villagernews.client.bedrock.BedrockDefinitions.RenderController;
import com.javafied.villagernews.converter.ConverterUtil;
import com.javafied.villagernews.molang.MolangProgram;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.ArrayValue;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runs an add-on client entity's own logic for a Java entity, once per frame:
 * {@code initialize} scripts the first time, {@code pre_animation} scripts
 * every frame, then each render controller - producing a {@link RenderPlan}
 * (which geometry, which textures in which order, which bones hidden).
 * Entity variables ({@code v.*}) persist per entity between frames, like in
 * Bedrock.
 */
public final class BedrockRuntime {
	/** One textured pass over the model: Bedrock renders a render controller once per texture it lists. */
	public record Layer(Identifier model, Identifier texture, BedrockMaterials.Kind kind) {
	}

	public record BoneVisibility(BonePattern pattern, boolean visible) {
	}

	public record RenderPlan(List<Layer> layers, List<BoneVisibility> boneVisibility, float scale) {
		public boolean isBoneVisible(String bone) {
			boolean visible = true;
			for (BoneVisibility rule : boneVisibility) {
				if (rule.pattern().matches(bone)) {
					visible = rule.visible();
				}
			}
			return visible;
		}
	}

	private static final class EntityState {
		final MutableObjectBinding variables = new MutableObjectBinding();
		String clientEntity;
	}

	// Render-thread only. Weak keys: state goes away with the entity.
	private static final Map<Entity, EntityState> STATES = new WeakHashMap<>();

	private BedrockRuntime() {
	}

	/** Null if the add-on hasn't been converted (or doesn't define this client entity). */
	public static RenderPlan evaluate(Entity entity, String clientEntityId, float partialTick) {
		BedrockDefinitions.Snapshot defs = BedrockDefinitions.get();
		ClientEntity ce = defs.clientEntity(clientEntityId);
		if (ce == null) {
			return null;
		}

		EntityState state = STATES.computeIfAbsent(entity, e -> new EntityState());
		boolean firstFrame = !ce.identifier().equals(state.clientEntity);
		state.clientEntity = ce.identifier();
		return plan(defs, ce, state.variables, firstFrame,
				new EntityQueries(entity, partialTick, defs.properties(ce.identifier())));
	}

	/**
	 * The entity-independent core: runs the client entity's scripts against
	 * {@code variables} (its persistent {@code v.*}) and {@code queries}, then
	 * its render controllers.
	 */
	public static RenderPlan plan(BedrockDefinitions.Snapshot defs, ClientEntity ce, MutableObjectBinding variables,
			boolean runInitialize, ObjectValue queries) {
		Scope scope = MolangProgram.newScope();
		scope.set("variable", variables);
		scope.set("v", variables);
		scope.set("query", queries);
		scope.set("q", queries);
		MutableObjectBinding temps = new MutableObjectBinding();
		scope.set("temp", temps);
		scope.set("t", temps);
		scope.set("texture", table(ce.textures()));
		scope.set("geometry", table(ce.geometry()));
		scope.set("material", table(ce.materials()));

		if (runInitialize) {
			ce.initialize().forEach(script -> MolangProgram.of(script).eval(scope));
		}
		ce.preAnimation().forEach(script -> MolangProgram.of(script).eval(scope));

		List<Layer> layers = new ArrayList<>();
		List<BoneVisibility> boneVisibility = new ArrayList<>();
		for (ControllerRef ref : ce.renderControllers()) {
			if (ref.condition() != null && !MolangProgram.of(ref.condition()).evalBoolean(scope)) {
				continue;
			}
			RenderController rc = defs.renderController(ref.name());
			if (rc == null) {
				continue;
			}
			scope.set("array", new Arrays(rc.arrays(), scope));

			boolean layerVisible = true;
			for (BonePattern pattern : rc.partVisibility()) {
				boolean visible = MolangProgram.of(pattern.molang()).evalBoolean(scope);
				if (pattern.pattern().equals("*")) {
					layerVisible = visible;
				} else {
					boneVisibility.add(new BoneVisibility(pattern, visible));
				}
			}
			BedrockMaterials.Kind kind = defs.materials().classify(material(rc, scope));
			if (!layerVisible || kind == BedrockMaterials.Kind.HIDDEN) {
				continue;
			}

			Identifier model = modelId(MolangProgram.of(rc.geometry()).eval(scope).getAsString());
			for (String textureExpression : rc.textures()) {
				String texture = MolangProgram.of(textureExpression).eval(scope).getAsString();
				if (!texture.isEmpty()) {
					layers.add(new Layer(model, textureId(texture), kind));
				}
			}
		}

		double scale = MolangProgram.of(ce.scale()).evalNumber(scope);
		return new RenderPlan(List.copyOf(layers), List.copyOf(boneVisibility), scale > 0 ? (float) scale : 1f);
	}

	/**
	 * The material a render controller asks for. Bedrock allows one per bone
	 * pattern; Java draws a whole pass with one render type, so the catch-all
	 * {@code "*"} entry (or the first one) decides.
	 */
	private static String material(RenderController rc, Scope scope) {
		BonePattern chosen = null;
		for (BonePattern pattern : rc.materials()) {
			if (chosen == null || pattern.pattern().equals("*")) {
				chosen = pattern;
			}
		}
		return chosen == null ? null : MolangProgram.of(chosen.molang()).eval(scope).getAsString();
	}

	/** "geometry.oreville_vn.-754165646" -> the GeckoLib model the converter produced for it. */
	private static Identifier modelId(String geometry) {
		return VillagerNewsJavafied.id("entity/" + ConverterUtil.slug(geometry));
	}

	/** "textures/oreville/vn/dil" -> assets/&lt;modid&gt;/textures/oreville/vn/dil.png */
	private static Identifier textureId(String bedrockPath) {
		return VillagerNewsJavafied.id(bedrockPath.toLowerCase(Locale.ROOT) + ".png");
	}

	/** {@code Texture.x} / {@code Geometry.x} / {@code Material.x}: the client entity's name -> value tables. */
	private static ObjectValue table(Map<String, String> entries) {
		return name -> {
			String value = entries.get(name.toLowerCase(Locale.ROOT));
			return value == null ? null : ObjectProperty.property(StringValue.of(value), true);
		};
	}

	/** {@code Array.x}: a render controller's arrays, whose elements are themselves Molang (e.g. "Texture.x"). */
	private record Arrays(Map<String, List<String>> arrays, Scope scope) implements ObjectValue {
		@Override
		public ObjectProperty getProperty(String name) {
			List<String> elements = arrays.get(name.toLowerCase(Locale.ROOT));
			if (elements == null || elements.isEmpty()) {
				return null;
			}
			Value[] values = new Value[elements.size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = MolangProgram.of(elements.get(i)).eval(scope);
			}
			return ObjectProperty.property(ArrayValue.of(values), true);
		}
	}
}
