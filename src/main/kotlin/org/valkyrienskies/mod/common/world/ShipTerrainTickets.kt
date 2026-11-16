package org.valkyrienskies.mod.common.world

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps the world under and around every loaded ship loaded for its physics, also where no player is near: the far end
 * of a long ship is often past every player's view, and there it had no ground to hit and no water to float on. The
 * chunks only load, they don't tick, and the tickets run out by themselves once the ship has moved on.
 */
object ShipTerrainTickets {
    private const val REFRESH_TICKS = 20
    private const val TIMEOUT_TICKS = 60L
    private const val MARGIN_CHUNKS = 1
    // Where the ship gets to in this long, so its terrain is ready before it arrives
    private const val LOOKAHEAD_SECONDS = 1.0

    private lateinit var TYPE: TicketType

    fun register() {
        TYPE = Registry.register(
            BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath("valkyrienskies", "ship_terrain"),
            TicketType(TIMEOUT_TICKS, TicketType.FLAG_LOADING)
        )
    }

    @JvmStatic
    fun tick(level: ServerLevel, shipWorld: VsiServerShipWorld, dimension: String) {
        if (level.gameTime % REFRESH_TICKS != 0L) return
        for (ship in shipWorld.loadedShips) {
            if (dimension != ship.chunkClaimDimension) continue
            val box = ship.worldAABB
            val velocity = ship.velocity
            val aheadX = velocity.x() * LOOKAHEAD_SECONDS
            val aheadZ = velocity.z() * LOOKAHEAD_SECONDS
            val minX = (floor(min(box.minX(), box.minX() + aheadX)).toInt() shr 4) - MARGIN_CHUNKS
            val minZ = (floor(min(box.minZ(), box.minZ() + aheadZ)).toInt() shr 4) - MARGIN_CHUNKS
            val maxX = (floor(max(box.maxX(), box.maxX() + aheadX)).toInt() shr 4) + MARGIN_CHUNKS
            val maxZ = (floor(max(box.maxZ(), box.maxZ() + aheadZ)).toInt() shr 4) + MARGIN_CHUNKS
            for (x in minX..maxX) for (z in minZ..maxZ) level.chunkSource.addTicketWithRadius(TYPE, ChunkPos(x, z), 0)
        }
    }
}
