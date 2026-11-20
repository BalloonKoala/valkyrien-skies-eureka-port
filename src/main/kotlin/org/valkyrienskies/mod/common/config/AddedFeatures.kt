package org.valkyrienskies.mod.common.config

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.properties.ReadOnlyProperty

/**
 * One switch for every block, item and feature this port added on top of Valkyrien Skies 2.4.11 and Eureka 1.6.4,
 * saved in `config/valkyrienskies/added-features.toml`. Everything is on by default. Turning something off brings back
 * how the original mods behaved, or for blocks and items: no recipe, not in the creative tab, can't be placed, and the
 * ones already built stop working (they can still be broken and picked up).
 *
 * The server's file decides. Players joining get the server's switches, see [Sync].
 */
object AddedFeatures {
    const val FILE = "valkyrienskies/added-features.toml"

    private val builder = ModConfigSpec.Builder()
    private val values = LinkedHashMap<String, ModConfigSpec.BooleanValue>()
    private var section: String? = null

    // The server's switches, while connected to one. Only the game's own thread uses them: in singleplayer the server
    // runs in the same program and reads the file, which can be newer than what it sent.
    @Volatile
    private var fromServer: Map<String, Boolean>? = null

    @Volatile
    private var clientThread: Thread? = null

    /** Called on the game's own thread at start up, on clients only. */
    @JvmStatic
    fun setClientThread(thread: Thread) {
        clientThread = thread
    }

    private fun switch(section: String, key: String, comment: String): ReadOnlyProperty<Any?, Boolean> {
        if (this.section != section) {
            if (this.section != null) builder.pop()
            builder.comment(SECTION_COMMENTS[section] ?: section).push(section)
            this.section = section
        }
        val name = "$section.$key"
        values[name] = builder.comment(comment).define(key, true)
        return ReadOnlyProperty { _, _ -> isOn(name) }
    }

    /** Whether the switch with this name, "section.key", is on. Unknown names count as on. */
    @JvmStatic
    fun isOn(name: String): Boolean {
        val synced = fromServer
        if (synced != null && Thread.currentThread() === clientThread) synced[name]?.let { return it }
        val value = values[name] ?: return true
        return try {
            value.get()
        } catch (e: IllegalStateException) {
            // Before the file is loaded
            true
        }
    }

    private val SECTION_COMMENTS = mapOf(
        "blocks" to "Blocks and items. Off: no recipe, not in the creative tab, can't be placed, and the ones already " +
            "built stop working. They can still be broken and picked up. Recipes change on /reload or a restart, " +
            "everything else right away.",
        "cannons" to "How cannons are used",
        "detacher" to "The Detacher",
        "helm" to "The ship helm and what happens when you assemble",
        "movement" to "How ships with a helm move. Off brings back how Eureka 1.6.4 did it",
        "world" to "Everything else"
    )

    // region Blocks and items
    @JvmStatic val cannon by switch("blocks", "cannon",
        "The Cannon, a block with a barrel you can aim in any direction")
    @JvmStatic val cannonballs by switch("blocks", "cannonballs",
        "Small, medium and large cannonballs. Off: cannons fire TNT instead, like before cannonballs")
    @JvmStatic val wirelessButtons by switch("blocks", "wirelessButtons",
        "Wireless Buttons in every wood type, pressed with a key from a seat")
    @JvmStatic val detacher by switch("blocks", "detacher",
        "The Detacher, which lets go of the part of a ship on its red face")
    @JvmStatic val electricWeight by switch("blocks", "electricWeight",
        "The Electric Weight, light as an oak log when off and heavy as netherite when on")
    // endregion

    // region Cannons
    @JvmStatic val cannonCrouchLeftClick by switch("cannons", "crouchLeftClickToAim",
        "Crouch and left click a cannon to aim it, whatever is in your hand. Crouch and right click with an empty hand " +
            "always works")
    @JvmStatic val cannonWireToSeats by switch("cannons", "wireToSeats",
        "Cannons can be wired to a helm or cushion in the Wiring screen and fired with a key, no Wireless Button needed")
    // endregion

    // region Detacher
    @JvmStatic val detacherGrab by switch("detacher", "grabMode",
        "A Detacher holding nothing shoots a pole and pulls another ship in as part of this one")
    // endregion

    // region Helm
    @JvmStatic val infoScreen by switch("helm", "infoScreenBeforeAssembling",
        "Assemble opens the Info screen with the block count and a 3D view first. Off: Assemble assembles right away")
    @JvmStatic val onlyPlayerPlacedBlocks by switch("helm", "onlyPlayerPlacedBlocks",
        "Only blocks players placed can become a ship, through the helm or the assembler items. Off: any block can, " +
            "ground and ores too")
    @JvmStatic val railsComeAlong by switch("helm", "railsComeAlong",
        "Rails, and stone, dirt and the like a player placed, come along when assembling. Off: the helm leaves those " +
            "blocks behind and rails on them break off")
    @JvmStatic val helmCrouchToOpen by switch("helm", "crouchToOpenWithItems",
        "Crouch and right click opens the helm whatever is in your hands")
    @JvmStatic val wiringScreen by switch("helm", "wiringScreen",
        "The Wiring screen in the helm, where keys are bound to Wireless Buttons, cannons and Detachers. Off: no keys " +
            "set anything off from a seat")
    @JvmStatic val cushionDriverSeat by switch("helm", "cushionDriverSeat",
        "A cushion in front of a helm is a driver's seat")
    @JvmStatic val pauseAllEngines by switch("helm", "pauseAllEnginesButton",
        "The Pause All button in the engine screen, which stops every engine on the ship")
    @JvmStatic val throttle by switch("helm", "throttle",
        "W and S set a throttle that stays where it is, shown above the XP bar. Off: hold W or S to move, and C turns " +
            "cruise control on and off, like Eureka 1.6.4")
    // endregion

