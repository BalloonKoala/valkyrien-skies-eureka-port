package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Bee.BeePollinateGoal.class)
public abstract class MixinPollinateGoal {
    @Shadow
    @Final
    Bee this$0;

    @Shadow
    protected abstract Optional<BlockPos> findNearbyFlower();

    @Unique
    private boolean vs$searchingShips = false;
    @Unique
    private BlockPos modifiedBeePosition = null;

    @Inject(method = "findNearbyFlower", at = @At("RETURN"), cancellable = true)
    private void preFindNearbyFlower(CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (!vs$searchingShips && cir.getReturnValue().isEmpty()) {
            vs$searchingShips = true;
            Optional<BlockPos> res =
                VSGameUtilsKt.transformToNearbyShipsAndWorld(this.this$0.level(), this$0.blockPosition().getX(),
                    this$0.blockPosition().getY(), this$0.blockPosition().getZ(), 5.0).stream().flatMap(pos -> {
                    this.modifiedBeePosition = BlockPos.containing(pos.x, pos.y, pos.z);
                    return findNearbyFlower().stream();
                }).min(Comparator.comparingDouble(
                    pos -> VSGameUtilsKt.squaredDistanceBetweenInclShips(this.this$0.level(),
                        modifiedBeePosition.getX(), modifiedBeePosition.getY(), modifiedBeePosition.getZ(), pos.getX(),
                        pos.getY(), pos.getZ())));
            modifiedBeePosition = this$0.blockPosition();
            vs$searchingShips = false;
            cir.setReturnValue(res);
        }
    }

    @WrapOperation(method = "findNearbyFlower", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/bee/Bee;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos onBlockPosition(Bee instance, Operation<BlockPos> original) {
        return vs$searchingShips ? modifiedBeePosition : original.call(instance);
    }
}
