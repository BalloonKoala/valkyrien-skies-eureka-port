package org.valkyrienskies.mod.mixin.client.multiplayer;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.IShipObjectWorldClientCreator;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Shadow
    private ClientLevel level;

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = Shift.AFTER
        ),
        method = "handleLogin"
    )
    private void beforeHandleLogin(final ClientboundLoginPacket packet, final CallbackInfo ci) {
        ((IShipObjectWorldClientCreator) Minecraft.getInstance()).createShipObjectWorldClient();
    }

    /**
     * Spawn [ShipMountingEntity] on client side
     */
    @Inject(method = "handleAddEntity",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"),
        cancellable = true)
    private void handleShipMountingEntity(final ClientboundAddEntityPacket packet, final CallbackInfo ci) {
        if (packet.getType().equals(ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE)) {
            ci.cancel();
            final double d = packet.getX();
            final double e = packet.getY();
            final double f = packet.getZ();
            final Entity entity = ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE.create(level, EntitySpawnReason.LOAD);
            final int i = packet.getId();
            entity.syncPacketPositionCodec(d, e, f);
            entity.snapTo(d, e, f);
            entity.setXRot(packet.getXRot());
            entity.setYRot(packet.getYRot());
            entity.setId(i);
            entity.setUUID(packet.getUUID());
            this.level.addEntity(entity);
        }
    }

    /**
     * If the player picks up an item/arrow that is on shipyard, animation picking it up has problem lerping the position.
     * To prevent this, the entity is repositioned from shipyard to world again.
     * @author Bunting_chj
     */

    @WrapOperation(
        method = "handleTakeItemEntity",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;")
    )
    private Entity setPositionBackToWorld(ClientLevel level, int i, Operation<Entity> getEntity) {
        Entity entity = getEntity.call(level, i);
        if(VSGameUtilsKt.getShipManaging(entity) instanceof ClientShip ship) {
            WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(entity, ship);
        }
        return entity;
    }

    /**
     * When mc receives a tp packet it lerps it between 2 positions in 3 steps, this is bad for ships it gets stuck in a
     * unloaded chunk clientside and stays there until rejoining the server.
     */
    @WrapOperation(method = "handleEntityPositionSync", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;FF)V"))
    private void teleportingWithNoStep(final Entity instance, final PositionPath path, final float yRot, final float xRot,
        final Operation<Void> moveOrInterpolateTo) {
        if (VSGameUtilsKt.getShipObjectManagingPos(instance.level(), instance.getX(), instance.getY(), instance.getZ()) !=
            null) {
            instance.snapTo(path.endPosition(), yRot, xRot);
        } else {
            moveOrInterpolateTo.call(instance, path, yRot, xRot);
        }
    }
}
