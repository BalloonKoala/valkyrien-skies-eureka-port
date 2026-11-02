package org.valkyrienskies.mod.mixin.feature.fix_render_chunk_sorting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SectionTaskDynamicQueue.class)
public class MixinRenderChunk {

    /**
     * This mixin fixes chunk render sorting. Vanilla MC behavior is to compile the chunks closest to the player first,
     * however ship chunks are extremely far away, so they always get compiled last.
     *
     * <p>By wrapping here we fix the calculation that determines the distance between the player and a chunk, which
     * makes the ship chunks compile in the correct order.
     */
    @WrapOperation(
        method = "poll",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D")
    )
    private double preGetSquaredCameraDistance(final BlockPos origin, final net.minecraft.core.Position camera,
        final Operation<Double> original) {
        final ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return original.call(origin, camera);
        }

        final ClientShip shipObject = VSGameUtilsKt.getLoadedShipManagingPos(world, origin);
        if (shipObject != null) {
            final Vector3dc chunkPosInWorld = shipObject.getRenderTransform().getShipToWorldMatrix().transformPosition(
                new Vector3d(origin.getX() + 8.0, origin.getY() + 8.0, origin.getZ() + 8.0)
            );
            return chunkPosInWorld.distanceSquared(camera.x(), camera.y(), camera.z());
        }
        return original.call(origin, camera);
    }
}
