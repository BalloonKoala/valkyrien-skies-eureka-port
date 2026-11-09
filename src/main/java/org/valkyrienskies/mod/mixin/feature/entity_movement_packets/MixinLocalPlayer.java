package org.valkyrienskies.mod.mixin.feature.entity_movement_packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.networking.PacketEntityShipMotion;
import org.valkyrienskies.mod.common.networking.PacketPlayerShipMotion;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends Entity implements IEntityDraggingInformationProvider {
    @Shadow
    public abstract float getViewYRot(float f);

    public MixinLocalPlayer(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @author Tomato
     * @reason Intercept client -> server player position sending to send our own data.
     */
    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendPosition(ClientPacketListener instance, Packet<?> arg, Operation<Void> original) {
        Packet<?> realArg = arg;
        if (getDraggingInformation().isEntityBeingDraggedByAShip()) {
            if (getDraggingInformation().getLastShipStoodOn() != null) {
                ClientShip ship = VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance().level).getAllShips().getById(getDraggingInformation().getLastShipStoodOn());
                if (ship != null) {
                    Vector3dc relativePosition = ship.getWorldToShip().transformPosition(
                        VectorConversionsMCKt.toJOML(getPosition(1f)), new Vector3d());

                    double relativeYaw = EntityLerper.INSTANCE.yawToShip(ship, getViewYRot(1f));

                    PacketPlayerShipMotion packet = new PacketPlayerShipMotion(ship.getId(), relativePosition.x(), relativePosition.y(), relativePosition.z(), relativeYaw);
                    ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
                }
            }
            if (realArg instanceof ServerboundMovePlayerPacket movePacket) {
                // Not while gliding, the server ends a glide as soon as it hears the player is on the ground
                final boolean isOnGround = movePacket.isOnGround() ||
                    (getDraggingInformation().isEntityBeingDraggedByAShip() && !((LocalPlayer) (Object) this).isFallFlying());
                if (movePacket.hasPosition() && movePacket.hasRotation()) {
                    //posrot
                    realArg = new ServerboundMovePlayerPacket.PosRot(movePacket.getX(0.0), movePacket.getY(0.0), movePacket.getZ(0.0), movePacket.getYRot(0.0f), movePacket.getXRot(0.0f), isOnGround, movePacket.horizontalCollision());
                } else if (movePacket.hasPosition()) {
                    //pos
                    realArg = new ServerboundMovePlayerPacket.Pos(movePacket.getX(0.0), movePacket.getY(0.0), movePacket.getZ(0.0), isOnGround, movePacket.horizontalCollision());
                } else if (movePacket.hasRotation()) {
                    //rot
                    realArg = new ServerboundMovePlayerPacket.Rot(movePacket.getYRot(0.0f), movePacket.getXRot(0.0f), isOnGround, movePacket.horizontalCollision());
                } else {
                    //status only
                    realArg = new ServerboundMovePlayerPacket.StatusOnly(isOnGround, movePacket.horizontalCollision());
                }
            }
        }
        original.call(instance, realArg);
    }

    /**
     * Taking off with an elytra from a ship. For a while after a jump on a ship, move packets tell the server the player
     * is on the ground, so ship motion can't build up fall damage, and the server turns gliding down while it believes
     * that. So the server hears that the player is in the air before it hears about the glide. The ship keeps carrying
     * the player like after any jump, and hands its speed over once they're clear of it.
     */
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
    private boolean vs$takeOffFromShip(final LocalPlayer player, final Operation<Boolean> original) {
        final boolean started = original.call(player);
        if (started && getDraggingInformation().isEntityBeingDraggedByAShip()) {
            player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(false, player.horizontalCollision));
        }
        return started;
    }

    @WrapOperation(method = "sendChanges",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendVehiclePosition(ClientPacketListener instance, Packet<?> arg, Operation<Void> original) {
        if (arg instanceof ServerboundMoveVehiclePacket vehiclePacket && getRootVehicle() instanceof IEntityDraggingInformationProvider dragProvider) {
            if (dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
                if (dragProvider.getDraggingInformation().getLastShipStoodOn() != null) {
                    if (!dragProvider.vs$shouldDrag()) return;

                    ClientShip ship = VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance().level).getAllShips().getById(
                        dragProvider.getDraggingInformation().getLastShipStoodOn());
                    if (ship != null) {
                        Vector3dc relativePosition = ship.getWorldToShip().transformPosition(
                            VectorConversionsMCKt.toJOML(getRootVehicle().getPosition(1f)), new Vector3d());

                        double relativeYaw = EntityLerper.INSTANCE.yawToShip(ship, getRootVehicle().getYRot());

                        PacketEntityShipMotion packet = new PacketEntityShipMotion(getRootVehicle().getId(), ship.getId(), relativePosition.x(), relativePosition.y(), relativePosition.z(), 0.0, 0.0, 0.0, relativeYaw, 0.0);
                        ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
                    }
                }
            }
        }
        original.call(instance, arg);
    }

}
