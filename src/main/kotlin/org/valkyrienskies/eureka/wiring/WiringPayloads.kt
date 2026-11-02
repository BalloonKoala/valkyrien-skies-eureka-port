package org.valkyrienskies.eureka.wiring

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.valkyrienskies.eureka.EurekaMod

private fun <T : CustomPacketPayload> type(name: String) =
    CustomPacketPayload.Type<T>(Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, name))

private fun <T> FriendlyByteBuf.readItems(read: (FriendlyByteBuf) -> T): List<T> = List(readVarInt()) { read(this) }

private fun <T> FriendlyByteBuf.writeItems(items: List<T>, write: (FriendlyByteBuf, T) -> Unit) {
    writeVarInt(items.size)
    items.forEach { write(this, it) }
}

/** [key] is empty when nothing is bound, [contact] is null when the button still goes to the helm. */
data class WiringButton(val pos: BlockPos, val key: String, val contact: BlockPos?)

data class WiringContact(val pos: BlockPos, val isHelm: Boolean)

/** Everything the wiring screen shows, sent when it opens and after every change. */
data class WiringDataPayload(val helm: BlockPos, val buttons: List<WiringButton>, val contacts: List<WiringContact>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<WiringDataPayload>("wiring_data")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WiringDataPayload> = CustomPacketPayload.codec(
            { payload, buf ->
                buf.writeBlockPos(payload.helm)
                buf.writeItems(payload.buttons) { out, button ->
                    out.writeBlockPos(button.pos)
                    out.writeUtf(button.key)
                    out.writeBoolean(button.contact != null)
                    button.contact?.let { out.writeBlockPos(it) }
                }
                buf.writeItems(payload.contacts) { out, contact ->
                    out.writeBlockPos(contact.pos)
                    out.writeBoolean(contact.isHelm)
                }
            },
            { buf ->
                WiringDataPayload(
                    buf.readBlockPos(),
                    buf.readItems { WiringButton(it.readBlockPos(), it.readUtf(), if (it.readBoolean()) it.readBlockPos() else null) },
                    buf.readItems { WiringContact(it.readBlockPos(), it.readBoolean()) }
                )
            }
        )
    }
}

/** A key went down while the player is sitting on a ship. */
data class WiringKeyPayload(val key: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<WiringKeyPayload>("wiring_key")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WiringKeyPayload> = CustomPacketPayload.codec(
            { payload, buf -> buf.writeUtf(payload.key) }, { buf -> WiringKeyPayload(buf.readUtf()) }
        )
    }
}

data class WiringBindPayload(val helm: BlockPos, val button: BlockPos, val key: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<WiringBindPayload>("wiring_bind")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WiringBindPayload> = CustomPacketPayload.codec(
            { payload, buf ->
                buf.writeBlockPos(payload.helm)
                buf.writeBlockPos(payload.button)
                buf.writeUtf(payload.key)
            },
            { buf -> WiringBindPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf()) }
        )
    }
}

data class WiringConnectPayload(val helm: BlockPos, val button: BlockPos, val contact: BlockPos) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<WiringConnectPayload>("wiring_connect")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WiringConnectPayload> = CustomPacketPayload.codec(
            { payload, buf ->
                buf.writeBlockPos(payload.helm)
                buf.writeBlockPos(payload.button)
                buf.writeBlockPos(payload.contact)
            },
            { buf -> WiringConnectPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos()) }
        )
    }
}
