package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(PathNavigation.class)
public class MixinPathNavigation {
    @Unique
    private static final double MAX_PATH_DRIFT_SQ = 1.0;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    protected Path path;

    @Unique
    private Path vs$trackedPath;

    @Unique
    private final Vector3d vs$pathEndWorld = new Vector3d();

    @Unique
    private final Vector3d vs$pathEndInShip = new Vector3d();

    @Shadow
    public void stop() {
    }

    /**
     * Path nodes are in world space, so they stop meaning anything once the ship under the mob moves. Walking to them
     * would drag the mob across the deck after a spot the ship already left, so forget the path instead.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void vs$dropPathLeftBehindByShip(final CallbackInfo ci) {
        if (this.path == null || !(this.mob instanceof final IEntityDraggingInformationProvider provider)) {
            vs$trackedPath = null;
            return;
        }
        final EntityDraggingInformation dragInfo = provider.getDraggingInformation();
        final Long shipId = dragInfo.getLastShipStoodOn();
        final Ship ship = shipId == null || !dragInfo.isEntityBeingDraggedByAShip() ? null :
            VSGameUtilsKt.getShipObjectWorld(this.level).getAllShips().getById(shipId);
        if (ship == null) {
            vs$trackedPath = null;
            return;
        }
        final BlockPos end = this.path.getTarget();
        if (vs$trackedPath != this.path) {
            vs$trackedPath = this.path;
            vs$pathEndWorld.set(end.getX(), end.getY(), end.getZ());
            ship.getTransform().getWorldToShip().transformPosition(vs$pathEndWorld, vs$pathEndInShip);
            return;
        }
        final Vector3d endNow = ship.getTransform().getShipToWorld().transformPosition(vs$pathEndInShip, new Vector3d());
        if (endNow.distanceSquared(vs$pathEndWorld) > MAX_PATH_DRIFT_SQ) {
            vs$trackedPath = null;
            this.stop();
        }
    }

    @WrapOperation(method = "moveTo(DDDD)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(DDDI)Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path onMoveToCreatePath(PathNavigation instance, double d, double e, double f, int i,
        Operation<Path> original) {
        Vec3 transformedPos = VSGameUtilsKt.toWorldCoordinates(this.level, new Vec3(d, e, f));
        return original.call(instance, transformedPos.x, transformedPos.y, transformedPos.z, i);
    }

    @WrapOperation(
        method = "isStableDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$getBlockStateIsNotStable(
        Level instance, BlockPos blockPos, Operation<BlockState> original) {
        BlockState originalState = original.call(instance, blockPos);
        if (originalState.isSolidRender()) return originalState;
        Iterable<Vector3d> candidates = ValkyrienSkies.positionToNearbyShips(instance, blockPos.above().getX(), blockPos.above().getY(), blockPos.above().getZ());
        for (Vector3d candidate : candidates) {
            Ship ship = ValkyrienSkies.getShipManagingBlock(instance, candidate);
            if (ship == null) continue;
            Vector3dc upVector = ship.getTransform().getShipToWorld().transformDirection(VectorConversionsMCKt.toJOMLD(Direction.UP.getUnitVec3i())).normalize();
            Direction closestDirection = Direction.getApproximateNearest(upVector.x(), upVector.y(), upVector.z()).getOpposite();
            BlockPos candidatePos = BlockPos.containing(ValkyrienSkies.toMinecraft(candidate)).relative(closestDirection);
            BlockState candidateState = original.call(instance, candidatePos);
            if (candidateState.isSolidRender()) {
                return candidateState;
            }
        }
        return originalState;
    }
}
