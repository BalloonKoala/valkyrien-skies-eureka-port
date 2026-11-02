package org.valkyrienskies.mod.fabric.mixin.server.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    @WrapOperation(
        method = "handleInteract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
        )
    )
    AABB getWorldAABB(Entity entity, Operation<AABB> getAABB) {
        return VSGameUtilsKt.transformAabbToWorld(entity.level(), getAABB.call(entity));
    }
}
