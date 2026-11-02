package org.valkyrienskies.mod.mixin.feature.wave_spawning;

import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.level.Level;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(Raids.class)
public class MixinRaids implements OfLevel {
    @Unique
    private ServerLevel vs$level;

    @Override
    public Level getLevel() {
        return this.vs$level;
    }

    @Override
    public void setLevel(final Level level) {
        this.vs$level = (ServerLevel) level;
    }

    @WrapOperation(method = "getNearbyRaid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/raid/Raid;getCenter()Lnet/minecraft/core/BlockPos;"))
    private BlockPos onGetNearbyRaid(Raid instance, Operation<BlockPos> original) {
        return BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(this.vs$level, original.call(instance)));
    }
}
