package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.world.entity.PositionMoveRotation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collections;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl extends ServerCommonPacketListenerImpl {
    public MixinServerGamePacketListenerImpl(MinecraftServer minecraftServer, Connection connection,
        CommonListenerCookie commonListenerCookie) {
        super(minecraftServer, connection, commonListenerCookie);
    }

    @Shadow
    public ServerPlayer player;

    @Shadow
    private int awaitingTeleport;

    @Shadow
    private int tickCount;

    @Shadow
    private Vec3 awaitingPositionFromClient;

    @Shadow
    private int awaitingTeleportTime;

    @ModifyExpressionValue(
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;aboveGroundTickCount:I", ordinal = 0),
        method = "tickPlayer"
    )
    private int noFlyKick(final int original) {
        if (VSGameConfig.SERVER.getEnableMovementChecks()) {
            return original;
        } else {
            return 0;
        }
    }

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        ),
        method = "handleUseItemOn"
    )
    private Vec3 skipDistanceCheck2(final Vec3 instance, final Vec3 vec3, final Operation<Vec3> subtract) {
        return VSGameUtilsKt.toWorldCoordinates(player.level(), subtract.call(instance, vec3));
    }

    /*
    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ChunkPos;getChessboardDistance(Lnet/minecraft/world/level/ChunkPos;)I"
        ),
        method = "handleUseItemOn"
    )
    private int skipDistanceCheck(final ChunkPos instance, final ChunkPos chunkPos, final Operation<Integer> getChessboardDistance) {
        return 0;
    }

     */

    @Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
    private void shouldSkipMoveCheck1(final boolean elytra, final CallbackInfoReturnable<Boolean> cir) {
        if (!VSGameConfig.SERVER.getEnableMovementChecks()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * The "moved wrongly" check, which 2.4.11 skips the same way through ServerPlayerGameMode.isCreative. The server
     * works out where a move ends against its own copy of the ships, which is a tick or a ping off from the player's on a
     * moving ship, so it kept pulling survival players back.
     */
    @WrapOperation(
        method = "handlePlayerPositionChange",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isCreative()Z")
    )
    private boolean vs$skipMovedWrongly(final ServerPlayer instance, final Operation<Boolean> isCreative) {
        return !VSGameConfig.SERVER.getEnableMovementChecks() || isCreative.call(instance);
    }

    @WrapOperation(
        method = "handleMoveVehicle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"
        ),
        require = 0
    )
    private boolean shouldSkipMoveCheck2(final ServerGamePacketListenerImpl instance,
        final Operation<Boolean> isSinglePlayerOwner) {
        return !VSGameConfig.SERVER.getEnableMovementChecks();
    }

    // Fixes:
    // https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/87
    // Bed Bug
    @Inject(
        method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V",
        at = @At(value = "HEAD"),
        cancellable = true
    )
    private void transformTeleport(final PositionMoveRotation change, final Set<Relative> relativeSet,
        final CallbackInfo ci) {
        final double x = change.position().x;
        final double y = change.position().y;
        final double z = change.position().z;
        final float yaw = change.yRot();
        final float pitch = change.xRot();

        if (!VSGameConfig.SERVER.getTransformTeleports()) {
            return;
        }

        final BlockPos blockPos = BlockPos.containing(x, y, z);
        final ServerShip ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) player.level(), blockPos);

        // TODO add flag to disable this https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/30
        if (ship != null) {
            final Vector3d pos = new Vector3d(x, y, z);
            ship.getShipToWorld().transformPosition(pos);

            this.awaitingPositionFromClient = VectorConversionsMCKt.toMinecraft(pos);
            if (++this.awaitingTeleport == Integer.MAX_VALUE) {
                this.awaitingTeleport = 0;
            }
            this.awaitingTeleportTime = this.tickCount;
            this.player.absSnapTo(pos.x, pos.y, pos.z, yaw, pitch);

            this.send(
                new ClientboundPlayerPositionPacket(awaitingTeleport,
                    new PositionMoveRotation(VectorConversionsMCKt.toMinecraft(pos), Vec3.ZERO, yaw, pitch),
                    Collections.emptySet()));
            ci.cancel();
        }
    }

    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    void onDisconnect(DisconnectionDetails disconnectionDetails, CallbackInfo ci) {
        final VsiServerShipWorld world = VSGameUtilsKt.getShipObjectWorld(this.server);
        if (world != null) {
            world.onDisconnect(VSGameUtilsKt.getPlayerWrapper(this.player));
        }
    }

    /**
     * The position in a move packet is in world space, worked out from where the client last saw the ship. On a moving
     * ship that's already behind by the player's ping, so use the position on the ship that came along with it instead.
     */
    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ServerboundMovePlayerPacket vs$usePositionOnShip(final ServerboundMovePlayerPacket packet) {
        if (!packet.hasPosition() || !this.server.isSameThread() || !(this.player instanceof final PlayerDuck duck)) {
            return packet;
        }
        final Vec3 inShip = duck.vs_getQueuedPositionUpdate();
        if (inShip == null) {
            return packet;
        }
        duck.vs_setQueuedPositionUpdate(null);
        final Long shipId = ((IEntityDraggingInformationProvider) this.player).getDraggingInformation().getLastShipStoodOn();
        final ServerShip ship = shipId == null ? null : VSGameUtilsKt.getShipObjectWorld(this.server).getAllShips().getById(shipId);
        if (ship == null) {
            return packet;
        }
        final Vector3d world = ship.getShipToWorld().transformPosition(new Vector3d(inShip.x, inShip.y, inShip.z));
        if (packet.hasRotation()) {
            return new ServerboundMovePlayerPacket.PosRot(world.x, world.y, world.z, packet.getYRot(0.0F),
                packet.getXRot(0.0F), packet.isOnGround(), packet.horizontalCollision());
        }
        return new ServerboundMovePlayerPacket.Pos(world.x, world.y, world.z, packet.isOnGround(), packet.horizontalCollision());
    }

}
