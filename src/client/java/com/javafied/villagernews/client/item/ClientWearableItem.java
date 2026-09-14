package com.javafied.villagernews.client.item;

import com.javafied.villagernews.content.WearableItem;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoArmorRenderer;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** {@link WearableItem} as the client builds it: with its armor renderer. */
public class ClientWearableItem extends WearableItem {
	public ClientWearableItem(Properties properties) {
		super(properties);
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(new GeoRenderProvider() {
			private WornItemRenderer renderer;

			@Override
			public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
				if (renderer == null) {
					renderer = new WornItemRenderer(ClientWearableItem.this);
				}
				return renderer;
			}
		});
	}
}
