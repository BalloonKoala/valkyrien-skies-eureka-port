package org.valkyrienskies.mod.mixin.feature.world_border;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(ServerLevel.class)
public class MixinLevel {

    @Inject(
        at = @At("RETURN"),
        method = "<init>"
    )
    private void setWorldBorderLevel(final CallbackInfo ci) {
        final ServerLevel self = (ServerLevel) (Object) this;
        ((OfLevel) self.getWorldBorder()).setLevel(self);
        ((OfLevel) self.getRaids()).setLevel(self);
    }
}
