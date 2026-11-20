package org.valkyrienskies.mod.common.world

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiChunkUnwatchTask
import org.valkyrienskies.core.internal.world.chunks.VsiChunkWatchTask
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
import org.valkyrienskies.mod.util.logger
import java.util.UUID

object ChunkManagement {
    /**
     * VS core watches every ship chunk by its own distance to a player, so on a ship longer than about 256 blocks the
     * far end never loaded and the whole ship stayed frozen, since a ship only moves with all of it loaded. Here a ship
     * loads as a whole, and goes to a player as a whole, as soon as core watches any chunk of it for that player.
     */
    private class WholeShip(val dimension: String) {
        // Per player, the chunks of this ship core itself watches for them
        val watchedByCore = HashMap<UUID, LongOpenHashSet>()
        // Per player, every chunk of this ship they have been sent
        val sentTo = HashMap<UUID, LongOpenHashSet>()
        val forced = LongOpenHashSet()
    }

    private val wholeShips = HashMap<Long, WholeShip>()

    @JvmStatic
    fun tickChunkLoading(shipWorld: VsiServerShipWorld, server: MinecraftServer) {
        val (chunkWatchTasks, chunkUnwatchTasks) = shipWorld.getChunkWatchTasks()
        // Turned off in added-features.toml, each chunk of a ship loads by its own distance like Valkyrien Skies 2.4.11
        val wholeOn = org.valkyrienskies.mod.common.config.AddedFeatures.wholeShipLoading

        // for now, just do all the watch tasks

        chunkWatchTasks.forEach { chunkWatchTask: VsiChunkWatchTask ->
            logger.debug(
                "Watch task for dimension " + chunkWatchTask.dimensionId + ": " +
                    chunkWatchTask.chunkX + " : " + chunkWatchTask.chunkZ
            )

            val chunkPos = ChunkPos(chunkWatchTask.chunkX, chunkWatchTask.chunkZ)

            val level = server.getLevelFromDimensionId(chunkWatchTask.dimensionId)!!
            level.chunkSource.updateChunkForced(chunkPos, true)

            val whole = if (!wholeOn) null else shipWorld.allShips.getByChunkPos(chunkPos.x, chunkPos.z, chunkWatchTask.dimensionId)?.let { ship ->
                wholeShips.getOrPut(ship.id) { WholeShip(chunkWatchTask.dimensionId) }
            }
            for (player in chunkWatchTask.playersNeedWatching) {
                val serverPlayer = (player as MinecraftPlayer).playerEntityReference.get() as ServerPlayer? ?: continue
                whole?.watchedByCore?.getOrPut(serverPlayer.uuid) { LongOpenHashSet() }?.add(chunkPos.pack())
                whole?.sentTo?.getOrPut(serverPlayer.uuid) { LongOpenHashSet() }?.add(chunkPos.pack())
            }

            level.server.executeIf({ level.isTickingChunk(chunkPos) }) {
                for (player in chunkWatchTask.playersNeedWatching) {
                    val minecraftPlayer = player as MinecraftPlayer
                    val serverPlayer = minecraftPlayer.playerEntityReference.get() as ServerPlayer?
                    if (serverPlayer != null) {
                        if (chunkWatchTask.dimensionId != player.dimension) {
                            logger.warn("Player received watch task for chunk in dimension that they are not also in!")
                        }
                        val map = level.chunkSource.chunkMap as ChunkMapAccessor
                        map.callMarkChunkPendingToSend(serverPlayer, chunkPos)
                    }
                }
            }
        }

        chunkUnwatchTasks.forEach { chunkUnwatchTask: VsiChunkUnwatchTask ->
            logger.debug(
                "Unwatch task for dimension " + chunkUnwatchTask.dimensionId + ": " +
                    chunkUnwatchTask.chunkX + " : " + chunkUnwatchTask.chunkZ
            )
            val chunkPos = ChunkPos(chunkUnwatchTask.chunkX, chunkUnwatchTask.chunkZ)
            val ship = shipWorld.allShips.getByChunkPos(chunkPos.x, chunkPos.z, chunkUnwatchTask.dimensionId)
            val whole = if (!wholeOn) null else ship?.let { wholeShips[it.id] }

            for (player in chunkUnwatchTask.playersNeedUnwatching) {
                val serverPlayer = player.mcPlayer as ServerPlayer
                val watched = whole?.watchedByCore?.get(serverPlayer.uuid)
                watched?.remove(chunkPos.pack())
                // Still near another part of the same ship, so it keeps all of it
                if (watched != null && watched.isNotEmpty()) continue
                serverPlayer.connection.chunkSender.dropChunk(serverPlayer, chunkPos)
                if (whole != null) dropShipFor(whole, serverPlayer)
            }

            if (chunkUnwatchTask.shouldUnload) {
                if (whole == null || whole.watchedByCore.isEmpty()) {
                    val level = server.getLevelFromDimensionId(chunkUnwatchTask.dimensionId)!!
                    level.chunkSource.updateChunkForced(chunkPos, false)
                } else {
                    // Stays loaded with the rest of the ship, and unloads with it
                    whole.forced.add(chunkPos.pack())
                }
            }
        }

        shipWorld.setExecutedChunkWatchTasks(chunkWatchTasks, chunkUnwatchTasks)

        if (wholeOn) keepShipsWhole(shipWorld, server) else letGoOfWholeShips(server)
    }

