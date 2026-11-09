package org.valkyrienskies.eureka.wiring

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * Something on a ship that a key can set off from a seat: a wireless button, or a cannon wired up straight to a seat.
 * It answers to [key] from its [contact], a helm or a cushion. A null contact means the helm.
 */
interface Wireable {
    var key: String?
    var contact: BlockPos?

    /** Shown as a cannon in the wiring screen, not as a button. */
    val isCannon: Boolean

    /** What happens when its key goes down on its contact. */
    fun fireFromKey(level: ServerLevel)
}
