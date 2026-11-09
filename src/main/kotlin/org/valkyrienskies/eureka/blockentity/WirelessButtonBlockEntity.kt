package org.valkyrienskies.eureka.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.eureka.EurekaBlockEntities
import org.valkyrienskies.eureka.block.WirelessButtonBlock
import org.valkyrienskies.eureka.wiring.Wireable

class WirelessButtonBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(EurekaBlockEntities.WIRELESS_BUTTON.get(), pos, state), Wireable {

    /** The name of the key that pulses this button, like key.keyboard.g. */
    override var key: String? = null
        set(value) {
            field = value
            setChanged()
        }

    // Relative to this button, so it stays right when the ship is taken apart and put back together. Null is the helm.
    private var contactOffset: BlockPos? = null

    override var contact: BlockPos?
        get() = contactOffset?.let { blockPos.offset(it) }
        set(value) {
            contactOffset = value?.subtract(blockPos)
            setChanged()
        }

    override val isCannon get() = false

    override fun fireFromKey(level: ServerLevel) {
        (blockState.block as? WirelessButtonBlock)?.pulse(blockState, level, blockPos)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        key?.let { output.putString(KEY_TAG, it) }
        contactOffset?.let { output.putLong(CONTACT_TAG, it.asLong()) }
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        key = input.getString(KEY_TAG).orElse(null)
        contactOffset = input.getLong(CONTACT_TAG).map(BlockPos::of).orElse(null)
    }

    private companion object {
        const val KEY_TAG = "Key"
        const val CONTACT_TAG = "Contact"
    }
}
