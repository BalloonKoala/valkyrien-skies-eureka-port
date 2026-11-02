package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {

    @Shadow
    private @Nullable ClientLevel level;

    /**
     * @reason This mixin forces the game to always render block damage.
     */
    @ModifyExpressionValue(
        method = "extractBlockDestroyAnimation",
        at = @At(value = "CONSTANT", args = "doubleValue=1024", ordinal = 0)
    )
    private double disableBlockDamageDistanceCheck(final double originalBlockDamageDistanceConstant) {
        return Double.MAX_VALUE;
    }

    /**
     * If an entity, for example an arrow stuck on a ship, is attached outside the border of the ship's chunk claim,
     * it won't be rendered because the chunk isn't compiled.
     * This injector bypasses that if the entity is in shipyard next to the compiled chunk.
     */
    @WrapOperation(
        method = "isEntityVisible",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;J)Z")
    )
    private boolean isInShipyardBorder(final LevelRenderer instance, final BlockPos blockPos, final long fadeDuration, final Operation<Boolean> original) {
        if (original.call(instance, blockPos, fadeDuration)) return true;
        if (VSGameUtilsKt.isBlockInShipyard(level, blockPos)) {
            final BlockPos blockPos1 = blockPos.offset(-1, -1, -1);
            final BlockPos blockPos2 = blockPos.offset(1, 1, 1);
            for (final BlockPos neighbor : BlockPos.betweenClosed(blockPos1, blockPos2)) {
                if (original.call(instance, neighbor, fadeDuration)) return true;
            }
        }
        return false;
    }
}
