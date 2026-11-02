package org.valkyrienskies.mod.mixin.client.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerPick {

    /**
     * {@link Entity#pick(double, float, boolean)} except the hit pos is not transformed
     */
    @Unique
    private static HitResult entityRaycastNoTransform(
        final Entity entity, final double maxDistance, final float tickDelta, final boolean includeFluids) {
        final Vec3 vec3d = entity.getEyePosition(tickDelta);
        final Vec3 vec3d2 = entity.getViewVector(tickDelta);
        final Vec3 vec3d3 = vec3d.add(vec3d2.x * maxDistance, vec3d2.y * maxDistance, vec3d2.z * maxDistance);
        return RaycastUtilsKt.clipIncludeShips(
            entity.level(),
            new ClipContext(
                vec3d,
                vec3d3,
                ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                entity
            ),
            false
        );
    }

    @WrapOperation(
        method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
        )
    )
    private static HitResult modifyCrosshairTargetBlocks(final Entity receiver, final double maxDistance,
        final float tickDelta, final boolean includeFluids, final Operation<HitResult> pick) {

        final HitResult original = entityRaycastNoTransform(receiver, maxDistance, tickDelta, includeFluids);
        ((MinecraftDuck) Minecraft.getInstance()).vs$setOriginalCrosshairTarget(original);

        return pick.call(receiver, maxDistance, tickDelta, includeFluids);
    }

    /**
     * The hit location of an entity on a ship is in the shipyard. Without this it always counts as out of reach, so
     * nothing that lives on a ship can be looked at, clicked or broken.
     */
    @WrapOperation(
        method = "filterHitResult",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private static boolean correctReachCheck(final Vec3 instance, final Position position, final double range,
        final Operation<Boolean> original) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(Minecraft.getInstance().level, instance.x, instance.y,
            instance.z, position.x(), position.y(), position.z()) < range * range;
    }

    @WrapOperation(
        method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private static double correctDistanceChecks(final Vec3 instance, final Vec3 vec3, final Operation<Double> original) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(Minecraft.getInstance().level, instance, vec3, original);
    }
}
