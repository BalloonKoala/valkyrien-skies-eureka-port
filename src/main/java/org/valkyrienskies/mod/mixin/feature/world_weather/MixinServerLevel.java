package org.valkyrienskies.mod.mixin.feature.world_weather;

import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos occlude(
        ServerLevel level, Types types, BlockPos pos, Operation<BlockPos> original,
        @Share("ship") LocalRef<Ship> shipRef
    ) {
        BlockPos result = original.call(level, types, pos);
        BlockPos failure = BlockPos.ZERO.above(level.getMinY() - 1);

        // Simple comparison with no ray casting. Ships will not occlude each other or world.
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, ChunkPos.containing(result));
        shipRef.set(ship);
        if (ship != null) {
            BlockPos worldPos = BlockPos.containing(CompatUtil.INSTANCE.toSameSpaceAs(level, Vec3.atCenterOf(result), (Ship)null, ship));
            BlockPos worldHeight = original.call(level, types, worldPos);
            if (worldHeight.getY() > worldPos.getY()) { // the ship block is occluded by a world block above it
                // Return an invalid BlockPos for which no snowfall and freezing will ever occur.
                return failure;
            }
        }
        return result;
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> useBiomeAtWorldPos(ServerLevel level, BlockPos pos, Operation<Holder<Biome>> original, @Share("ship") LocalRef<Ship> shipRef) {
        return original.call(level, BlockPos.containing(CompatUtil.INSTANCE.toSameSpaceAs(level, Vec3.atCenterOf(pos), (Ship)null, shipRef.get())));
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Precipitation usePrecipitationAtWorldPos(Biome instance, BlockPos pos, int seaLevel, Operation<Precipitation> original, @Share("ship") LocalRef<Ship> shipRef) {
        ServerLevel level = ServerLevel.class.cast(this);
        return original.call(instance, BlockPos.containing(CompatUtil.INSTANCE.toSameSpaceAs(level, Vec3.atCenterOf(pos), (Ship)null, shipRef.get())), seaLevel);
    }
}
