package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(PathfindingContext.class)
public class MixinPathfindingContext {

    /**
     * Newer versions cache path types by position, but on-ship results change as ships move, and the old game never
     * cached them. Skip the cache whenever mobs may path over ships.
     */
    @WrapOperation(
        method = "getPathTypeFromState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/PathTypeCache;getOrCompute(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;"
        )
    )
    private PathType skipCacheForShips(final PathTypeCache cache, final BlockGetter level, final BlockPos pos,
        final Operation<PathType> original) {
        if (VSGameConfig.SERVER.getAiOnShips()) {
            return WalkNodeEvaluatorInvoker.callGetPathTypeFromState(level, pos);
        }
        return original.call(cache, level, pos);
    }
}
