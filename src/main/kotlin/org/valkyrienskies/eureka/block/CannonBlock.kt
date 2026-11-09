package org.valkyrienskies.eureka.block

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.valkyrienskies.eureka.item.CannonballItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DispenserBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import org.joml.Vector3d
import org.valkyrienskies.eureka.blockentity.CannonBlockEntity
import org.valkyrienskies.eureka.entity.Cannonball
import org.valkyrienskies.mod.common.getShipManagingPos

/**
 * Works like a dispenser that only takes cannonballs: a redstone pulse or a wired key fires one out of the barrel.
 * The barrel sits on a swivel and can point anywhere. Sneak and right click it, then look where it should fire.
 */
class CannonBlock(properties: BlockBehaviour.Properties) : DispenserBlock(properties) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = CannonBlockEntity(pos, state)

    // The barrel points where the player is looking, not back at them like a dispenser
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection)

    override fun useWithoutItem(
        state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult
    ): InteractionResult {
        if (!player.isSecondaryUseActive) return super.useWithoutItem(state, level, pos, player, hitResult)
        if (level is ServerLevel && player is ServerPlayer) {
            (level.getBlockEntity(pos) as? CannonBlockEntity)?.startAiming(player)
        }
        return InteractionResult.SUCCESS
    }

    override fun <T : BlockEntity> getTicker(
        level: Level, state: BlockState, type: BlockEntityType<T>
    ): BlockEntityTicker<T>? = if (level.isClientSide) null else BlockEntityTicker { tickLevel, _, _, blockEntity ->
        (blockEntity as? CannonBlockEntity)?.serverTick(tickLevel as ServerLevel)
    }

    // Unlike a dispenser, only a signal right at the cannon counts. Side by side or stacked cannons each have their own
    // button, power near the block above one must not fire it.
    override fun neighborChanged(
        state: BlockState, level: Level, pos: BlockPos, block: Block, orientation: Orientation?, movedByPiston: Boolean
    ) {
        val powered = level.hasNeighborSignal(pos)
        val triggered = state.getValue(TRIGGERED)
        if (powered && !triggered) {
            level.scheduleTick(pos, this, TRIGGER_DELAY_TICKS)
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_CLIENTS)
        } else if (!powered && triggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), Block.UPDATE_CLIENTS)
        }
    }

    override fun dispenseFrom(level: ServerLevel, state: BlockState, pos: BlockPos) {
        val cannon = level.getBlockEntity(pos) as? CannonBlockEntity ?: return
        // The first cannonball in the cannon, other things in it are never fired
        val slot = (0 until cannon.containerSize).firstOrNull { cannon.getItem(it).item is CannonballItem }
        if (slot == null) {
            level.levelEvent(DISPENSER_FAIL_EVENT, pos, 0)
            return
        }
        val ammo = cannon.removeItem(slot, 1)
        fire(level, cannon, pos, ammo)
    }

    private fun fire(level: ServerLevel, cannon: CannonBlockEntity, pos: BlockPos, ammo: ItemStack) {
        val direction = cannon.direction()
        val muzzle = Vector3d(pos.x + 0.5, pos.y + PIVOT_HEIGHT, pos.z + 0.5).fma(MUZZLE_DISTANCE, direction)
        val velocity = Vector3d()

        val ship = level.getShipManagingPos(pos)
        if (ship != null) {
            ship.shipToWorld.transformPosition(muzzle)
            ship.shipToWorld.transformDirection(direction).normalize()
            ship.velocity.div(TICKS_PER_SECOND, velocity)
        }
        direction.mul(SHOT_SPEED).add(velocity)

        val ball = Cannonball(level, muzzle.x, muzzle.y, muzzle.z)
        ball.setItem(ammo)
        ball.setDeltaMovement(direction.x, direction.y, direction.z)
        level.addFreshEntity(ball)

        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 1.3f)
        level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0)
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.2, 0.2, 0.2, 0.05)
    }

    companion object {
        /** Height in the block of the point the barrel swivels around. */
        const val PIVOT_HEIGHT = 9.0 / 16.0
        private const val DISPENSER_FAIL_EVENT = 1001
        private const val TRIGGER_DELAY_TICKS = 4
        private const val MUZZLE_DISTANCE = 1.1
        private const val SHOT_SPEED = 2.5
        private const val TICKS_PER_SECOND = 20.0
    }
}
