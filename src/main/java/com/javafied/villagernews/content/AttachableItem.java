package com.javafied.villagernews.content;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;

/**
 * A held item the add-on draws in the hand with its own 3D model and
 * animations (its "attachable"): the handbook and the microphone. Everywhere
 * else it shows its icon. Using the handbook opens the guide.
 *
 * <p>As with {@link WearableItem}, the client builds a subclass that supplies
 * GeckoLib's renderer, which shared code can't name.
 */
public class AttachableItem extends Item implements GeoItem {
	private static final String CLIENT_CLASS = "com.javafied.villagernews.client.item.ClientAttachableItem";

	/** Set by the client when it starts: opens the guide. */
	public static volatile Runnable guideOpener = () -> {
	};

	private final boolean guide;
	private AnimatableInstanceCache cache;

	public AttachableItem(Properties properties, boolean guide) {
		super(properties);
		this.guide = guide;
	}

	public static AttachableItem create(Properties properties, boolean guide) {
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			try {
				return (AttachableItem) Class.forName(CLIENT_CLASS).getConstructor(Properties.class, boolean.class)
						.newInstance(properties, guide);
			} catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
					| InvocationTargetException e) {
				throw new IllegalStateException("Client item class missing", e);
			}
		}
		return new AttachableItem(properties, guide);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!guide) {
			return super.use(level, player, hand);
		}
		if (level.isClientSide()) {
			guideOpener.run();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		if (cache == null) {
			cache = GeckoLibUtil.createInstanceCache(this);
		}
		return cache;
	}
}
