package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(MoveControl.class)
public class MixinMoveControl {

    @Unique
    private static final double FEET_MARGIN = 0.02;

    @Shadow
    @Final
    protected Mob mob;

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape vs$insertShipCollisions(BlockState instance, BlockGetter blockGetter, BlockPos blockPos,
        Operation<VoxelShape> original) {
        VoxelShape originalShape = original.call(instance, this.mob.level(), blockPos);
        if (originalShape.isEmpty()) {
            // Use the feet of the mob, not the corner of its block. That corner can land inside the deck the mob is
            // standing on when the ship isn't lined up with the world grid, which would make the mob hop forever.
            double feetY = this.mob.getY() + FEET_MARGIN;
            Iterable<Vector3d> alternates = ValkyrienSkies.positionToNearbyShips(this.mob.level(), this.mob.getX(), feetY, this.mob.getZ());
            for (Vector3d alternate : alternates) {
                BlockPos alternatePos = BlockPos.containing(ValkyrienSkies.toMinecraft(alternate));
                BlockState alternateState = this.mob.level().getBlockState(alternatePos);
                if (!alternateState.isAir() && !alternateState.is(BlockTags.DOORS) && !alternateState.is(BlockTags.FENCES)) {
                    VoxelShape alternateShape = alternateState.getCollisionShape(this.mob.level(), alternatePos);
                    if (!alternateShape.isEmpty() && alternate.y() < alternateShape.max(Direction.Axis.Y) + alternatePos.getY()) {
                        return Shapes.block();
                    }
                }
            }
        }
        return originalShape;
    }
}