    // region Movement
    @JvmStatic val engineSpeedByMass by switch("movement", "engineSpeedByMass",
        "Engine speed depends on the number of engines and the ship's mass, up to maxShipSpeed in vs_eureka.toml. " +
            "Off: Eureka 1.6.4's engine boost, about 12 blocks a second at most")
    @JvmStatic val turningNeedsEngines by switch("movement", "turningNeedsEngines",
        "Turning takes running engines, long ships turn slower, and turning stops quickly when the key is let go. " +
            "Off: Eureka 1.6.4's turning")
    @JvmStatic val highSpeedLevelling by switch("movement", "highSpeedLevelling",
        "Fast ships hold themselves level harder so they don't tip over, and levelling never turns a ship. Off: " +
            "Eureka 1.6.4's levelling")
    @JvmStatic val noBlockDragOnBigShips by switch("movement", "noBlockDragOnBigShips",
        "Ships with a helm heavier than noBlockDragAboveMass skip the block by block air drag, which made big ships " +
            "stutter. Off: every ship gets it")
    @JvmStatic val boatWaterContact by switch("movement", "boatWaterContact",
        "Boat engines measure the water under the ship themselves, so big boats get their push. Off: only what " +
            "Valkyrien Skies reports, which is almost nothing for big boats")
    // endregion

    // region World
    @JvmStatic val buildCommands by switch("world", "buildCommands",
        "The /vs build unassemble and /vs build delete commands. Needs /reload or a restart")
    @JvmStatic val elytraTakeoff by switch("world", "elytraTakeoffFromShips",
        "Players can start gliding with an elytra from a ship")
    @JvmStatic val noAiMobsRideShips by switch("world", "noAiMobsRideShips",
        "Mobs with no AI are carried along by ships instead of being left behind in the air")
    @JvmStatic val wholeShipLoading by switch("world", "wholeShipLoading",
        "A ship loads as a whole once any part of it is near a player, so ships longer than about 256 blocks don't " +
            "freeze. Off: each part loads by its own distance")
    @JvmStatic val groundLoadedUnderShips by switch("world", "keepGroundLoadedUnderShips",
        "The ground and water under every loaded ship stay loaded, also where no player is near")
    @JvmStatic val terrainOnlyNearShips by switch("world", "physicsTerrainOnlyNearShips",
        "Physics only gets the ground near ships, which saves a lot of work. Off: every loaded chunk, like Valkyrien " +
            "Skies 2.4.11")
    @JvmStatic val welcomeMessage by switch("world", "welcomeMessage",
        "The welcome message with credits when a player joins")
    @JvmStatic val paperWorldImport by switch("world", "paperWorldImport",
        "Worlds made by Paper are converted when they're opened")
    // endregion

    @JvmField
    val SPEC: ModConfigSpec = run {
        builder.pop()
        builder.build()
    }

    /** Every switch and its value, in the order of the file. */
    @JvmStatic
    fun all(): Map<String, Boolean> = values.keys.associateWith { isOn(it) }

    /** The local file's values, what a server sends its players. */
    @JvmStatic
    fun local(): Map<String, Boolean> = values.mapValues { (_, v) ->
        try { v.get() } catch (e: IllegalStateException) { true }
    }

    /** Changes a switch in this program only, the file stays as it is. For tests. */
    @JvmStatic
    fun setLocal(name: String, on: Boolean) {
        values[name]?.set(on) ?: throw IllegalArgumentException("No switch $name")
    }

    @JvmStatic
    fun setFromServer(switches: Map<String, Boolean>?) {
        fromServer = switches
    }

    /**
     * `"fabric:load_conditions": [{"condition": "valkyrienskies:added_feature", "feature": "blocks.cannon"}]` drops a
     * recipe or advancement while that switch is off. Datapacks load after the file, a change needs /reload.
     */
    class FeatureCondition(val feature: String) : ResourceCondition {
        override fun getType(): ResourceConditionType<*> = CONDITION
        override fun test(registryInfo: RegistryOps.RegistryInfoLookup?): Boolean = local()[feature] ?: true
    }

    val CONDITION: ResourceConditionType<FeatureCondition> = ResourceConditionType.create(
        Identifier.fromNamespaceAndPath("valkyrienskies", "added_feature"),
        RecordCodecBuilder.mapCodec<FeatureCondition> { instance ->
            instance.group(com.mojang.serialization.Codec.STRING.fieldOf("feature").forGetter { it.feature })
                .apply(instance, ::FeatureCondition)
        } as MapCodec<FeatureCondition>
    )

    fun registerCondition() {
        ResourceConditions.register(CONDITION)
    }

    /** The server's switches, sent to every player while they join and again when the file changes. */
    class Sync(val switches: Map<String, Boolean>) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<Sync> = TYPE

        companion object {
            @JvmField
            val TYPE = CustomPacketPayload.Type<Sync>(Identifier.fromNamespaceAndPath("valkyrienskies", "added_features"))

            @JvmField
            val CODEC: StreamCodec<FriendlyByteBuf, Sync> = StreamCodec.of(
                { buf, payload ->
                    buf.writeVarInt(payload.switches.size)
                    payload.switches.forEach { (k, v) -> buf.writeUtf(k); buf.writeBoolean(v) }
                },
                { buf ->
                    val map = LinkedHashMap<String, Boolean>()
                    repeat(buf.readVarInt()) { map[buf.readUtf()] = buf.readBoolean() }
                    Sync(map)
                }
            )
        }
    }
}
