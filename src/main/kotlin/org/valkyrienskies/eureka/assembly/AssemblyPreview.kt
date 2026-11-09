package org.valkyrienskies.eureka.assembly

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.EurekaMod
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity
import org.valkyrienskies.eureka.util.ShipAssembler
import org.valkyrienskies.mod.common.getShipManagingPos

/**
 * Everything that would become the ship, sent to the player before anything gets assembled. [blocks] are packed
 * block positions in the world. When [complete] is false the search stopped after [limit] blocks.
 */
class AssemblyPreviewPayload(val helm: BlockPos, val blocks: LongArray, val complete: Boolean, val limit: Int) :
    CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AssemblyPreviewPayload>(
            Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "assembly_preview")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AssemblyPreviewPayload> = CustomPacketPayload.codec(
            { payload, buf ->
                buf.writeBlockPos(payload.helm)
                buf.writeLongArray(payload.blocks)
                buf.writeBoolean(payload.complete)
                buf.writeVarInt(payload.limit)
            },
            { buf -> AssemblyPreviewPayload(buf.readBlockPos(), buf.readLongArray(), buf.readBoolean(), buf.readVarInt()) }
        )
    }
}

/** The player looked at the preview and pressed Assemble. */
class AssemblyConfirmPayload(val helm: BlockPos) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AssemblyConfirmPayload>(
            Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "assembly_confirm")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AssemblyConfirmPayload> = CustomPacketPayload.codec(
            { payload, buf -> buf.writeBlockPos(payload.helm) },
            { buf -> AssemblyConfirmPayload(buf.readBlockPos()) }
        )
    }
}

/**
 * Assembling takes two steps. The helm's Assemble button only shows what would become the ship, and the Assemble
 * button under that preview really assembles it.
 */
object AssemblyPreview {
    // A preview never gets bigger than this, even when the config allows ships of any size, so it fits in one packet
    private const val MAX_PREVIEW_BLOCKS = 65536
    private const val MAX_CONFIRM_DISTANCE_SQ = 16.0 * 16.0

    fun register() {
        PayloadTypeRegistry.clientboundPlay().register(AssemblyPreviewPayload.TYPE, AssemblyPreviewPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(AssemblyConfirmPayload.TYPE, AssemblyConfirmPayload.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(AssemblyConfirmPayload.TYPE) { payload, context ->
            context.server().execute { confirm(context.player(), payload.helm) }
        }
    }

    private fun limit(): Int {
        val configured = EurekaConfig.SERVER.maxShipBlocks
        return if (configured in 1..MAX_PREVIEW_BLOCKS) configured else MAX_PREVIEW_BLOCKS
    }

    // Searching tens of thousands of blocks takes a moment, clicking Assemble over and over mustn't lag the server
    private const val COOLDOWN_TICKS = 10L
    private val lastRequest = java.util.WeakHashMap<ServerPlayer, Long>()

    fun sendTo(player: ServerPlayer, helm: ShipHelmBlockEntity) {
        val level = player.level()
        if (helm.assembled || level.getShipManagingPos(helm.blockPos) != null) return
        val now = level.gameTime
        val last = lastRequest[player]
        if (last != null && now - last in 0 until COOLDOWN_TICKS) return
        lastRequest[player] = now
        val limit = limit()
        val (blocks, complete) = ShipAssembler.findBlocks(level, helm.blockPos, limit, helm::canAssemble)
        val packed = LongArray(blocks.size)
        var i = 0
        for (pos in blocks) packed[i++] = pos.asLong()
        ServerPlayNetworking.send(player, AssemblyPreviewPayload(helm.blockPos, packed, complete, limit))
    }

    private fun confirm(player: ServerPlayer, helmPos: BlockPos) {
        val level = player.level()
        val helm = level.getBlockEntity(helmPos) as? ShipHelmBlockEntity ?: return
        if (helm.assembled || level.getShipManagingPos(helmPos) != null) return
        if (player.distanceToSqr(helmPos.x + 0.5, helmPos.y + 0.5, helmPos.z + 0.5) > MAX_CONFIRM_DISTANCE_SQ) return
        helm.assemble(player)
    }
}
