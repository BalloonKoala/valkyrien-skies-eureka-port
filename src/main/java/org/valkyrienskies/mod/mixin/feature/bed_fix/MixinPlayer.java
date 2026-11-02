package org.valkyrienskies.mod.mixin.feature.bed_fix;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ServerPlayer.class)
public abstract class MixinPlayer {
    /**
     * @author Bunting_chj
     * @reason prevent respawning to shipyard when ship is deleted
     */
    @Inject(
        method = "findRespawnAndUseSpawnBlock",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void cancelIfInShipyard(final ServerLevel serverLevel, final ServerPlayer.RespawnConfig respawnConfig,
        final boolean bl, final CallbackInfoReturnable<Optional<ServerPlayer.RespawnPosAngle>> cir) {
        if (cir.getReturnValue().isEmpty()) return;
        final Vec3 pos = cir.getReturnValue().get().position();
        if (VSGameUtilsKt.isBlockInShipyard(serverLevel, pos) && VSGameUtilsKt.getShipManagingPos(serverLevel, pos) == null) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
