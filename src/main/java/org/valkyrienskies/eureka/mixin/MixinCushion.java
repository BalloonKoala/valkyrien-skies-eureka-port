package org.valkyrienskies.eureka.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.eureka.block.ShipHelmBlock;
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity;

@Mixin(Cushion.class)
public abstract class MixinCushion extends BlockAttachedEntity {

    protected MixinCushion(final EntityType<? extends BlockAttachedEntity> type, final Level level) {
        super(type, level);
    }

    /**
     * Sitting on a cushion in front of the helm of a ship takes the wheel.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void driveFromCushion(final Player player, final InteractionHand hand, final Vec3 location,
        final CallbackInfoReturnable<InteractionResult> cir) {
        if (this.level().isClientSide() || player.isSecondaryUseActive() || this.isVehicle()) {
            return;
        }
        final ShipHelmBlockEntity helm = vs_eureka$findHelm();
        if (helm == null || !helm.getAssembled()) {
            return;
        }
        if (helm.getOccupied()) {
            cir.setReturnValue(InteractionResult.PASS);
        } else if (helm.sit(player, false)) {
            this.playSound(SoundEvents.CUSHION_SIT, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
        }
    }

    @Unique
    private ShipHelmBlockEntity vs_eureka$findHelm() {
        final BlockPos cushionPos = this.blockPosition();
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            final BlockPos helmPos = cushionPos.relative(direction);
            final BlockState state = this.level().getBlockState(helmPos);
            if (state.getBlock() instanceof ShipHelmBlock
                && state.getValue(HorizontalDirectionalBlock.FACING) == direction.getOpposite()
                && this.level().getBlockEntity(helmPos) instanceof final ShipHelmBlockEntity helm) {
                return helm;
            }
        }
        return null;
    }
}
