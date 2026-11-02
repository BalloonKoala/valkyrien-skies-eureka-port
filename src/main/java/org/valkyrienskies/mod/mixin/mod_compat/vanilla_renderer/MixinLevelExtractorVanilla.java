package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.render.LevelRendererVanillaDuck;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

@Mixin(value = LevelExtractor.class, priority = 999)
public abstract class MixinLevelExtractorVanilla {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelRenderer levelRenderer;

    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    private @Nullable SectionUpdateTracker sectionUpdateTracker;

    @Unique
    private Long lastMountedShipId = null;
    @Unique
    private ShipTransform lastTransform = null;

    /**
     * Prevents ships from disappearing on f3+a
     */
    @Inject(
        method = "allChanged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;repositionCamera(Lnet/minecraft/core/SectionPos;)V"
        )
    )
    private void afterRefresh(final CallbackInfo ci) {
        // This can happen when immersive portals is installed
        if (!(this.level.getChunkSource() instanceof final ClientChunkCacheDuck chunks)) return;

        chunks.vs$getShipChunks().forEach((pos, chunk) -> {
            for (int y = level.getMinSectionY(); y <= level.getMaxSectionY(); y++) {
                sectionUpdateTracker.setDirty(ChunkPos.getX(pos), y, ChunkPos.getZ(pos), false);
            }
        });
    }

    /**
     * Force frustum update if the ship moves and the camera doesn't
     */
    @ModifyExpressionValue(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"
        )
    )
    private boolean getNeedsFrustumUpdate(final boolean needsFrustumUpdate) {
        // force frustum update if default behaviour says to OR if the player is mounted to a ship
        final Player player = this.minecraft.player;
        if (player == null || !(VSGameUtilsKt.getShipMountedTo(player) instanceof final ClientShip ship)) {
            this.lastMountedShipId = null;
            return needsFrustumUpdate;
        }
        final ShipTransform transform = ship.getRenderTransform();
        if (this.lastMountedShipId == null || this.lastMountedShipId.longValue() != ship.getId() || this.lastTransform == null) {
            this.lastMountedShipId = ship.getId();
            this.lastTransform = transform;
            return true;
        }
        final boolean needUpdate = this.lastTransform != transform && !this.lastTransform.getShipToWorld().equals(transform.getShipToWorld());
        this.lastTransform = transform;
        return needUpdate;
    }

    /**
     * Add ship render chunks to visibleSections, right before vanilla goes through them to compile what is dirty
     */
    @Inject(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections()Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            ordinal = 0
        )
    )
    private void updateShipSections(final DeltaTracker deltaTracker, final Camera camera, final float partialTick,
        final CallbackInfo ci) {
        ((LevelRendererVanillaDuck) levelRenderer).vs$addShipVisibleChunks(camera.getCullFrustum());
    }
}