    // Switched off while running: chunks only kept for a whole ship go back to loading by their own distance
    private fun letGoOfWholeShips(server: MinecraftServer) {
        if (wholeShips.isEmpty()) return
        for (whole in wholeShips.values) {
            val level = server.getLevelFromDimensionId(whole.dimension) ?: continue
            val watched = LongOpenHashSet()
            whole.watchedByCore.values.forEach { watched.addAll(it) }
            val iterator = whole.forced.iterator()
            while (iterator.hasNext()) {
                val key = iterator.nextLong()
                if (!watched.contains(key)) level.chunkSource.updateChunkForced(ChunkPos.unpack(key), false)
            }
        }
        wholeShips.clear()
    }

    // Stops sending a ship to a player that has gone away from all of it
    private fun dropShipFor(whole: WholeShip, player: ServerPlayer) {
        whole.watchedByCore.remove(player.uuid)
        val sent = whole.sentTo.remove(player.uuid) ?: return
        val iterator = sent.iterator()
        while (iterator.hasNext()) player.connection.chunkSender.dropChunk(player, ChunkPos.unpack(iterator.nextLong()))
    }

    private fun keepShipsWhole(shipWorld: VsiServerShipWorld, server: MinecraftServer) {
        val iterator = wholeShips.entries.iterator()
        while (iterator.hasNext()) {
            val (shipId, whole) = iterator.next()
            val level = server.getLevelFromDimensionId(whole.dimension)
            val ship = shipWorld.allShips.getById(shipId)
            // Players that left or went to another dimension stop counting, core watches them again if they come back
            whole.watchedByCore.keys.removeIf { uuid ->
                val player = server.playerList.getPlayer(uuid)
                val gone = player == null || level == null || player.level() !== level || ship == null
                if (gone) whole.sentTo.remove(uuid)
                gone
            }
            whole.watchedByCore.values.removeIf { it.isEmpty() }
            if (ship == null || level == null || whole.watchedByCore.isEmpty()) {
                if (level != null) unforceAll(level, whole)
                iterator.remove()
                continue
            }
            ship.activeChunksSet.forEach { chunkX, chunkZ ->
                val key = ChunkPos.pack(chunkX, chunkZ)
                val chunkPos = ChunkPos(chunkX, chunkZ)
                if (whole.forced.add(key)) level.chunkSource.updateChunkForced(chunkPos, true)
                for (uuid in whole.watchedByCore.keys) {
                    val sent = whole.sentTo.getOrPut(uuid) { LongOpenHashSet() }
                    if (!sent.add(key)) continue
                    level.server.executeIf({ level.isTickingChunk(chunkPos) }) {
                        val player = server.playerList.getPlayer(uuid) ?: return@executeIf
                        (level.chunkSource.chunkMap as ChunkMapAccessor).callMarkChunkPendingToSend(player, chunkPos)
                    }
                }
            }
        }
    }

    private fun unforceAll(level: ServerLevel, whole: WholeShip) {
        val iterator = whole.forced.iterator()
        while (iterator.hasNext()) level.chunkSource.updateChunkForced(ChunkPos.unpack(iterator.nextLong()), false)
        whole.forced.clear()
    }

    private val logger by logger()
}
