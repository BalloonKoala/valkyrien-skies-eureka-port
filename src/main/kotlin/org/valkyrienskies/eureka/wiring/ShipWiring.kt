package org.valkyrienskies.eureka.wiring

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.decoration.Cushion
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.getLoadedShipManagingPos

/**
 * The server side of wireless buttons and wired cannons. They go to the helm until they get wired to another contact
 * point, which is a helm or a cushion on the same ship. Pressing the key while sitting on the contact point pulses the
 * button or fires the cannon.
 */
object ShipWiring {
    // Far enough for any spot in front of a helm, an edit from further away isn't from the helm screen
    private const val MAX_EDIT_DISTANCE_SQ = 32.0 * 32.0
    private const val MAX_KEY_NAME_LENGTH = 64

    fun register() {
        PayloadTypeRegistry.clientboundPlay().register(WiringDataPayload.TYPE, WiringDataPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(WiringKeyPayload.TYPE, WiringKeyPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(WiringBindPayload.TYPE, WiringBindPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(WiringConnectPayload.TYPE, WiringConnectPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(WiringKeyPayload.TYPE) { payload, context ->
            context.server().execute { onKeyPressed(context.player(), payload.key) }
        }
        ServerPlayNetworking.registerGlobalReceiver(WiringBindPayload.TYPE) { payload, context ->
            context.server().execute { bindKey(context.player(), payload) }
        }
        ServerPlayNetworking.registerGlobalReceiver(WiringConnectPayload.TYPE) { payload, context ->
            context.server().execute { connect(context.player(), payload) }
        }
    }

    private fun blockEntitiesOn(level: ServerLevel, ship: LoadedServerShip): List<BlockEntity> {
        val box = ship.shipAABB ?: return emptyList()
        val found = ArrayList<BlockEntity>()
        for (chunkX in (box.minX() shr 4)..(box.maxX() shr 4)) {
            for (chunkZ in (box.minZ() shr 4)..(box.maxZ() shr 4)) {
                level.chunkSource.getChunkNow(chunkX, chunkZ)?.let { found.addAll(it.blockEntities.values) }
            }
        }
        return found
    }

    private fun cushionsOn(level: ServerLevel, ship: LoadedServerShip): List<Cushion> {
        val box = ship.shipAABB ?: return emptyList()
        val area = AABB(
            box.minX().toDouble(), box.minY().toDouble(), box.minZ().toDouble(),
            box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0
        )
        return level.getEntitiesOfClass(Cushion::class.java, area)
    }

    private fun contactsOn(level: ServerLevel, ship: LoadedServerShip): List<WiringContact> =
        blockEntitiesOn(level, ship).filterIsInstance<ShipHelmBlockEntity>().map { WiringContact(it.blockPos, true) } +
            cushionsOn(level, ship).map { WiringContact(it.blockPosition(), false) }

    /** Everything on the ship a key can set off: wireless buttons and cannons. */
    private fun wireablesOn(level: ServerLevel, ship: LoadedServerShip): List<BlockEntity> =
        blockEntitiesOn(level, ship).filter { it is Wireable }

    /** The contact point of this button or cannon that really exists, null means the helm. */
    private fun liveContact(button: Wireable, contacts: List<WiringContact>): BlockPos? =
        button.contact?.takeIf { pos -> contacts.any { it.pos == pos } }

    fun sendTo(player: ServerPlayer, helm: BlockPos) {
        val level = player.level()
        val ship = level.getLoadedShipManagingPos(helm) as? LoadedServerShip ?: return
        val contacts = contactsOn(level, ship)
        val buttons = wireablesOn(level, ship).map {
            val wired = it as Wireable
            WiringButton(it.blockPos, wired.key.orEmpty(), liveContact(wired, contacts), wired.isCannon)
        }
        ServerPlayNetworking.send(player, WiringDataPayload(helm, buttons, contacts))
    }

    // Only someone standing at the helm of the ship can change it
    private fun shipBeingEdited(player: ServerPlayer, helm: BlockPos): LoadedServerShip? {
        val level = player.level()
        if (level.getBlockEntity(helm) !is ShipHelmBlockEntity) return null
        val ship = level.getLoadedShipManagingPos(helm) as? LoadedServerShip ?: return null
        val inWorld = ship.shipToWorld.transformPosition(Vector3d(helm.x + 0.5, helm.y + 0.5, helm.z + 0.5))
        return ship.takeIf { player.distanceToSqr(inWorld.x, inWorld.y, inWorld.z) < MAX_EDIT_DISTANCE_SQ }
    }

    private fun button(level: ServerLevel, ship: LoadedServerShip, pos: BlockPos): Wireable? =
        (level.getBlockEntity(pos) as? Wireable)
            ?.takeIf { level.getLoadedShipManagingPos(pos)?.id == ship.id }

    private fun bindKey(player: ServerPlayer, payload: WiringBindPayload) {
        val ship = shipBeingEdited(player, payload.helm) ?: return
        if (payload.key.length > MAX_KEY_NAME_LENGTH) return
        button(player.level(), ship, payload.button)?.key = payload.key
        sendTo(player, payload.helm)
    }

    private fun connect(player: ServerPlayer, payload: WiringConnectPayload) {
        val ship = shipBeingEdited(player, payload.helm) ?: return
        val level = player.level()
        // A contact point has to be on this ship
        if (contactsOn(level, ship).any { it.pos == payload.contact }) {
            button(level, ship, payload.button)?.contact = payload.contact
        }
        sendTo(player, payload.helm)
    }

    /** The contact points this player is sitting on. A cushion in front of a helm counts as both. */
    private fun seatedOn(player: ServerPlayer, level: ServerLevel, ship: LoadedServerShip): Set<BlockPos> {
        val vehicle = player.vehicle
        if (vehicle is Cushion) return setOf(vehicle.blockPosition())
        if (vehicle !is ShipMountingEntity) return emptySet()

        val seated = HashSet<BlockPos>()
        for (helm in blockEntitiesOn(level, ship).filterIsInstance<ShipHelmBlockEntity>()) {
            if (helm.hasSeat(vehicle)) {
                seated.add(helm.blockPos)
                val seat = helm.blockPos.relative(helm.blockState.getValue(HorizontalDirectionalBlock.FACING))
                if (level.getEntitiesOfClass(Cushion::class.java, AABB(seat)).isNotEmpty()) seated.add(seat)
            }
        }
        return seated
    }

    private fun onKeyPressed(player: ServerPlayer, key: String) {
        val vehicle = player.vehicle ?: return
        val level = player.level()
        val ship = level.getLoadedShipManagingPos(vehicle.blockPosition()) as? LoadedServerShip ?: return
        val seated = seatedOn(player, level, ship)
        if (seated.isEmpty()) return

        val contacts = contactsOn(level, ship)
        val atHelm = contacts.any { it.isHelm && it.pos in seated }
        for (entity in wireablesOn(level, ship)) {
            val wired = entity as Wireable
            if (wired.key != key) continue
            val contact = liveContact(wired, contacts)
            if (if (contact == null) atHelm else contact in seated) wired.fireFromKey(level)
        }
    }
}
