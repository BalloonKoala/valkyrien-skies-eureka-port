package org.valkyrienskies.mod.mixin.mod_compat.entity_culling;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * Entity Culling hides every block entity whose box it can't see, and the box it makes from a block entity's position
 * sits in the shipyard for blocks on a ship, far from the camera. That hid helm wheels, cannon barrels, chests and
 * signs on every ship. The box goes where the ship is drawn instead, so its checks work for ships too.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.entityculling.EntityCullingMod", remap = false)
public abstract class MixinEntityCullingMod {
    @ModifyReturnValue(method = "setupAABB", at = @At("RETURN"), remap = false)
    private AABB vs$boxWhereShipIs(final AABB box, final BlockEntity blockEntity, final BlockPos pos) {
        if (box == null || blockEntity == null) return box;
        final Level level = blockEntity.getLevel();
        if (level == null || !VSGameUtilsKt.isBlockInShipyard(level, pos)) return box;
        final LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
        if (ship == null) return box;
        final Matrix4dc shipToWorld = ship instanceof final ClientShip clientShip
            ? clientShip.getRenderTransform().getShipToWorld() : ship.getShipToWorld();
        final AABBd inWorld = VectorConversionsMCKt.toJOML(box).transform(shipToWorld);
        return VectorConversionsMCKt.toMinecraft(inWorld);
    }
}
