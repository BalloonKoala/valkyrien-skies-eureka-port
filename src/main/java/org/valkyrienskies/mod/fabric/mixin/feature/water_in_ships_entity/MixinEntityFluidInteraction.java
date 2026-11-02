package org.valkyrienskies.mod.fabric.mixin.feature.water_in_ships_entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * Lets entities float in water that is on a ship. After the normal pass, the same scan runs again for each nearby ship
 * with the entity's box in ship space, adding to what the normal pass found.
 */
@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {
    @Shadow
    private EntityFluidInteraction.Tracker getOrCreateTrackerFor(final Holder<Fluid> fluidType) {
        throw new AssertionError();
    }

    @Shadow
    public abstract boolean update(Entity entity, boolean ignoreCurrent);

    /**
     * The entity's box in ship space while scanning a ship
     */
    @Unique
    private AABB valkyrienskies$shipBox = null;

    @Unique
    private boolean inShipContext() {
        return valkyrienskies$shipBox != null;
    }

    @WrapOperation(
        method = "update",
        at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z")
    )
    private <T> boolean keepTrackersInShipContext(final List<T> trackers, final Predicate<T> reset,
        final Operation<Boolean> original) {
        return inShipContext() ? false : original.call(trackers, reset);
    }

    @WrapOperation(
        method = "update",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectCollection;forEach(Ljava/util/function/Consumer;)V")
    )
    private <T> void keepCurrentsInShipContext(final ObjectCollection<T> currents, final Consumer<T> reset,
        final Operation<Void> original) {
        if (!inShipContext()) {
            original.call(currents, reset);
        }
    }

    @WrapOperation(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getFluidInteractionBox()Lnet/minecraft/world/phys/AABB;")
    )
    private AABB setFluidBoxInShipContext(final Entity entity, final Operation<AABB> original) {
        return inShipContext() ? valkyrienskies$shipBox : original.call(entity);
    }

    @WrapOperation(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;")
    )
    private AABB setBoundingBoxInShipContext(final Entity entity, final Operation<AABB> original) {
        return inShipContext() ? valkyrienskies$shipBox : original.call(entity);
    }

    @WrapMethod(method = "update")
    private boolean collectShipFluid(final Entity entity, final boolean ignoreCurrent, final Operation<Boolean> original) {
        final boolean[] inFluid = {original.call(entity, ignoreCurrent)};
        if (inShipContext() || ((IEntityDraggingInformationProvider) entity).vs$isInSealedArea()) {
            return inFluid[0];
        }

        final AABB box = entity.getFluidInteractionBox();
        if (box == null) {
            return inFluid[0];
        }

        final Level level = entity.level();
        VSGameUtilsKt.transformFromWorldToNearbyShips(level, box, (shipBox) -> {
            valkyrienskies$shipBox = shipBox;
            inFluid[0] |= this.update(entity, ignoreCurrent);
        });
        valkyrienskies$shipBox = null;

        updateEyesInShipFluid(entity, level);
        return inFluid[0];
    }

    @Unique
    private void updateEyesInShipFluid(final Entity entity, final Level level) {
        final double eyeY = entity.getEyeY() - 0.1111111119389534;
        final BlockPos eyePos = BlockPos.containing(entity.getX(), eyeY, entity.getZ());
        if (!level.getFluidState(eyePos).isEmpty()) {
            return;
        }

        final FluidState[] shipFluid = {level.getFluidState(eyePos)};
        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, entity.getX(), eyeY, entity.getZ(), entity.getBoundingBox().getSize(),
            (double x, double y, double z) -> {
                shipFluid[0] = level.getFluidState(BlockPos.containing(x, y, z));
            });

        final FluidState fluid = shipFluid[0];
        if (fluid.isEmpty()) {
            return;
        }
        final double fluidTop = eyePos.getY() + (fluid.isSource() ? 1.0 : fluid.getHeight(level, eyePos));
        if (fluidTop > eyeY) {
            final EntityFluidInteraction.Tracker tracker = getOrCreateTrackerFor(fluid.typeHolder());
            tracker.eyesInside = true;
        }
    }
}
