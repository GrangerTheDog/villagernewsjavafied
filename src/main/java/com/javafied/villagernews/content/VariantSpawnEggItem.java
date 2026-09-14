package com.javafied.villagernews.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Spawns one of the add-on's characters: the vanilla mob it's drawn over,
 * tagged with the add-on variant and named after it (the add-on names its
 * special villagers when they spawn).
 */
public class VariantSpawnEggItem extends Item {
	private final EntityType<? extends Mob> type;
	private final String variant;

	public VariantSpawnEggItem(EntityType<? extends Mob> type, String variant, Properties properties) {
		super(properties);
		this.type = type;
		this.variant = variant;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
		Mob mob = type.spawn(level, spawned -> {
			spawned.setAttached(ModAttachments.VILLAGER_VARIANT, variant);
			spawned.setCustomName(Component.translatable("entity.villagernewsjavafied." + variant));
		}, pos, EntitySpawnReason.SPAWN_ITEM_USE, true, face == Direction.UP);
		if (mob != null) {
			context.getItemInHand().consume(1, context.getPlayer());
			level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
