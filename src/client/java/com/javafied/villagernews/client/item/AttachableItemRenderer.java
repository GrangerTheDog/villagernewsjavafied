package com.javafied.villagernews.client.item;

import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.client.bedrock.BedrockGeoModel;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.Layer;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.Pose;
import com.javafied.villagernews.client.bedrock.BedrockRuntime.RenderPlan;
import com.javafied.villagernews.client.bedrock.BedrockRuntime;
import com.javafied.villagernews.content.AttachableItem;
import com.javafied.villagernews.names.AddonNames;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;

import java.io.Reader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a held item from the add-on's attachable, animated by its own
 * first/third-person animations (a bobbing microphone, a handbook with a
 * swinging ribbon).
 *
 * <p>Bedrock binds an attachable's bone to the holder's hand bone; Java puts
 * a held item in a frame of its own at the hand. {@link #adjustRenderPose}
 * turns the one into the other, so the add-on's poses apply unchanged: in
 * third person from Java's hand frame to Bedrock's hand bone; in first
 * person through Bedrock's own first-person arm pose (its
 * {@code first_person.empty_hand} animation), from the camera. One Bedrock
 * quirk is kept too: a bound bone at the top of its model sits 24 pixels
 * lower than it says (the add-on's poses make up for it).
 *
 * <p>Positions below are GeckoLib model space: pixels, x mirrored from
 * Bedrock's (the right hand is +x), facing -z.
 */
final class AttachableItemRenderer extends GeoItemRenderer<AttachableItem> {
	private static final DataTicket<ItemDisplayContext> PERSPECTIVE =
			DataTicket.create("villagernewsjavafied_attachable_perspective", ItemDisplayContext.class);
	/** Where Bedrock's hand bone is, from Java's third-person held-item frame (in blocks). */
	private static final float HAND_Y = -3 / 16f;
	private static final float HAND_Z = 3 / 16f;
	/** Bedrock's quirk for bound top-level bones. */
	private static final float BOUND_ROOT_DROP = -24;
	/** Java's first-person held-item offset from the camera (ItemInHandRenderer, right hand). */
	private static final float JAVA_FIRST_PERSON_X = 0.56f;
	private static final float JAVA_FIRST_PERSON_Y = -0.52f;
	private static final float JAVA_FIRST_PERSON_Z = -0.72f;
	private static final float EYE_HEIGHT = 1.62f;
	// Bedrock's first-person right arm: its pose, its pivot and its hand bone (pivot, plus the pose's own offset).
	private static final float[] ARM_POSITION = {-13.5f, -10, 12};
	private static final float[] ARM_ROTATION = {95, -45, 115};
	private static final float[] ARM_PIVOT = {5, 22, 0};
	private static final float[] HAND = {6, 15, 0};

	private static final Map<Identifier, Set<String>> BOUND_ROOTS = new ConcurrentHashMap<>();

	AttachableItemRenderer(AttachableItem item) {
		super(new Model(AddonNames.item(BuiltInRegistries.ITEM.getKey(item).getPath())));
	}

	@Override
	public void captureDefaultRenderState(AttachableItem item, RenderData data, GeoRenderState state, float partialTick) {
		super.captureDefaultRenderState(item, data, state, partialTick);
		ItemDisplayContext perspective = data.renderPerspective();
		state.addGeckolibData(PERSPECTIVE, perspective);
		LivingEntity holder = data.itemOwner() == null ? null : data.itemOwner().asLivingEntity();
		if (holder == null) {
			holder = Minecraft.getInstance().player;
		}
		if (holder == null) {
			return;
		}
		HumanoidArm mainArm = holder instanceof Player player ? player.getMainArm() : HumanoidArm.RIGHT;
		boolean mainHand = perspective.leftHand() == (mainArm == HumanoidArm.LEFT);
		RenderPlan plan = BedrockRuntime.evaluateAttachable(holder, ((Model) getGeoModel()).attachable, perspective.firstPerson(),
				mainHand, partialTick);
		if (plan != null) {
			state.addGeckolibData(BedrockGeoModel.PLAN, plan);
		}
	}

	/** In place of GeckoLib's centring: from Java's held-item frame to Bedrock's hand bone. */
	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> pass) {
		PoseStack pose = pass.poseStack();
		pose.translate(0.5f, 0.5f, 0.5f); // undo the item renderer's centring
		ItemDisplayContext perspective = pass.getGeckolibData(PERSPECTIVE);
		if (perspective == null || !perspective.firstPerson()) {
			pose.translate(0, HAND_Y, HAND_Z);
			pose.mulPose(Axis.XP.rotationDegrees(90));
			return;
		}
		// The left hand mirrors the right (without flipping the model, which would turn its faces inside out).
		float side = perspective.leftHand() ? -1 : 1;
		pose.translate(-side * JAVA_FIRST_PERSON_X, -JAVA_FIRST_PERSON_Y, -JAVA_FIRST_PERSON_Z); // to the camera
		pose.mulPose(Axis.YP.rotationDegrees(180)); // Bedrock's first-person rig faces the camera's other way
		pose.translate(0, -EYE_HEIGHT, 0); // to the holder's feet
		pose.translate(side * ARM_POSITION[0] / 16, ARM_POSITION[1] / 16, ARM_POSITION[2] / 16);
		pose.translate(side * ARM_PIVOT[0] / 16, ARM_PIVOT[1] / 16, ARM_PIVOT[2] / 16);
		// Bedrock's rotations, as GeckoLib applies them: z, then y, then x, with x and y negated.
		pose.mulPose(Axis.ZP.rotationDegrees(side * ARM_ROTATION[2]));
		pose.mulPose(Axis.YP.rotationDegrees(-side * ARM_ROTATION[1]));
		pose.mulPose(Axis.XP.rotationDegrees(-ARM_ROTATION[0]));
		pose.translate(-side * ARM_PIVOT[0] / 16, -ARM_PIVOT[1] / 16, -ARM_PIVOT[2] / 16);
		pose.translate(side * HAND[0] / 16, HAND[1] / 16, HAND[2] / 16);
	}

	/** The add-on's poses, as {@code BedrockEntityRenderer} applies them. */
	@Override
	public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> pass, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(pass, snapshots);
		RenderPlan plan = pass.getGeckolibData(BedrockGeoModel.PLAN);
		Set<String> boundRoots = boundRoots(getGeoModel().getModelResource(pass.renderState()));
		for (String bone : pass.model().boneLookup().get().keySet()) {
			String key = bone.toLowerCase(Locale.ROOT);
			Pose pose = plan == null ? null : plan.poses().get(key);
			boolean hidden = plan != null && !plan.isBoneVisible(bone);
			boolean bound = boundRoots.contains(key);
			if (pose == null && !hidden && !bound) {
				continue;
			}
			snapshots.ifPresent(bone, snapshot -> {
				if (pose != null) {
					snapshot.setRotation((float) -Math.toRadians(pose.rx), (float) -Math.toRadians(pose.ry), (float) Math.toRadians(pose.rz));
					snapshot.setScale((float) pose.sx, (float) pose.sy, (float) pose.sz);
				}
				snapshot.setTranslation(pose == null ? 0 : (float) pose.px,
						(pose == null ? 0 : (float) pose.py) + (bound ? BOUND_ROOT_DROP : 0), pose == null ? 0 : (float) pose.pz);
				if (hidden) {
					snapshot.skipRender(true);
				}
			});
		}
	}

	/** The model's top-level bones bound to a bone of the holder (the geometry's {@code binding}). */
	private static Set<String> boundRoots(Identifier model) {
		return BOUND_ROOTS.computeIfAbsent(model, id -> {
			Set<String> bound = new HashSet<>();
			Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "geckolib/models/" + id.getPath() + ".geo.json");
			try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(file)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				for (JsonElement geometry : root.getAsJsonArray("minecraft:geometry")) {
					for (JsonElement bone : geometry.getAsJsonObject().getAsJsonArray("bones")) {
						JsonObject b = bone.getAsJsonObject();
						if (!b.has("parent") && b.has("binding")) {
							bound.add(b.get("name").getAsString().toLowerCase(Locale.ROOT));
						}
					}
				}
			} catch (Exception e) {
				VillagerNewsJavafied.LOGGER.debug("No geometry file for {}", id, e);
			}
			return Set.copyOf(bound);
		});
	}

	private static final class Model extends GeoModel<AttachableItem> {
		private static final Identifier NOT_CONVERTED = VillagerNewsJavafied.id("item/not_converted");
		private final String attachable;

		Model(String attachable) {
			this.attachable = attachable;
		}

		private Layer layer(GeoRenderState state) {
			RenderPlan plan = state.getGeckolibData(BedrockGeoModel.PLAN);
			return plan != null && !plan.layers().isEmpty() ? plan.layers().getFirst() : BedrockRuntime.attachableBaseLayer(attachable);
		}

		@Override
		public Identifier getModelResource(GeoRenderState state) {
			Layer layer = layer(state);
			return layer == null ? NOT_CONVERTED : layer.model();
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			Layer layer = layer(state);
			return layer == null ? NOT_CONVERTED : layer.texture();
		}

		@Override
		public Identifier getAnimationResource(AttachableItem animatable) {
			return NOT_CONVERTED;
		}

		@Override
		public BakedGeoModel getBakedModel(Identifier location) {
			if (location.equals(NOT_CONVERTED)) {
				return BakedModelCache.MISSINGNO.get();
			}
			try {
				return super.getBakedModel(location);
			} catch (RuntimeException e) {
				return BakedModelCache.MISSINGNO.get();
			}
		}
	}
}
