package org.valkyrienskies.eureka.block

import org.valkyrienskies.mod.common.config.AddedFeatures
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.world.clipIncludeShips
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.blockentity.DetacherBlockEntity
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore

/**
 * Holds on to whatever is on its red face. On a redstone pulse, or its key from a seat, it lets go: everything that only
 * hangs on through it becomes a ship of its own, pushed off the way the red face points.
 */
class DetacherBlock(properties: BlockBehaviour.Properties) : DirectionalBlock(properties), EntityBlock {

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, POWERED)
    }

    // The red face points away from the block it was put on
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.clickedFace)

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = DetacherBlockEntity(pos, state)

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (!level.isClientSide) (level.getBlockEntity(pos) as? DetacherBlockEntity)?.let { player.openMenu(it) }
        return InteractionResult.SUCCESS
    }

    // Only a signal right at the detacher counts, like a cannon
    override fun neighborChanged(
        state: BlockState, level: Level, pos: BlockPos, block: Block, orientation: Orientation?, movedByPiston: Boolean
    ) {
        val powered = level.hasNeighborSignal(pos)
        if (powered == state.getValue(POWERED)) return
        level.setBlock(pos, state.setValue(POWERED, powered), UPDATE_CLIENTS)
        if (powered) level.scheduleTick(pos, this, RELEASE_DELAY_TICKS)
    }

    // Holding something on the red face it lets go, with nothing there it shoots its pole out to grab a ship
    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        val detacher = level.getBlockEntity(pos) as? DetacherBlockEntity ?: return
        if (detacher.poleOut) {
            stepPole(level, pos, state, detacher)
        } else if (!AddedFeatures.detacher) {
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0f, 1.2f)
        } else if (level.getBlockState(pos.relative(state.getValue(FACING))).isAir) {
            // Grab mode can be turned off, then a detacher holding nothing does nothing
            if (AddedFeatures.detacherGrab) shootPole(level, pos, state, detacher)
            else level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0f, 1.2f)
        } else {
            release(level, pos)
        }
    }

    // A grabbed ship's helms work again once nothing holds it
    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, movedByPiston: Boolean) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)
        val ship = level.getShipManagingPos(pos) ?: return
        for (at in reachable(level, ship, pos, state.getValue(FACING))) (level.getBlockEntity(at) as? ShipHelmBlockEntity)?.heldByDetacher = false
    }

    companion object {
        val POWERED: BooleanProperty = BlockStateProperties.POWERED

        // The same short wait as a cannon, so a key held down or mashed can't go faster than that
        const val RELEASE_DELAY_TICKS = 2

        // How fast what it lets go of moves away, per block of push: half a second later it's that far out
        private const val PUSH_SPEED_PER_BLOCK = 2.0

        private const val MAX_REACH = 5.0
        // Blocks a tick going out, and ticks coming back in with whatever it grabbed
        private const val POLE_SPEED = 1.0
        private const val POLE_BACK_TICKS = 8

        /** How far out the pole is, [ticks] after it started, for one that reaches [reach]. The renderer uses it too. */
        @JvmStatic
        fun poleLength(reach: Float, ticks: Float): Float {
            val outTicks = ceil(reach / POLE_SPEED).toFloat()
            return if (ticks < outTicks) min(ticks * POLE_SPEED.toFloat(), reach)
            else max(0f, reach * (1f - (ticks - outTicks) / POLE_BACK_TICKS))
        }

        /**
         * A ship the pole caught. It ends up with [anchor], the block the pole hit, right against the red face, turned
         * by [rotation] so it lines up with this ship. [fromPos] and [fromRot] are where it was, in this ship's yard, when
         * the pole got there.
         */
        class Grab(val shipId: Long, val anchor: BlockPos, val rotation: Rotation, val turn: Quaterniond) {
            var fromPos: Vector3d? = null
            var fromRot: Quaterniond? = null
        }

        private fun shootPole(level: ServerLevel, pos: BlockPos, state: BlockState, detacher: DetacherBlockEntity) {
            val ship = level.getShipManagingPos(pos)
            if (ship == null) {
                level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0f, 1.2f)
                return
            }
            val facing = state.getValue(FACING)
            val step = Vector3d(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
            val from = ship.shipToWorld.transformPosition(Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5).fma(0.5, step))
            val to = Vector3d(from).fma(MAX_REACH, ship.shipToWorld.transformDirection(Vector3d(step)).normalize())
            // Only ships get grabbed, never the ground, and never this ship itself
            val hit = level.clipIncludeShips(
                ClipContext(Vec3(from.x, from.y, from.z), Vec3(to.x, to.y, to.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()),
                true, ship.id
            )
            var reach = MAX_REACH
            if (hit.type == HitResult.Type.BLOCK) {
                reach = from.distance(hit.location.x, hit.location.y, hit.location.z)
                val caught = level.getShipManagingPos(hit.blockPos)
                if (caught != null && caught.id != ship.id) {
                    detacher.grab = planGrab(level, ship, caught, hit.blockPos.immutable(), pos.relative(facing))
                }
            }
            detacher.startPole(level.gameTime, reach.toFloat())
            level.playSound(null, from.x, from.y, from.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 1.3f)
            level.scheduleTick(pos, state.block, 1)
        }

        // Where every block of the caught ship would land, turned to line up with this ship. Null if anything is in the way.
        private fun planGrab(level: ServerLevel, ship: ServerShip, caught: ServerShip, anchor: BlockPos, target: BlockPos): Grab? {
            val relative = Quaterniond(ship.transform.shipToWorldRotation).conjugate().mul(caught.transform.shipToWorldRotation)
            val x = relative.transform(Vector3d(1.0, 0.0, 0.0))
            val rotation = if (abs(x.x) >= abs(x.z)) {
                if (x.x >= 0) Rotation.NONE else Rotation.CLOCKWISE_180
            } else {
                if (x.z >= 0) Rotation.CLOCKWISE_90 else Rotation.COUNTERCLOCKWISE_90
            }
            val blocks = shipBlocks(level, caught) ?: return null
            if (!fits(level, ship, blocks, anchor, target, rotation)) return null
            val axis = BlockPos(1, 0, 0).rotate(rotation)
            return Grab(caught.id, anchor, rotation, Quaterniond().rotationY(atan2(-axis.z.toDouble(), axis.x.toDouble())))
        }

        private fun shipBlocks(level: ServerLevel, ship: ServerShip): Set<BlockPos>? {
            val box = ship.shipAABB ?: return null
            val blocks = HashSet<BlockPos>()
            val limit = EurekaConfig.SERVER.maxShipBlocks
            for (at in BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockState(at).isAir) continue
                blocks.add(at.immutable())
                if (limit > 0 && blocks.size > limit) return null
            }
            return blocks.ifEmpty { null }
        }

        private fun landing(at: BlockPos, anchor: BlockPos, target: BlockPos, rotation: Rotation): BlockPos =
            target.offset(at.subtract(anchor).rotate(rotation))

        private fun fits(level: ServerLevel, ship: ServerShip, blocks: Set<BlockPos>, anchor: BlockPos, target: BlockPos, rotation: Rotation): Boolean =
            blocks.all {
                val to = landing(it, anchor, target, rotation)
                ship.chunkClaim.contains(to.x shr 4, to.z shr 4) && level.getBlockState(to).isAir
            }

        private fun stepPole(level: ServerLevel, pos: BlockPos, state: BlockState, detacher: DetacherBlockEntity) {
            val ticks = level.gameTime - detacher.poleStart
            val outTicks = ceil(detacher.poleReach / POLE_SPEED).toLong()
            val grab = detacher.grab
            val ship = level.getShipManagingPos(pos)
            if (grab != null && ship != null && ticks >= outTicks) {
                val caught = level.shipObjectWorld.loadedShips.getById(grab.shipId)
                if (caught == null) {
                    detacher.grab = null
                } else {
                    if (grab.fromPos == null) {
                        grab.fromPos = ship.worldToShip.transformPosition(Vector3d(caught.transform.positionInWorld))
                        grab.fromRot = Quaterniond(ship.transform.shipToWorldRotation).conjugate().mul(caught.transform.shipToWorldRotation)
                    }
                    val pulled = ((ticks - outTicks).toDouble() / POLE_BACK_TICKS).coerceIn(0.0, 1.0)
                    val target = pos.relative(state.getValue(FACING))
                    pullIn(level, ship, caught, grab, target, pulled)
                    if (pulled >= 1.0) {
                        merge(level, ship, caught, grab, target)
                        detacher.stopPole()
                        return
                    }
                }
            }
            if (ticks >= outTicks + POLE_BACK_TICKS) {
                detacher.stopPole()
                return
            }
            level.scheduleTick(pos, state.block, 1)
        }

        // Moves the caught ship along with the pole coming back, ending exactly where its blocks will go
        private fun pullIn(level: ServerLevel, ship: ServerShip, caught: LoadedServerShip, grab: Grab, target: BlockPos, pulled: Double) {
            val end = Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5).add(
                grab.turn.transform(Vector3d(caught.transform.positionInShip).sub(grab.anchor.x + 0.5, grab.anchor.y + 0.5, grab.anchor.z + 0.5))
            )
            val posInShip = Vector3d(grab.fromPos!!).lerp(end, pulled)
            val rotInShip = Quaterniond(grab.fromRot!!).slerp(grab.turn, pulled)
            vsCore.teleportShip(
                level.shipObjectWorld as ServerShipWorld, caught,
                vsCore.newShipTeleportData(
                    newPos = ship.shipToWorld.transformPosition(posInShip),
                    newRot = Quaterniond(ship.transform.shipToWorldRotation).mul(rotInShip),
                    newVel = Vector3d(ship.velocity),
                    newOmega = Vector3d(ship.angularVelocity)
                )
            )
        }

        private fun merge(level: ServerLevel, ship: ServerShip, caught: ServerShip, grab: Grab, target: BlockPos) {
            val blocks = shipBlocks(level, caught)
            if (blocks == null || !fits(level, ship, blocks, grab.anchor, target, grab.rotation)) {
                level.playSound(null, target, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0f, 1.2f)
                return
            }
            // Nobody stays seated at a helm of a ship that's now part of another one, and its helms wait to be let go of
            val box = caught.shipAABB
            if (box != null) {
                val area = AABB(box.minX() - 1.0, box.minY() - 1.0, box.minZ() - 1.0, box.maxX() + 2.0, box.maxY() + 2.0, box.maxZ() + 2.0)
                for (seat in level.getEntitiesOfClass(ShipMountingEntity::class.java, area)) {
                    seat.ejectPassengers()
                    seat.discard()
                }
            }
            for (at in blocks) (level.getBlockEntity(at) as? ShipHelmBlockEntity)?.heldByDetacher = true
            val (min, max) = ShipAssembler.findMinAndMax(blocks)
            val moved = ShipAssembler.moveBlocksFromTo(
                level, blocks, caught, ship, min, max, Vector3i(target.x, target.y, target.z), true, grab.anchor to target, grab.rotation
            )
            if (moved.wasSuccessful) level.shipObjectWorld.deleteShip(caught)
            val face = ship.shipToWorld.transformPosition(Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5))
            level.playSound(null, face.x, face.y, face.z, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.0f, 0.8f)
            level.playSound(null, face.x, face.y, face.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f)
        }

        private class Push(val shipId: Long, val velocity: Vector3d, var waited: Int = 0)

        // Server thread only
        private val pushes = ArrayList<Push>()
        private const val PUSH_WAIT_TICKS = 100

        fun registerPushes() {
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register { server ->
                if (pushes.isEmpty()) return@register
                val shipWorld = server.shipObjectWorld
                pushes.removeIf { push ->
                    val ship = shipWorld.loadedShips.getById(push.shipId) ?: return@removeIf ++push.waited > PUSH_WAIT_TICKS
                    val transform = ship.transform
                    vsCore.teleportShip(
                        shipWorld as ServerShipWorld, ship,
                        vsCore.newShipTeleportData(
                            newPos = Vector3d(transform.positionInWorld),
                            newRot = Quaterniond(transform.shipToWorldRotation),
                            newVel = Vector3d(ship.velocity).add(push.velocity),
                            newOmega = Vector3d(ship.angularVelocity)
                        )
                    )
                    true
                }
            }
        }

        fun registerCrouchToOpen() {
            net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register { player, level, hand, hit ->
                if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !player.isSecondaryUseActive || player.isSpectator) {
                    return@register InteractionResult.PASS
                }
                val detacher = level.getBlockEntity(hit.blockPos) as? DetacherBlockEntity ?: return@register InteractionResult.PASS
                if (!level.isClientSide) player.openMenu(detacher)
                InteractionResult.SUCCESS
            }
        }

        /** Lets go of what's on the red face, if the detacher is all that holds it to the ship. */
        @JvmStatic
        fun release(level: ServerLevel, pos: BlockPos) {
            val state = level.getBlockState(pos)
            if (state.block !is DetacherBlock) return
            val facing = state.getValue(FACING)
            val ship = level.getShipManagingPos(pos)
            val held = ship?.let { heldBlocks(level, it, pos, facing) }
            if (ship == null || held == null) {
                level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0f, 1.2f)
                return
            }
            val push = (level.getBlockEntity(pos) as? DetacherBlockEntity)?.push ?: DetacherBlockEntity.DEFAULT_PUSH_HALF_BLOCKS / 2.0
            // Helms on it work again
            for (at in held) (level.getBlockEntity(at) as? ShipHelmBlockEntity)?.heldByDetacher = false
            val loose = ShipAssembler.assembleToShipFull(level, held).ship
            // The new ship only takes a push once it's loaded, a speed set before that gets lost
            val out = ship.shipToWorld.transformDirection(
                Vector3d(facing.stepX.toDouble(), facing.stepY.toDouble(), facing.stepZ.toDouble())
            ).normalize()
            pushes.add(Push(loose.id, out.mul(push * PUSH_SPEED_PER_BLOCK)))

            val face = ship.shipToWorld.transformPosition(
                Vector3d(pos.x + 0.5 + facing.stepX * 0.55, pos.y + 0.5 + facing.stepY * 0.55, pos.z + 0.5 + facing.stepZ * 0.55)
            )
            level.playSound(null, face.x, face.y, face.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.7f)
            level.playSound(null, face.x, face.y, face.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.6f)
            level.sendParticles(ParticleTypes.POOF, face.x, face.y, face.z, 12, 0.3, 0.3, 0.3, 0.05)
        }

        // Everything joined to the block on the red face, not going through the detacher
        private fun reachable(level: ServerLevel, ship: ServerShip, pos: BlockPos, facing: Direction): Set<BlockPos> {
            val start = pos.relative(facing)
            if (level.getBlockState(start).isAir) return emptySet()
            val found = hashSetOf(start)
            val queue = ArrayDeque(listOf(start))
            val limit = EurekaConfig.SERVER.maxShipBlocks
            while (queue.isNotEmpty() && (limit <= 0 || found.size <= limit)) {
                val at = queue.removeFirst()
                for (direction in Direction.entries) {
                    val next = at.relative(direction)
                    if (next == pos || next in found || level.getBlockState(next).isAir) continue
                    if (!ship.chunkClaim.contains(next.x shr 4, next.z shr 4)) continue
                    found.add(next)
                    queue.add(next)
                }
            }
            return found
        }

        /**
         * Everything that hangs on to the ship through the red face, or null when it's held on some other way too, or
         * there's nothing there.
         */
        private fun heldBlocks(level: ServerLevel, ship: ServerShip, pos: BlockPos, facing: Direction): Set<BlockPos>? {
            val start = pos.relative(facing)
            if (level.getBlockState(start).isAir) return null
            val rest = Direction.entries.filter { it != facing }.map { pos.relative(it) }
                .filter { !level.getBlockState(it).isAir }.toSet()
            val found = hashSetOf(start)
            val queue = ArrayDeque(listOf(start))
            val limit = EurekaConfig.SERVER.maxShipBlocks
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                for (direction in Direction.entries) {
                    val next = at.relative(direction)
                    if (next == pos || next in found || level.getBlockState(next).isAir) continue
                    if (!ship.chunkClaim.contains(next.x shr 4, next.z shr 4)) continue
                    // It reaches the rest of the ship another way, so the detacher isn't all that holds it
                    if (next in rest) return null
                    found.add(next)
                    queue.add(next)
                    if (limit > 0 && found.size > limit) return null
                }
            }
            return found
        }
    }
}
