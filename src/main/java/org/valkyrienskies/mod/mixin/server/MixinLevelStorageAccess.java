package org.valkyrienskies.mod.mixin.server;

import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.util.PaperWorldImport;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
public class MixinLevelStorageAccess {

    // The first thing both the server and the client do with a world, before anything reads the files we care about
    @Inject(method = "getUnfixedDataTag", at = @At("HEAD"))
    private void importPaperWorld(final boolean useFallback, final CallbackInfoReturnable<?> cir) {
        PaperWorldImport.importIfNeeded(
            LevelStorageSource.LevelStorageAccess.class.cast(this).getLevelPath(LevelResource.ROOT));
    }
}
