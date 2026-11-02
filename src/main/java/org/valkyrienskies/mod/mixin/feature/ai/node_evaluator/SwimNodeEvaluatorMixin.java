package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(SwimNodeEvaluator.class)
public abstract class SwimNodeEvaluatorMixin extends NodeEvaluator {

    @Unique
    private static Level vs$levelOf(final BlockGetter blockGetter) {
        if (blockGetter instanceof PathNavigationRegion) {
            return ((PathNavigationRegionAccessor) blockGetter).getLevel();
        } else if (blockGetter instanceof Level) {
            return (Level) blockGetter;
        }
        return null;
    }

    //region Single block obstacle path type
    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getFluidState()Lnet/minecraft/world/level/material/FluidState;"),
        method = "getPathTypeOfMob"
    )
    private FluidState getFluidStateRedirectPathType(final BlockState instance, final Operation<FluidState> original,
        @Local(argsOnly = true) final PathfindingContext context, @Local final BlockPos.MutableBlockPos blockPos) {
        final FluidState[] fluidState = {original.call(instance)};
        if (!VSGameConfig.SERVER.getAiOnShips()) {
            final Level level = vs$levelOf(context.level());
            if (level != null && fluidState[0].isEmpty()) {

                final double origX = blockPos.getX();
                final double origY = blockPos.getY();
                final double origZ = blockPos.getZ();
                VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                    origY, origZ, 2,
                    (x, y, z) -> {
                        final BlockPos groundPos = BlockPos.containing(x, y, z);
                        final FluidState tempFluidState = level.getFluidState(groundPos);
                        if (!tempFluidState.isEmpty()) { // Skip any empty results for the case of intersecting ships
                            fluidState[0] = tempFluidState;
                        }
                    });
            }
        }
        return fluidState[0];
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/PathfindingContext;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        method = "getPathTypeOfMob"
    )
    private BlockState getBlockStateRedirectPathType(final PathfindingContext instance, final BlockPos blockPos,
        final Operation<BlockState> original) {
        final BlockState[] blockState = {original.call(instance, blockPos)};
        if (instance.level() instanceof PathNavigationRegion && blockState[0].isAir() && VSGameConfig.SERVER.getAiOnShips()) {
            final Level level = ((PathNavigationRegionAccessor) instance.level()).getLevel();

            final double origX = blockPos.getX();
            final double origY = blockPos.getY();
            final double origZ = blockPos.getZ();
            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                origY, origZ, 1,
                (x, y, z) -> {
                    final BlockPos groundPos = BlockPos.containing(x, y, z);
                    final BlockState tempBlockState = level.getBlockState(groundPos);
                    if (!tempBlockState.isAir()) { // Skip any empty results for the case of intersecting ships
                        blockState[0] = tempBlockState;
                    }
                });
        }
        return blockState[0];
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isPathfindable(Lnet/minecraft/world/level/pathfinder/PathComputationType;)Z"),
        method = "getPathTypeOfMob"
    )
    private boolean isPathFindableRedirectPathType(final BlockState instance, final PathComputationType pathComputationType,
        final Operation<Boolean> original, @Local(argsOnly = true) final PathfindingContext context,
        @Local final BlockPos.MutableBlockPos blockPos) {
        final boolean[] isPathFindable = {original.call(instance, pathComputationType)};
        if (!isPathFindable[0] && VSGameConfig.SERVER.getAiOnShips()) {
            final Level level = vs$levelOf(context.level());
            if (level != null) {
                final double origX = blockPos.getX();
                final double origY = blockPos.getY();
                final double origZ = blockPos.getZ();
                VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                    origY, origZ, 1,
                    (x, y, z) -> {
                        final BlockPos groundPos = BlockPos.containing(x, y, z);
                        final boolean pathfindable = original.call(level.getBlockState(groundPos), pathComputationType);
                        if (pathfindable) { // Try to give a true result, not 100% accurate but method expects a single result
                            isPathFindable[0] = true;
                        }
                    });
            }
        }
        return isPathFindable[0];
    }
    //endregion

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/CollisionGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "findAcceptedNode"
    )
    private FluidState getFluidStateRedirectGetNode(final CollisionGetter instance, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState[] fluidState = {getFluidState.call(instance, blockPos)};
        final Level level = vs$levelOf(instance);
        if (!VSGameConfig.SERVER.getAiOnShips()) {
            if (level != null && fluidState[0].isEmpty()) {
                final double origX = blockPos.getX();
                final double origY = blockPos.getY();
                final double origZ = blockPos.getZ();
                VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                    origY, origZ, 1,
                    (x, y, z) -> {
                        final BlockPos groundPos = BlockPos.containing(x, y, z);
                        final FluidState tempFluidState = getFluidState.call(instance, groundPos);
                        if (!tempFluidState.isEmpty()) { // Skip any empty results for the case of intersecting ships
                            fluidState[0] = tempFluidState;
                        }
                    });
            }
        }
        return fluidState[0];
    }

    // TODO - re-add isFree mixins
}
