package org.valkyrienskies.eureka.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.valkyrienskies.eureka.EurekaBlockEntities
import org.valkyrienskies.eureka.block.DetacherBlock
import org.valkyrienskies.eureka.gui.detacher.DetacherMenu
import org.valkyrienskies.eureka.wiring.Wireable

class DetacherBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(EurekaBlockEntities.DETACHER.get(), pos, state), MenuProvider, Wireable {

    /** How hard it pushes off what it lets go of, in half blocks: from half a block to 6 blocks. */
    var pushHalfBlocks = DEFAULT_PUSH_HALF_BLOCKS
        set(value) {
            field = value.coerceIn(MIN_PUSH_HALF_BLOCKS, MAX_PUSH_HALF_BLOCKS)
            setChanged()
        }

    val push: Double get() = pushHalfBlocks / 2.0

    /** The key that sets it off from its seat, like a wireless button but without one. */
    override var key: String? = null
        set(value) {
            field = value
            setChanged()
        }

    // Relative to the detacher, so it stays right when the ship is taken apart and put back together. Null is the helm.
    private var contactOffset: BlockPos? = null

    override var contact: BlockPos?
        get() = contactOffset?.let { blockPos.offset(it) }
        set(value) {
            contactOffset = value?.subtract(blockPos)
            setChanged()
        }

    override val isCannon get() = false

    override val isDetacher get() = true

    override fun fireFromKey(level: ServerLevel) {
        level.scheduleTick(blockPos, blockState.block, DetacherBlock.RELEASE_DELAY_TICKS)
    }

    /**
     * The pole, for drawing it: the game time it started going out, and how far it reaches before it comes back. A
     * start below 0 means it's in. Clients get these through the update tag.
     */
    var poleStart = -1L
        private set
    var poleReach = 0f
        private set

    // What the pole is grabbing while it's out, server only
    internal var grab: DetacherBlock.Companion.Grab? = null

    val poleOut: Boolean get() = poleStart >= 0

    fun startPole(gameTime: Long, reach: Float) {
        poleStart = gameTime
        poleReach = reach
        sync()
    }

    fun stopPole() {
        poleStart = -1L
        poleReach = 0f
        grab = null
        sync()
    }

    private fun sync() {
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, net.minecraft.world.level.block.Block.UPDATE_CLIENTS)
    }

    // Clients only need the pole to draw it
    override fun getUpdateTag(registries: net.minecraft.core.HolderLookup.Provider): net.minecraft.nbt.CompoundTag =
        net.minecraft.nbt.CompoundTag().apply {
            putLong(POLE_START_TAG, poleStart)
            putFloat(POLE_REACH_TAG, poleReach)
        }

    override fun getUpdatePacket(): net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> =
        net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this)

    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        DetacherMenu(containerId, inventory, this)

    override fun getDisplayName(): Component = Component.translatable("block.vs_eureka.detacher")

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putInt(PUSH_TAG, pushHalfBlocks)
        key?.let { output.putString(KEY_TAG, it) }
        contactOffset?.let { output.putLong(CONTACT_TAG, it.asLong()) }
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        pushHalfBlocks = input.getIntOr(PUSH_TAG, DEFAULT_PUSH_HALF_BLOCKS)
        key = input.getString(KEY_TAG).orElse(null)
        contactOffset = input.getLong(CONTACT_TAG).map(BlockPos::of).orElse(null)
        poleStart = input.getLongOr(POLE_START_TAG, -1L)
        poleReach = input.getFloatOr(POLE_REACH_TAG, 0f)
    }

    companion object {
        const val MIN_PUSH_HALF_BLOCKS = 1
        const val MAX_PUSH_HALF_BLOCKS = 12
        const val DEFAULT_PUSH_HALF_BLOCKS = 4

        private const val PUSH_TAG = "Push"
        private const val KEY_TAG = "Key"
        private const val CONTACT_TAG = "Contact"
        private const val POLE_START_TAG = "PoleStart"
        private const val POLE_REACH_TAG = "PoleReach"
    }
}
