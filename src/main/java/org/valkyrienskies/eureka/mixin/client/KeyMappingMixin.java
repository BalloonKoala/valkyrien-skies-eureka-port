package org.valkyrienskies.eureka.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.wiring.WiringClient;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    // The game calls this once for every key or mouse button that goes down during play, wired to something or not
    @Inject(method = "click", at = @At("HEAD"))
    private static void pressWirelessButtons(final InputConstants.Key key, final CallbackInfo info) {
        WiringClient.onKeyPressed(key.getName());
    }
}
