package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Vanilla only tracks sections around the camera, so ship sections are tracked separately.
 */
@Mixin(SectionUpdateTracker.class)
public class MixinSectionUpdateTracker {

    @Unique
    private final Long2ObjectMap<SectionUpdateTracker.SectionDirtyState> vs$shipSections = new Long2ObjectOpenHashMap<>();

    @Unique
    private static boolean vs$isShipSection(final int sectionX, final int sectionZ) {
        final ClientLevel level = Minecraft.getInstance().level;
        return level != null && VSGameUtilsKt.isChunkInShipyard(level, sectionX, sectionZ);
    }

    /**
     * This mixin creates render chunks for ship chunks.
     */
    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void preScheduleRebuild(final int x, final int y, final int z, final boolean important,
        final CallbackInfo callbackInfo) {
        if (vs$isShipSection(x, z)) {
            final long section = SectionPos.asLong(x, y, z);
            vs$shipSections.computeIfAbsent(section, k -> new SectionUpdateTracker.SectionDirtyState(true, false, section))
                .setDirty(important);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "getDirtyState", at = @At("HEAD"), cancellable = true)
    private void getShipDirtyState(final long section, final CallbackInfoReturnable<SectionUpdateTracker.SectionDirtyState> cir) {
        if (vs$isShipSection(SectionPos.x(section), SectionPos.z(section))) {
            cir.setReturnValue(vs$shipSections.get(section));
        }
    }

    /**
     * A section normally waits for the chunks around it before it compiles. Ships are islands in the shipyard, the
     * chunks around them never arrive.
     */
    @Inject(method = "hasAllNeighbors", at = @At("HEAD"), cancellable = true)
    private void shipSectionsNeedNoNeighbors(final ClientLevel level, final long section, final CallbackInfoReturnable<Boolean> cir) {
        if (VSGameUtilsKt.isChunkInShipyard(level, SectionPos.x(section), SectionPos.z(section))) {
            cir.setReturnValue(true);
        }
    }
}
