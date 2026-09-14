package com.javafied.villagernews.content;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.world.item.Item;

import java.lang.reflect.InvocationTargetException;

/**
 * A head-worn item (hats, moustache, nose) drawn on its wearer with the
 * add-on's own 3D model through GeckoLib's armor renderer.
 *
 * <p>GeckoLib gets the renderer from the item itself, but renderers are
 * client-only classes this (shared) code can't mention - so on the client
 * {@link #create} builds the client subclass that supplies one instead.
 */
public class WearableItem extends Item implements GeoItem {
	private static final String CLIENT_CLASS = "com.javafied.villagernews.client.item.ClientWearableItem";

	/** Created lazily, so GeckoLib only looks for the renderer once the client is initialized. */
	private AnimatableInstanceCache cache;

	public WearableItem(Properties properties) {
		super(properties);
	}

	public static WearableItem create(Properties properties) {
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			try {
				return (WearableItem) Class.forName(CLIENT_CLASS).getConstructor(Properties.class).newInstance(properties);
			} catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
					| InvocationTargetException e) {
				throw new IllegalStateException("Client item class missing", e);
			}
		}
		return new WearableItem(properties);
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
