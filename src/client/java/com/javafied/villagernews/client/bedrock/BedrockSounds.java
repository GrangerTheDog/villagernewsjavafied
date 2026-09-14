package com.javafied.villagernews.client.bedrock;

import com.javafied.villagernews.dialog.BedrockSoundIds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;

/** Client-side playback of the add-on's sounds, following the entity that makes them. */
public final class BedrockSounds {
	private BedrockSounds() {
	}

	/** @return the playing instance, so a line cut short can be stopped */
	public static SoundInstance playFrom(Entity entity, String bedrockEvent) {
		SoundInstance sound = new EntityBoundSoundInstance(BedrockSoundIds.event(bedrockEvent), entity.getSoundSource(),
				1f, 1f, entity, entity.getRandom().nextLong());
		Minecraft.getInstance().getSoundManager().play(sound);
		return sound;
	}
}
