package org.valkyrienskies.eureka.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DispenserBlock
import net.minecraft.world.level.block.entity.DispenserBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.joml.Vector3d
import org.valkyrienskies.eureka.EurekaBlockEntities
import org.valkyrienskies.eureka.item.CannonballItem
import org.valkyrienskies.eureka.wiring.Wireable
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.api.toJOML
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class CannonBlockEntity(pos: BlockPos, state: BlockState) :
    DispenserBlockEntity(EurekaBlockEntities.CANNON.get(), pos, state), Wireable {

    /**
     * Where the barrel points, in degrees, on the ship if the cannon is on one. The turn is counted from the way the
     * block faces, so it turns along when the block is rotated, as ships do when they're taken apart. The tilt is
     * Minecraft's pitch, so negative points up.
     */
    var turn = 0f
        private set
    var tilt = 0f
        private set

    /** The key that fires this cannon from its seat, like a wireless button but without one. */
    override var key: String? = null
        set(value) {
            field = value
            setChanged()
        }

    // Relative to the cannon, so it stays right when the ship is taken apart and put back together. Null is the helm.
    private var contactOffset: BlockPos? = null

    override var contact: BlockPos?
        get() = contactOffset?.let { blockPos.offset(it) }
        set(value) {
            contactOffset = value?.subtract(blockPos)
            setChanged()
        }

    override val isCannon get() = true

    // The same short wait as a redstone pulse, and a key held down or mashed can't fire faster than that
    override fun fireFromKey(level: ServerLevel) {
        level.scheduleTick(blockPos, blockState.block, FIRE_DELAY_TICKS)
    }

    private var aimingPlayer: UUID? = null
    private var aimingTicks = 0

    // Where the barrel is drawn, it swings over to the aim instead of jumping. Client only.
    var shownYaw = Float.NaN
    var shownPitch = 0f
    var shownAtNanos = 0L

    override fun getDefaultName(): Component = Component.translatable("container.vs_eureka.cannon")

    // Cannons only fire cannonballs
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = stack.item is CannonballItem

    private val facing: Direction get() = blockState.getValueOrElse(DispenserBlock.FACING, Direction.NORTH)

    /** Minecraft's yaw of the barrel, on the ship if the cannon is on one. */
    val yaw: Float
        get() = when (facing) {
            Direction.UP, Direction.DOWN -> Direction.NORTH.toYRot() + turn
            else -> facing.toYRot() + turn
        }

    /** Minecraft's pitch of the barrel. Cannons that faced up or down before they could be aimed keep doing so. */
    val pitch: Float
        get() = when (facing) {
            Direction.UP -> if (hasAim) tilt else -90f
            Direction.DOWN -> if (hasAim) tilt else 90f
            else -> tilt
        }

    private var hasAim = false

    /** Unit vector the barrel points along, on the ship if the cannon is on one. */
    fun direction(): Vector3d {
        val y = Math.toRadians(yaw.toDouble())
        val p = Math.toRadians(pitch.toDouble())
        return Vector3d(-sin(y) * cos(p), -sin(p), cos(y) * cos(p))
    }

    private fun aimAlong(direction: Vector3d) {
        val newYaw = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
        val newPitch = Math.toDegrees(-atan2(direction.y, hypot(direction.x, direction.z))).toFloat()
        val base = if (facing.axis.isHorizontal) facing.toYRot() else Direction.NORTH.toYRot()
        val newTurn = Mth.wrapDegrees(newYaw - base)
        if (hasAim && abs(Mth.wrapDegrees(newTurn - turn)) < MIN_CHANGE && abs(newPitch - tilt) < MIN_CHANGE) return
        turn = newTurn
        tilt = newPitch.coerceIn(-90f, 90f)
        hasAim = true
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /** The barrel follows where this player looks for as long as they hold sneak. */
    fun startAiming(player: ServerPlayer) {
        aimingPlayer = player.uuid
        aimingTicks = 0
        player.sendOverlayMessage(Component.translatable("message.vs_eureka.cannon_aiming"))
    }

    fun serverTick(level: ServerLevel) {
        val id = aimingPlayer ?: return
        val player = level.getPlayerByUUID(id) as? ServerPlayer
        aimingTicks++
        // The sneak that started aiming may still be on its way, give it a moment
        val stillAiming = player != null && player.isAlive && (player.isShiftKeyDown || aimingTicks < GRACE_TICKS) &&
            distanceSquared(level, player) < MAX_AIM_DISTANCE * MAX_AIM_DISTANCE
        if (!stillAiming) {
            aimingPlayer = null
            player?.sendOverlayMessage(Component.translatable("message.vs_eureka.cannon_aimed", elevationText()))
            return
        }
        val look = player!!.getViewVector(1f).toJOML()
        level.getShipManagingPos(blockPos)?.worldToShip?.transformDirection(look)
        aimAlong(look.normalize())
        if (aimingTicks % 4 == 0) {
            player.sendOverlayMessage(Component.translatable("message.vs_eureka.cannon_aiming_at", elevationText()))
        }
    }

    private fun elevationText(): Component {
        val degrees = (-pitch).roundToInt()
        return when {
            degrees > 0 -> Component.translatable("message.vs_eureka.cannon_up", degrees)
            degrees < 0 -> Component.translatable("message.vs_eureka.cannon_down", -degrees)
            else -> Component.translatable("message.vs_eureka.cannon_level")
        }
    }

    private fun distanceSquared(level: ServerLevel, player: ServerPlayer): Double {
        val center = Vector3d(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5)
        level.getShipManagingPos(blockPos)?.shipToWorld?.transformPosition(center)
        return center.distanceSquared(player.x, player.eyeY, player.z)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        key?.let { output.putString(KEY_TAG, it) }
        contactOffset?.let { output.putLong(CONTACT_TAG, it.asLong()) }
        if (hasAim) {
            output.putFloat(TURN_TAG, turn)
            output.putFloat(TILT_TAG, tilt)
        }
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        key = input.getString(KEY_TAG).orElse(null)
        contactOffset = input.getLong(CONTACT_TAG).map(BlockPos::of).orElse(null)
        val savedTurn = input.getFloatOr(TURN_TAG, Float.NaN)
        hasAim = !savedTurn.isNaN()
        turn = if (hasAim) savedTurn else 0f
        tilt = input.getFloatOr(TILT_TAG, 0f)
    }

    // Clients only need the aim to draw the barrel, not the TNT inside
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = CompoundTag().apply {
        if (hasAim) {
            putFloat(TURN_TAG, turn)
            putFloat(TILT_TAG, tilt)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    private companion object {
        const val KEY_TAG = "Key"
        const val CONTACT_TAG = "Contact"
        const val FIRE_DELAY_TICKS = 4
        const val TURN_TAG = "AimTurn"
        const val TILT_TAG = "AimTilt"
        const val MIN_CHANGE = 0.25f
        const val GRACE_TICKS = 6
        const val MAX_AIM_DISTANCE = 8.0
    }
}
