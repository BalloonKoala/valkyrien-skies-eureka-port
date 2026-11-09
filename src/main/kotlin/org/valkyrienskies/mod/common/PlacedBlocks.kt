package org.valkyrienskies.mod.common

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.LevelChunk
import java.util.stream.LongStream

/**
 * Remembers which blocks players placed, so blocks the world generated can't end up in a ship.
 *
 * Every chunk keeps the positions players placed blocks at. Chunks generated after this was added are strict: any
 * block there that no player placed counts as world generated. Chunks from before can't know who placed what, so there
 * only blocks in the `valkyrienskies:world_generated` tag count, like ores, which nobody builds boats from.
 */
object PlacedBlocks {
    class ChunkPlaced(val strict: Boolean, val positions: LongOpenHashSet)

    private val CODEC: Codec<ChunkPlaced> = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.BOOL.fieldOf("strict").forGetter { it.strict },
            Codec.LONG_STREAM.fieldOf("placed").forGetter { LongStream.of(*it.positions.toLongArray()) }
        ).apply(instance) { strict, placed -> ChunkPlaced(strict, LongOpenHashSet(placed.toArray())) }
    }

    @JvmField
    val WORLD_GENERATED: TagKey<Block> =
        TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("valkyrienskies", "world_generated"))

    private lateinit var TYPE: AttachmentType<ChunkPlaced>

    // Above 0 while a player is placing a block, everything that turns up then is theirs
    private val placing = ThreadLocal.withInitial { 0 }

    fun register() {
        TYPE = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("valkyrienskies", "placed_blocks")) {
            it.persistent(CODEC)
        }
        ServerChunkEvents.CHUNK_GENERATE.register { _, chunk ->
            (chunk as AttachmentTarget).setAttached(TYPE, ChunkPlaced(true, LongOpenHashSet()))
        }
    }

    @JvmStatic
    fun startPlayerPlacing() {
        placing.set(placing.get() + 1)
    }

    @JvmStatic
    fun stopPlayerPlacing() {
        placing.set(maxOf(placing.get() - 1, 0))
    }

    private fun data(chunk: ChunkAccess): ChunkPlaced? = (chunk as AttachmentTarget).getAttached(TYPE)

    private fun dataOrCreate(chunk: ChunkAccess): ChunkPlaced {
        data(chunk)?.let { return it }
        // A chunk from before placed blocks were kept track of
        val created = ChunkPlaced(false, LongOpenHashSet())
        (chunk as AttachmentTarget).setAttached(TYPE, created)
        return created
    }

    /** Called for every block that changes in a loaded chunk on the server. */
    @JvmStatic
    fun onBlockChanged(chunk: LevelChunk, pos: BlockPos, oldState: BlockState, newState: BlockState) {
        if (newState.isAir || newState.block is LiquidBlock) {
            val data = data(chunk) ?: return
            if (data.positions.remove(pos.asLong())) chunk.markUnsaved()
            return
        }
        // A new block, not a fence or stairs next to it changing shape
        if (placing.get() > 0 && oldState.block != newState.block) {
            if (dataOrCreate(chunk).positions.add(pos.asLong())) chunk.markUnsaved()
        }
    }

    /** Counts this block as placed by a player, for blocks that come back out of a ship. */
    fun markPlaced(level: ServerLevel, pos: BlockPos) {
        val chunk = level.getChunkAt(pos)
        if (dataOrCreate(chunk).positions.add(pos.asLong())) chunk.markUnsaved()
    }

    fun isPlaced(level: ServerLevel, pos: BlockPos): Boolean =
        data(level.getChunkAt(pos))?.positions?.contains(pos.asLong()) == true

    /**
     * How many of these blocks no player placed. Blocks already on a ship passed this when the ship was made, they're
     * left out.
     */
    fun countWorldGenerated(level: ServerLevel, blocks: Iterable<BlockPos>): Int {
        var count = 0
        for (pos in blocks) {
            if (level.isBlockInShipyard(pos)) continue
            val state = level.getBlockState(pos)
            if (!state.isAir && isWorldGenerated(level, pos, state)) count++
        }
        return count
    }

    /** True for a block no player placed. Those can't become part of a ship. */
    fun isWorldGenerated(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        val data = data(level.getChunkAt(pos))
        if (data != null && data.positions.contains(pos.asLong())) return false
        return data?.strict == true || state.`is`(WORLD_GENERATED)
    }
}
