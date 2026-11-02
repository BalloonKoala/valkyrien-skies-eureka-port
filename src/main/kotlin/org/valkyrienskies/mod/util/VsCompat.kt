package org.valkyrienskies.mod.util

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * Small helpers standing in for APIs that were removed after 1.20.1, so the calling code can keep
 * the same shape and behaviour it had in Valkyrien Skies 2.4.11.
 */

/** The old `ItemStack.tag`. Item NBT lives in the custom data component now. */
val ItemStack.vsTag: CompoundTag?
    get() = get(DataComponents.CUSTOM_DATA)?.copyTag()

fun ItemStack.vsEditTag(edit: (CompoundTag) -> Unit) {
    CustomData.update(DataComponents.CUSTOM_DATA, this) { edit(it) }
}

/** The old `Player.sendSystemMessage`, which worked on both logical sides. */
fun Player.vsSendMessage(message: Component) {
    sendSystemMessage(message)
}

/** The old numeric `CommandSourceStack.hasPermission(level)`. */
fun CommandSourceStack.vsHasPermission(level: Int): Boolean =
    permissions().hasPermission(Permission.HasCommandLevel(PermissionLevel.byId(level)))

/** The old numeric `Player.hasPermissions(level)`. */
fun Player.vsHasPermissions(level: Int): Boolean =
    permissions().hasPermission(Permission.HasCommandLevel(PermissionLevel.byId(level)))

fun CompoundTag.vsInput(provider: HolderLookup.Provider): ValueInput =
    TagValueInput.create(ProblemReporter.DISCARDING, provider, this)

fun vsOutput(provider: HolderLookup.Provider): TagValueOutput =
    TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider)

fun BlockEntity.vsLoad(tag: CompoundTag, provider: HolderLookup.Provider) {
    loadWithComponents(tag.vsInput(provider))
}

fun BlockEntity.vsSaveWithId(provider: HolderLookup.Provider): CompoundTag {
    val output = vsOutput(provider)
    saveWithId(output)
    return output.buildResult()
}

fun Entity.vsSaveWithoutId(provider: HolderLookup.Provider): CompoundTag {
    val output = vsOutput(provider)
    saveWithoutId(output)
    return output.buildResult()
}

fun ValueOutput.vsPutNbt(key: String, tag: CompoundTag) {
    store(key, CompoundTag.CODEC, tag)
}

fun ValueInput.vsReadNbt(key: String): CompoundTag? =
    read(key, CompoundTag.CODEC).orElse(null)

/** Blocks need their registry id before they are constructed now. */
fun vsBlockProperties(name: String): BlockBehaviour.Properties =
    BlockBehaviour.Properties.of().setId(
        ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("valkyrienskies", name))
    )
