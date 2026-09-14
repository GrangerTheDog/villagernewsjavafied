package com.javafied.villagernews.mixin.modmenu;

import com.javafied.villagernews.ConvertedPack;
import com.javafied.villagernews.VillagerNewsJavafied;
import com.javafied.villagernews.converter.ConverterCli;

import com.mojang.blaze3d.platform.NativeImage;
import com.terraformersmc.modmenu.util.mod.fabric.FabricIconHandler;
import com.terraformersmc.modmenu.util.mod.fabric.FabricMod;

import net.minecraft.client.renderer.texture.DynamicTexture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod Menu shows this mod with the icon of the player's own converted
 * add-on - its pack icon, which the mod itself never ships. Until the add-on
 * is converted, Mod Menu's default icon stays. Applied only when Mod Menu is
 * installed.
 */
@Pseudo
@Mixin(value = FabricMod.class, remap = false)
abstract class ModMenuIconMixin {
	@Unique
	private static DynamicTexture villagernewsjavafied$icon;
	@Unique
	private static Path villagernewsjavafied$iconFile;

	@Inject(method = "getIcon", at = @At("HEAD"), cancellable = true, require = 0)
	private void villagernewsjavafied$convertedIcon(FabricIconHandler handler, int size, CallbackInfoReturnable<DynamicTexture> cir) {
		if (!VillagerNewsJavafied.MOD_ID.equals(((FabricMod) (Object) this).getId())) {
			return;
		}
		Path file = ConvertedPack.dir().resolve(ConverterCli.PACK_ICON);
		if (!Files.isRegularFile(file)) {
			return;
		}
		if (villagernewsjavafied$icon == null || !file.equals(villagernewsjavafied$iconFile)) {
			try (InputStream in = Files.newInputStream(file)) {
				villagernewsjavafied$icon = new DynamicTexture(() -> VillagerNewsJavafied.MOD_ID + " icon", NativeImage.read(in));
				villagernewsjavafied$iconFile = file;
			} catch (Exception e) {
				VillagerNewsJavafied.LOGGER.debug("Couldn't read the converted add-on's icon", e);
				return;
			}
		}
		cir.setReturnValue(villagernewsjavafied$icon);
	}
}
