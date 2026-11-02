package org.valkyrienskies.mod.common

import com.mojang.serialization.Codec
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import org.valkyrienskies.core.internal.world.VsiPipeline

/**
 * This class saves/loads ship data for a world.
 *
 * This is only a temporary solution, and should be replaced eventually because it is very inefficient.
 */
class ShipSavedData : SavedData() {

    companion object {
        const val SAVED_DATA_ID = "vs_ship_data"
        private const val QUERYABLE_SHIP_DATA_NBT_KEY = "queryable_ship_data"
        private const val CHUNK_ALLOCATOR_NBT_KEY = "chunk_allocator"
        private const val PIPELINE_NBT_KEY = "vs_pipeline"

        private val CODEC: Codec<ShipSavedData> = CompoundTag.CODEC.xmap({ load(it) }, { it.saveToTag() })

        @JvmField
        val TYPE: SavedDataType<ShipSavedData> =
            SavedDataType(Identifier.fromNamespaceAndPath("valkyrienskies", SAVED_DATA_ID), { createEmpty() }, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE)

        fun createEmpty(): ShipSavedData {
            return ShipSavedData().apply { pipeline = vsCore.newPipeline() }
        }

        @JvmStatic
        fun load(compoundTag: CompoundTag): ShipSavedData {
            val data = ShipSavedData()

            // Read bytes from the [CompoundTag]
            val queryableShipDataAsBytes = compoundTag.getByteArray(QUERYABLE_SHIP_DATA_NBT_KEY).orElse(ByteArray(0))
            val chunkAllocatorAsBytes = compoundTag.getByteArray(CHUNK_ALLOCATOR_NBT_KEY).orElse(ByteArray(0))
            val pipelineAsBytes = compoundTag.getByteArray(PIPELINE_NBT_KEY).orElse(ByteArray(0))

            try {
                if (pipelineAsBytes.isNotEmpty()) {
                    data.pipeline = vsCore.newPipeline(pipelineAsBytes)
                } else if (queryableShipDataAsBytes.isNotEmpty() && chunkAllocatorAsBytes.isNotEmpty()) {
                    data.pipeline = vsCore.newPipelineLegacyData(queryableShipDataAsBytes, chunkAllocatorAsBytes)
                } else {
                    throw IllegalStateException("Couldn't find serialized ship data")
                }
            } catch (ex: Exception) {
                data.loadingException = ex
            }
            return data
        }
    }

    lateinit var pipeline: VsiPipeline

    var loadingException: Throwable? = null
        private set

    private fun saveToTag(): CompoundTag {
        val compoundTag = CompoundTag()
        compoundTag.putByteArray(PIPELINE_NBT_KEY, vsCore.serializePipeline(pipeline))

        return compoundTag
    }

    /**
     * This is not efficient, but it will work for now.
     */
    override fun isDirty(): Boolean {
        return true
    }
}
