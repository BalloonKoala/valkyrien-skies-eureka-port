package org.valkyrienskies.mod.common.util

// Used on Level to get a Level to remove a chunk from its tracking
interface VSServerLevel {
    fun removeChunk(chunkX: Int, chunkZ: Int)

    /** True when physics has this chunk's terrain, it only gets the terrain near ships. */
    fun isChunkKnownToPhysics(chunkX: Int, chunkZ: Int): Boolean
}

// Used on LevelChunk to delete all blocks and block entities in a LevelChunk
interface VSLevelChunk {
    fun clearChunk()

    fun copyChunkFromOtherDimension(srcChunk: VSLevelChunk)
}
