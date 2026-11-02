package org.valkyrienskies.mod.mixin.feature.ship_debug_overlay;

import java.util.Map;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.VSDebugScreenEntries;

@Mixin(DebugScreenEntryList.class)
public class MixinDebugScreenEntryList {
    @Shadow
    private Map<Identifier, DebugScreenEntryStatus> allStatuses;

    /**
     * The ship lines were always part of F3, keep them on unless someone turned them off.
     */
    @Inject(method = "rebuildCurrentList", at = @At("HEAD"))
    private void enableShipEntries(final CallbackInfo ci) {
        for (final Identifier id : VSDebugScreenEntries.register()) {
            allStatuses.putIfAbsent(id, DebugScreenEntryStatus.IN_OVERLAY);
        }
    }
}
