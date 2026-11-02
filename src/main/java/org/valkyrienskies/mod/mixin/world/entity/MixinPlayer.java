package org.valkyrienskies.mod.mixin.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity implements PlayerDuck {

    @Unique
    private final MinecraftPlayer vsPlayer = new MinecraftPlayer(Player.class.cast(this));
    @Unique
    private Vec3 queuedPositionUpdate = null;

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType,
        Level level) {
        super(entityType, level);
    }

    @Override
    public MinecraftPlayer vs_getPlayer() {
        return vsPlayer;
    }

    @Override
    public Vec3 vs_getQueuedPositionUpdate() {
        return this.queuedPositionUpdate;
    }

    @Override
    public void vs_setQueuedPositionUpdate(final Vec3 queuedPositionUpdate) {
        this.queuedPositionUpdate = queuedPositionUpdate;
    }

    @Shadow
    public abstract double blockInteractionRange();

    @Shadow
    public abstract double entityInteractionRange();

    /**
     * Entities that live on a ship, like cushions and item frames, have their bounding box in the shipyard. Measure the
     * distance to them from the player's eyes as seen from the ship, or they're always millions of blocks out of reach.
     *
     * @return the squared distance to [aabb], or a negative number if it isn't on a ship
     */
    @Unique
    private double vs$squaredDistanceToBoxOnShip(final AABB aabb) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level(), BlockPos.containing(aabb.getCenter()));
        if (ship == null) {
            return -1.0;
        }
        final Vec3 eyePosInShip = VectorConversionsMCKt.toMinecraft(
            ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(getEyePosition())));
        return aabb.distanceToSqr(eyePosInShip) * ship.getTransform().getShipToWorldScaling().lengthSquared() / 3.0;
    }

    @Inject(
        method = "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private void includeShipsInEntityRange(final AABB aabb, final double buffer, final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        final double distanceSq = vs$squaredDistanceToBoxOnShip(aabb);
        if (distanceSq >= 0.0) {
            final double maxRange = this.entityInteractionRange() + buffer;
            cir.setReturnValue(distanceSq < maxRange * maxRange);
        }
    }

    @Inject(method = "isWithinAttackRange", at = @At("RETURN"), cancellable = true)
    private void includeShipsInAttackRange(final ItemStack weapon, final AABB aabb, final double buffer,
        final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        final double distanceSq = vs$squaredDistanceToBoxOnShip(aabb);
        if (distanceSq >= 0.0) {
            final double maxRange = this.getAttackRangeWith(weapon).effectiveMaxRange(this) + buffer;
            cir.setReturnValue(distanceSq < maxRange * maxRange);
        }
    }

    @Inject(
        method = "isWithinBlockInteractionRange",
        at = @At("RETURN"),
        cancellable = true
    )
    private void includeShipsInDistanceCheck(BlockPos blockPos, double reachDistance, CallbackInfoReturnable<Boolean> cir) {
        // If the player can already interact then just return
        if (cir.getReturnValueZ()) {
            return;
        }
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level(), blockPos);
        if (ship != null) {
            final double e = this.blockInteractionRange() + reachDistance;
            final Vec3 eyePosInShip = VectorConversionsMCKt.toMinecraft(ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(getEyePosition())));
            // Handle scaling
            final double distanceSq = (new AABB(blockPos)).distanceToSqr(eyePosInShip) * ship.getTransform().getShipToWorldScaling().x() * ship.getTransform().getShipToWorldScaling().x();
            cir.setReturnValue(distanceSq < e * e);
        }
    }
}
