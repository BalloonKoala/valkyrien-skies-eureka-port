package org.valkyrienskies.mod.dev

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.core.api.attachment.removeAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.EurekaItems
import org.valkyrienskies.eureka.block.WirelessButtonBlock
import org.valkyrienskies.eureka.blockentity.CannonBlockEntity
import org.valkyrienskies.eureka.blockentity.DetacherBlockEntity
import org.valkyrienskies.eureka.blockentity.EngineBlockEntity
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity
import org.valkyrienskies.eureka.entity.Cannonball
import org.valkyrienskies.eureka.ship.EurekaShipControl
import org.valkyrienskies.eureka.util.ShipAssembler
import org.valkyrienskies.eureka.util.TurnedOff
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.PlacedBlocks
import org.valkyrienskies.mod.common.config.AddedFeatures
import org.valkyrienskies.mod.common.config.AddedFeaturesSetup
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.VSServerLevel
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.server.level.DistanceManagerAccessor
import java.nio.file.Files
import kotlin.math.hypot

/**
 * Turns every switch in added-features.toml off and on again and checks what it does, with real clicks and keys
 * where a player would use it. `VS_TEST_CONFIG=1`. Every check logs "config OK" or "config FAIL".
 */
object DevConfigTest {
    private fun log(s: String) = println("SELFTEST $s")
    private fun block(id: String): Block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))

    private var failures = 0
    private var checks = 0
    private val failed = ArrayList<String>()

    private fun check(name: String, ok: Boolean, detail: String) {
        checks++
        if (!ok) { failures++; failed.add(name) }
        log("config ${if (ok) "OK  " else "FAIL"} $name: $detail")
    }

    // Shots out of cannons, and what they were
    @Volatile private var shots = 0
    @Volatile private var lastShot = ""
    @Volatile private var welcomes = 0
    private var listening = false

    private const val GROUND = -60 // first air above the superflat ground

    private fun set(context: ClientGameTestContext, server: TestServerContext, name: String, on: Boolean) {
        server.runOnServer<RuntimeException> { s ->
            AddedFeatures.setLocal(name, on)
            AddedFeaturesSetup.sendToAll(s)
        }
        context.waitTicks(4)
    }

    private fun reload(context: ClientGameTestContext, server: TestServerContext) {
        server.runCommand("reload")
        context.waitTicks(60)
    }

    private fun look(context: ClientGameTestContext, x: Double, y: Double, z: Double) {
        context.runOnClient<RuntimeException> { mc ->
            val eye = mc.player!!.getEyePosition(1f)
            val dx = x - eye.x; val dy = y - eye.y; val dz = z - eye.z
            mc.player!!.yRot = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
            mc.player!!.xRot = Math.toDegrees(-Math.atan2(dy, hypot(dx, dz))).toFloat()
        }
        context.waitTicks(3)
    }

    private fun stand(server: TestServerContext, x: Double, y: Double, z: Double, item: ItemStack = ItemStack.EMPTY) {
        server.runOnServer<RuntimeException> { s ->
            val p = s.playerList.players[0]
            p.stopRiding()
            p.abilities.flying = false
            p.onUpdateAbilities()
            p.setItemInHand(InteractionHand.MAIN_HAND, item)
            p.teleportTo(s.overworld(), x, y, z, setOf(), 0f, 0f, true)
        }
    }

    private fun recipe(server: TestServerContext, id: String): Boolean = server.computeOnServer<Boolean, RuntimeException> { s ->
        s.recipeManager.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent
    }

    private fun clientItemOn(context: ClientGameTestContext, id: String): Boolean = context.computeOnClient<Boolean, RuntimeException> {
        TurnedOff.itemOn(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)))
    }

    // Only what the test built, never the ground or water it stands in, or the search takes the whole world
    private val GROUND_BLOCKS = setOf(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.BEDROCK, Blocks.STONE)
    private val OURS: (net.minecraft.world.level.block.state.BlockState) -> Boolean =
        { !it.isAir && it.fluidState.isEmpty && it.block !in GROUND_BLOCKS }

    private fun put(level: ServerLevel, pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
        level.setBlock(pos, state, 3)
        PlacedBlocks.markPlaced(level, pos)
    }

    private fun shipPos(level: ServerLevel, ship: ServerShip, world: BlockPos): BlockPos {
        val v = ship.worldToShip.transformPosition(Vector3d(world.x + 0.5, world.y + 0.5, world.z + 0.5))
        return BlockPos.containing(v.x, v.y, v.z)
    }

    private fun loaded(level: ServerLevel, ship: ServerShip) = level.shipObjectWorld.loadedShips.getById(ship.id) as LoadedServerShip

    private fun <T> field(target: Any, name: String): T {
        val f = target.javaClass.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return f.get(target) as T
    }

    fun run(context: ClientGameTestContext) {
        if (!listening) {
            listening = true
            ServerEntityEvents.ENTITY_LOAD.register(ServerEntityEvents.Load { entity, _ ->
                if (entity is Cannonball) {
                    shots++
                    lastShot = BuiltInRegistries.ITEM.getKey(entity.item.item).path
                    entity.discard()
                }
            })
            ClientReceiveMessageEvents.GAME.register(ClientReceiveMessageEvents.Game { message, _ ->
                if ((message.contents as? TranslatableContents)?.key == "info.vs_eureka.welcome.hello") welcomes++
            })
        }
        context.worldBuilder().create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("gamerule tntExplodes false")
            server.runCommand("gamerule doMobSpawning false")
            server.runCommand("time set noon")
            context.runOnClient<RuntimeException> { mc -> mc.options.renderDistance().set(8) }
            server.runOnServer<RuntimeException> { s -> s.playerList.players[0].setGameMode(GameType.SURVIVAL) }
            stand(server, 0.5, GROUND.toDouble(), 0.5)
            context.waitTicks(100)
            log("config file " + FabricLoader.getInstance().configDir.resolve(AddedFeatures.FILE) +
                " switches=${AddedFeatures.all().size} all on=${AddedFeatures.all().values.all { it }}")

            val from = System.getenv("VS_TEST_CONFIG_FROM") ?: "blocks"
            val sections = listOf("blocks", "cannons", "detacher", "helm", "movement", "world", "file")
            fun runs(section: String) = sections.indexOf(section) >= sections.indexOf(from)
            if (runs("blocks")) blocks(context, server)
            if (runs("cannons")) cannons(context, server)
            if (runs("detacher")) detacher(context, server)
            if (runs("helm")) helm(context, server)
            if (runs("movement")) movement(context, server)
            if (runs("world")) world(context, server)
            if (runs("file")) fileEdit(context, server)
        }
        log("config test finished: $checks checks, $failures failed ${if (failed.isEmpty()) "" else failed.toString()}")
    }

    // region Blocks
    private fun placeWithClick(context: ClientGameTestContext, server: TestServerContext, item: String, x: Int): String {
        val spot = BlockPos(x, GROUND, 3)
        server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(spot, Blocks.AIR.defaultBlockState(), 3) }
        stand(server, x + 0.5, GROUND.toDouble(), 0.5, ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)), 4))
        context.waitTicks(10)
        look(context, x + 0.5, GROUND.toDouble(), 3.5)
        context.input.pressKey { it.keyUse }
        context.waitTicks(6)
        return server.computeOnServer<String, RuntimeException> { s ->
            val placed = s.overworld().getBlockState(spot)
            s.overworld().setBlock(spot, Blocks.AIR.defaultBlockState(), 3)
            BuiltInRegistries.BLOCK.getKey(placed.block).path
        }
    }

    private fun blocks(context: ClientGameTestContext, server: TestServerContext) {
        val cases = listOf(
            Triple("blocks.cannon", "vs_eureka:cannon", "vs_eureka:cannon"),
            Triple("blocks.wirelessButtons", "vs_eureka:oak_wireless_button", "vs_eureka:oak_wireless_button"),
            Triple("blocks.detacher", "vs_eureka:detacher", "vs_eureka:detacher"),
            Triple("blocks.electricWeight", "vs_eureka:electric_weight", "vs_eureka:electric_weight")
        )
        for ((i, case) in cases.withIndex()) {
            val (name, item, recipeId) = case
            val x = i * 4
            for (on in listOf(false, true)) {
                set(context, server, name, on)
                reload(context, server)
                val placed = placeWithClick(context, server, item, x)
                val wanted = item.substringAfter(':')
                check("$name=$on placing", (placed == wanted) == on, "a real right click placed $placed")
                check("$name=$on recipe", recipe(server, recipeId) == on, "recipe ${if (recipe(server, recipeId)) "there" else "gone"} after /reload")
                check("$name=$on creative tab and JEI", clientItemOn(context, item) == on, "item shown=${clientItemOn(context, item)}")
            }
        }

        // Cannons that are turned off don't fire, and fire TNT while cannonballs are turned off
        val cannonPos = BlockPos(20, GROUND, 6)
        fun fire(ammo: ItemStack): Int {
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                level.setBlock(cannonPos.above(), Blocks.AIR.defaultBlockState(), 3)
                level.setBlock(cannonPos, block("vs_eureka:cannon").defaultBlockState(), 3)
                (level.getBlockEntity(cannonPos) as CannonBlockEntity).setItem(0, ammo)
                shots = 0
                level.setBlock(cannonPos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3)
            }
            context.waitTicks(12)
            server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(cannonPos.above(), Blocks.AIR.defaultBlockState(), 3) }
            return shots
        }
        val small = { ItemStack(EurekaItems.SMALL_CANNONBALL.get(), 4) }
        set(context, server, "blocks.cannon", false)
        check("blocks.cannon=false firing", fire(small()) == 0, "a powered cannon fired $shots")
        set(context, server, "blocks.cannon", true)
        check("blocks.cannon=true firing", fire(small()) == 1 && lastShot == "small_cannonball", "fired $shots, $lastShot")

        set(context, server, "blocks.cannonballs", false)
        reload(context, server)
        val takesTnt = server.computeOnServer<Boolean, RuntimeException> { s ->
            (s.overworld().getBlockEntity(cannonPos) as CannonBlockEntity).canPlaceItem(0, ItemStack(Items.TNT))
        }
        check("blocks.cannonballs=false loading", takesTnt, "a cannon takes TNT: $takesTnt")
        check("blocks.cannonballs=false firing", fire(ItemStack(Items.TNT, 4)) == 1 && lastShot == "tnt", "fired $shots, $lastShot")
        check("blocks.cannonballs=false recipes", !recipe(server, "vs_eureka:small_cannonball") && !recipe(server, "vs_eureka:large_cannonball"),
            "small there=${recipe(server, "vs_eureka:small_cannonball")}")
        check("blocks.cannonballs=false creative tab", !clientItemOn(context, "vs_eureka:medium_cannonball"), "")
        set(context, server, "blocks.cannonballs", true)
        reload(context, server)
        val refusesTnt = server.computeOnServer<Boolean, RuntimeException> { s ->
            !(s.overworld().getBlockEntity(cannonPos) as CannonBlockEntity).canPlaceItem(0, ItemStack(Items.TNT))
        }
        check("blocks.cannonballs=true loading", refusesTnt, "a cannon refuses TNT: $refusesTnt")
        check("blocks.cannonballs=true firing", fire(ItemStack(Items.TNT, 4)) == 0 && fire(small()) == 1, "TNT doesn't fire, cannonballs do")
        check("blocks.cannonballs=true recipes", recipe(server, "vs_eureka:small_cannonball") && recipe(server, "vs_eureka:medium_cannonball"), "")

        // A key fires a wireless button through pulse, turned off it stays up
        val buttonPos = BlockPos(24, GROUND, 6)
        for (on in listOf(false, true)) {
            set(context, server, "blocks.wirelessButtons", on)
            val pressed = server.computeOnServer<Boolean, RuntimeException> { s ->
                val level = s.overworld()
                level.setBlock(buttonPos, block("vs_eureka:oak_wireless_button").defaultBlockState(), 3)
                val state = level.getBlockState(buttonPos)
                (state.block as WirelessButtonBlock).pulse(state, level, buttonPos)
                level.getBlockState(buttonPos).getValue(BlockStateProperties.POWERED)
            }
            check("blocks.wirelessButtons=$on pressing", pressed == on, "pressed=$pressed")
            context.waitTicks(10)
        }

        // An Electric Weight flips on a redstone pulse
        val weightPos = BlockPos(28, GROUND, 6)
        for (on in listOf(false, true)) {
            set(context, server, "blocks.electricWeight", on)
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                level.setBlock(weightPos.above(), Blocks.AIR.defaultBlockState(), 3)
                level.setBlock(weightPos, block("vs_eureka:electric_weight").defaultBlockState(), 3)
            }
            context.waitTicks(2)
            server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(weightPos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3) }
            context.waitTicks(4)
            val lit = server.computeOnServer<Boolean, RuntimeException> { s -> s.overworld().getBlockState(weightPos).getValue(BlockStateProperties.LIT) }
            check("blocks.electricWeight=$on switching", lit == on, "lit after a pulse=$lit")
        }
    }
    // endregion

    // region Cannons
    private fun cannons(context: ClientGameTestContext, server: TestServerContext) {
        val cannonPos = BlockPos(32, GROUND, 4)
        server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(cannonPos, block("vs_eureka:cannon").defaultBlockState(), 3) }
        for (on in listOf(false, true)) {
            set(context, server, "cannons.crouchLeftClickToAim", on)
            stand(server, 32.5, GROUND.toDouble(), 1.5, ItemStack(Items.COBBLESTONE, 8))
            context.waitTicks(10)
            look(context, 32.5, GROUND + 0.5, 4.5)
            context.input.holdKey { it.keyShift }
            context.waitTicks(4)
            context.input.pressKey { it.keyAttack }
            context.waitTicks(4)
            val aiming = server.computeOnServer<Boolean, RuntimeException> { s -> (s.overworld().getBlockEntity(cannonPos) as? CannonBlockEntity)?.isBeingAimed == true }
            val still = server.computeOnServer<String, RuntimeException> { s -> BuiltInRegistries.BLOCK.getKey(s.overworld().getBlockState(cannonPos).block).path }
            context.input.releaseKey { it.keyShift }
            context.waitTicks(15)
            check("cannons.crouchLeftClickToAim=$on", aiming == on && still == "cannon", "crouch and left click with cobblestone: aiming=$aiming, block=$still")
        }

        // A cannon wired to the helm fires from a key, with the Wiring screen on
        val rig = wiredShip(context, server)
        fun pressG(): Int {
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                (level.getBlockEntity(rig.cannon) as CannonBlockEntity).setItem(0, ItemStack(EurekaItems.SMALL_CANNONBALL.get(), 4))
                shots = 0
            }
            context.input.pressKey(InputConstants.getKey("key.keyboard.g"))
            context.waitTicks(12)
            return shots
        }
        set(context, server, "cannons.wireToSeats", false)
        check("cannons.wireToSeats=false", pressG() == 0, "G at the helm fired $shots")
        set(context, server, "cannons.wireToSeats", true)
        check("cannons.wireToSeats=true", pressG() == 1, "G at the helm fired $shots")
        set(context, server, "helm.wiringScreen", false)
        check("helm.wiringScreen=false keys", pressG() == 0, "G at the helm fired $shots")
        set(context, server, "helm.wiringScreen", true)
        check("helm.wiringScreen=true keys", pressG() == 1, "G at the helm fired $shots")
        server.runOnServer<RuntimeException> { s -> s.playerList.players[0].stopRiding() }
        context.waitTicks(10)
    }

    class WiredShip(val ship: ServerShip, val helm: BlockPos, val cannon: BlockPos)

    // A raft resting on the ground with a helm and a cannon wired to it, the player at the wheel
    private fun wiredShip(context: ClientGameTestContext, server: TestServerContext): WiredShip {
        lateinit var rig: WiredShip
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val o = BlockPos(40, GROUND + 1, 20)
            for (x in 0 until 5) for (z in 0 until 5) put(level, o.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState())
            val helm = o.offset(2, 1, 3)
            put(level, helm, block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
            val cannon = o.offset(2, 1, 0)
            put(level, cannon, block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH))
            val ship = ShipAssembler.collectBlocks(level, helm, OURS)!!
            rig = WiredShip(ship, shipPos(level, ship, helm), shipPos(level, ship, cannon))
        }
        context.waitTicks(40)
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val p = s.playerList.players[0]
            val world = rig.ship.shipToWorld.transformPosition(Vector3d(rig.helm.x + 0.5, rig.helm.y + 1.0, rig.helm.z + 1.5))
            p.teleportTo(level, world.x, world.y, world.z, setOf(), 0f, 0f, true)
            val cannon = level.getBlockEntity(rig.cannon) as CannonBlockEntity
            cannon.key = "key.keyboard.g"
            cannon.contact = null
            log("config wired ship helm=${level.getBlockEntity(rig.helm)?.javaClass?.simpleName} cannon=${cannon.javaClass.simpleName}")
        }
        context.waitTicks(20)
        server.runOnServer<RuntimeException> { s ->
            val helm = s.overworld().getBlockEntity(rig.helm) as ShipHelmBlockEntity
            log("config seated at helm ${helm.sit(s.playerList.players[0], true)}")
        }
        context.waitTicks(20)
        return rig
    }
    // endregion

    // region Detacher
    private fun detacher(context: ClientGameTestContext, server: TestServerContext) {
        lateinit var ship: ServerShip
        lateinit var detacherPos: BlockPos
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val o = BlockPos(60, GROUND, 20)
            for (x in 0 until 3) for (z in 0 until 3) put(level, o.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState())
            put(level, o.offset(3, 0, 1), block("vs_eureka:detacher").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST))
            put(level, o.offset(4, 0, 1), Blocks.OAK_PLANKS.defaultBlockState())
            ship = ShipAssembler.collectBlocks(level, o.offset(1, 0, 1), OURS)!!
            detacherPos = shipPos(level, ship, o.offset(3, 0, 1))
        }
        stand(server, 62.5, GROUND.toDouble(), 17.5)
        context.waitTicks(40)
        fun trigger() {
            server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(detacherPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3) }
            context.waitTicks(8)
            server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(detacherPos.below(), Blocks.AIR.defaultBlockState(), 3) }
            context.waitTicks(4)
        }
        fun ships() = server.computeOnServer<Int, RuntimeException> { s -> s.overworld().shipObjectWorld.allShips.size }
        fun holding() = server.computeOnServer<Boolean, RuntimeException> { s -> !s.overworld().getBlockState(detacherPos.east()).isAir }

        set(context, server, "blocks.detacher", false)
        var before = ships()
        trigger()
        check("blocks.detacher=false letting go", ships() == before && holding(), "ships $before -> ${ships()}, still holding=${holding()}")
        set(context, server, "blocks.detacher", true)
        before = ships()
        trigger()
        check("blocks.detacher=true letting go", ships() == before + 1 && !holding(), "ships $before -> ${ships()}")
        context.waitTicks(20)

        // Nothing on the red face now: grab mode shoots the pole
        for (on in listOf(false, true)) {
            set(context, server, "detacher.grabMode", on)
            server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(detacherPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3) }
            var out = false
            for (t in 1..8) {
                context.waitTicks(1)
                out = out || server.computeOnServer<Boolean, RuntimeException> { s -> (s.overworld().getBlockEntity(detacherPos) as DetacherBlockEntity).poleOut }
            }
            server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(detacherPos.below(), Blocks.AIR.defaultBlockState(), 3) }
            context.waitTicks(30)
            check("detacher.grabMode=$on", out == on, "pole went out=$out")
        }
    }
    // endregion

    // region Helm
    // A deck in the air, made of blocks a player placed, with a helm facing north
    private fun deck(level: ServerLevel, o: BlockPos): BlockPos {
        for (x in 0 until 5) for (z in 0 until 5) put(level, o.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState())
        put(level, o.offset(2, -1, 2), block("vs_eureka:balloon").defaultBlockState())
        val helm = o.offset(2, 1, 2)
        put(level, helm, block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
        return helm
    }

    private fun openHelm(context: ClientGameTestContext, server: TestServerContext, helm: BlockPos, item: ItemStack): String {
        stand(server, helm.x + 0.5, helm.y.toDouble(), helm.z + 1.7, item)
        context.waitTicks(15)
        look(context, helm.x + 0.5, helm.y + 0.3, helm.z + 0.5)
        context.input.holdKey { it.keyShift }
        context.waitTicks(3)
        context.input.pressKey { it.keyUse }
        context.waitTicks(8)
        context.input.releaseKey { it.keyShift }
        context.waitTicks(4)
        return context.computeOnClient<String, RuntimeException> { it.gui.screen()?.javaClass?.simpleName ?: "none" }
    }

    private fun closeScreen(context: ClientGameTestContext) {
        context.runOnClient<RuntimeException> { mc -> if (mc.gui.screen() != null) mc.player?.closeContainer(); mc.gui.setScreen(null) }
        context.waitTicks(5)
    }

    private fun helm(context: ClientGameTestContext, server: TestServerContext) {
        fun ships() = server.computeOnServer<Int, RuntimeException> { s -> s.overworld().shipObjectWorld.allShips.size }

        // Crouching with something in hand opens the helm
        val helmA = server.computeOnServer<BlockPos, RuntimeException> { s -> deck(s.overworld(), BlockPos(0, GROUND + 10, 40)) }
        for (on in listOf(false, true)) {
            set(context, server, "helm.crouchToOpenWithItems", on)
            val screen = openHelm(context, server, helmA, ItemStack(Items.COBBLESTONE, 8))
            check("helm.crouchToOpenWithItems=$on", (screen == "ShipHelmScreen") == on, "crouch and right click holding cobblestone opened $screen")
            closeScreen(context)
            server.runOnServer<RuntimeException> { s -> s.overworld().setBlock(helmA.above(), Blocks.AIR.defaultBlockState(), 3) }
        }

        // The Info screen comes first, or Assemble assembles right away
        for (on in listOf(true, false)) {
            set(context, server, "helm.infoScreenBeforeAssembling", on)
            val screen = openHelm(context, server, helmA, ItemStack.EMPTY)
            val before = ships()
            context.clickScreenButton("gui.vs_eureka.assemble")
            context.waitTicks(20)
            val after = context.computeOnClient<String, RuntimeException> { it.gui.screen()?.javaClass?.simpleName ?: "none" }
            check("helm.infoScreenBeforeAssembling=$on", if (on) after == "ShipAssemblyScreen" && ships() == before else ships() == before + 1,
                "opened $screen, after Assemble: $after, ships $before -> ${ships()}")
            closeScreen(context)
        }

        // Only player placed blocks, and rails with the stone under them
        val helmB = server.computeOnServer<BlockPos, RuntimeException> { s ->
            val level = s.overworld()
            val o = BlockPos(20, GROUND + 10, 40)
            val helm = deck(level, o)
            level.setBlock(o.offset(5, 0, 2), Blocks.COAL_ORE.defaultBlockState(), 3) // nobody placed this
            // Placed stone in the deck with a rail on it
            put(level, o.offset(1, 0, 0), Blocks.STONE.defaultBlockState())
            put(level, o.offset(1, 1, 0), Blocks.RAIL.defaultBlockState())
            helm
        }
        for (on in listOf(false, true)) {
            set(context, server, "helm.onlyPlayerPlacedBlocks", on)
            val red = server.computeOnServer<Int, RuntimeException> { s ->
                val level = s.overworld()
                val helm = level.getBlockEntity(helmB) as ShipHelmBlockEntity
                ShipAssembler.findBlocks(level, helmB, 0, helm::canAssemble).worldGenerated.size
            }
            check("helm.onlyPlayerPlacedBlocks=$on", (red > 0) == on, "blocks no player placed in the way: $red")
        }
        for (on in listOf(false, true)) {
            set(context, server, "helm.railsComeAlong", on)
            val taken = server.computeOnServer<String, RuntimeException> { s ->
                val level = s.overworld()
                val helm = level.getBlockEntity(helmB) as ShipHelmBlockEntity
                val found = ShipAssembler.findBlocks(level, helmB, 0, helm::canAssemble).blocks
                "stone=${BlockPos(21, GROUND + 10, 40) in found} rail=${BlockPos(21, GROUND + 11, 40) in found}"
            }
            check("helm.railsComeAlong=$on", taken == if (on) "stone=true rail=true" else "stone=false rail=true", taken)
        }

        // A cushion in front of the helm is a driver's seat
        lateinit var seatShip: ServerShip
        lateinit var seatHelm: BlockPos
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val o = BlockPos(40, GROUND + 1, 40)
            val helm = deck(level, o)
            seatShip = ShipAssembler.collectBlocks(level, helm, OURS)!!
            seatHelm = shipPos(level, seatShip, helm)
        }
        stand(server, 42.5, GROUND.toDouble(), 37.5)
        context.waitTicks(40)
        for (on in listOf(false, true)) {
            set(context, server, "helm.cushionDriverSeat", on)
            val driving = server.computeOnServer<Boolean, RuntimeException> { s ->
                val level = s.overworld()
                val p = s.playerList.players[0]
                val seat = seatHelm.north()
                level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Cushion::class.java, net.minecraft.world.phys.AABB(seat)).forEach { it.discard() }
                DevSelfTest.placeCushion(level, seat)
                val cushion = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Cushion::class.java, net.minecraft.world.phys.AABB(seat)).first()
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                cushion.interact(p, InteractionHand.MAIN_HAND, Vec3.atCenterOf(seat))
                p.vehicle is org.valkyrienskies.mod.common.entity.ShipMountingEntity
            }
            context.waitTicks(10)
            val controls = server.computeOnServer<Boolean, RuntimeException> { s ->
                loaded(s.overworld(), seatShip).getAttachment(SeatedControllingPlayer::class.java) != null
            }
            check("helm.cushionDriverSeat=$on", driving == on && controls == on, "sitting on the cushion takes the wheel=$driving, drives=$controls")
            server.runOnServer<RuntimeException> { s -> s.playerList.players[0].stopRiding() }
            context.waitTicks(10)
        }

        // Pause All on the engine screen
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            level.setBlock(seatHelm.east(), block("vs_eureka:engine").defaultBlockState(), 3)
        }
        context.waitTicks(5)
        for (on in listOf(false, true)) {
            set(context, server, "helm.pauseAllEnginesButton", on)
            val paused = server.computeOnServer<Boolean, RuntimeException> { s ->
                val level = s.overworld()
                val control = loaded(level, seatShip).getAttachment(EurekaShipControl::class.java)!!
                control.enginesPaused = false
                (level.getBlockEntity(seatHelm.east()) as EngineBlockEntity).toggleEnginesPaused(s.playerList.players[0])
                control.enginesPaused.also { control.enginesPaused = false }
            }
            check("helm.pauseAllEnginesButton=$on", paused == on, "Pause All paused the engines=$paused")
        }

        // The Wiring button on the helm screen
        for (on in listOf(false, true)) {
            set(context, server, "helm.wiringScreen", on)
            val world = server.computeOnServer<Vec3, RuntimeException> { s ->
                val v = seatShip.shipToWorld.transformPosition(Vector3d(seatHelm.x + 0.5, seatHelm.y + 0.5, seatHelm.z + 0.5))
                Vec3(v.x, v.y, v.z)
            }
            server.runOnServer<RuntimeException> { s ->
                s.playerList.players[0].openMenu(s.overworld().getBlockEntity(seatHelm) as ShipHelmBlockEntity)
            }
            context.waitTicks(10)
            val visible = context.computeOnClient<Boolean, RuntimeException> { mc ->
                mc.gui.screen()?.children()?.filterIsInstance<net.minecraft.client.gui.components.Button>()
                    ?.any { it.visible && (it.message.contents as? TranslatableContents)?.key == "gui.vs_eureka.wiring" } == true
            }
            check("helm.wiringScreen=$on button", visible == on, "Wiring button shown=$visible (helm at ${"%.0f %.0f".format(world.x, world.z)})")
            closeScreen(context)
        }
    }
    // endregion

    // region Movement
    // A flying deck with balloons, a helm and some engines
    private fun flyer(level: ServerLevel, o: BlockPos, engines: Int): Pair<ServerShip, BlockPos> {
        for (x in 0 until 3) for (z in 0 until 3) {
            put(level, o.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState())
            put(level, o.offset(x, -1, z), block("vs_eureka:balloon").defaultBlockState())
        }
        val helm = o.offset(1, 1, 2)
        put(level, helm, block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
        var placed = 0
        var y = 1
        while (placed < engines) {
            for (z in 0 until 3) for (x in 0 until 3) {
                if (placed >= engines) break
                val pos = o.offset(x, y, z)
                if (pos == helm) continue
                put(level, pos, block("vs_eureka:engine").defaultBlockState())
                (level.getBlockEntity(pos) as EngineBlockEntity).setItem(0, ItemStack(Items.COAL, 64))
                placed++
            }
            y++
        }
        val ship = ShipAssembler.collectBlocks(level, helm, OURS)!!
        return ship to shipPos(level, ship, helm)
    }

    private fun follow(server: TestServerContext, ship: ServerShip) {
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val loaded = level.shipObjectWorld.loadedShips.getById(ship.id) ?: return@runOnServer
            val pos = loaded.transform.positionInWorld
            val p = s.playerList.players[0]
            if (p.vehicle == null) {
                p.abilities.flying = true
                p.onUpdateAbilities()
                p.teleportTo(level, pos.x(), pos.y() + 12.0, pos.z() + 6.0, setOf(), 180f, 40f, true)
            }
        }
    }

    private fun stop(server: TestServerContext, ship: ServerShip) {
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val loaded = level.shipObjectWorld.loadedShips.getById(ship.id) as? LoadedServerShip ?: return@runOnServer
            loaded.getAttachment(EurekaShipControl::class.java)?.throttle = 0.0
            loaded.removeAttachment<SeatedControllingPlayer>()
            vsCore.teleportShip(level.shipObjectWorld, loaded, vsCore.newShipTeleportData(
                newPos = Vector3d(loaded.transform.positionInWorld), newVel = Vector3d(), newOmega = Vector3d()
            ))
        }
    }

    private fun speed(server: TestServerContext, ship: ServerShip): Double = server.computeOnServer<Double, RuntimeException> { s ->
        val v = s.overworld().shipObjectWorld.loadedShips.getById(ship.id)?.velocity ?: return@computeOnServer -1.0
        hypot(v.x(), v.z())
    }

    private fun movement(context: ClientGameTestContext, server: TestServerContext) {
        // The throttle with real keys, from the helm
        lateinit var flyer: ServerShip
        lateinit var flyerHelm: BlockPos
        server.runOnServer<RuntimeException> { s ->
            val (ship, helm) = flyer(s.overworld(), BlockPos(0, 20, 120), 4)
            flyer = ship; flyerHelm = helm
        }
        follow(server, flyer)
        context.waitTicks(60)
        fun sit() {
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                p.abilities.flying = false
                p.onUpdateAbilities()
                (s.overworld().getBlockEntity(flyerHelm) as ShipHelmBlockEntity).sit(p, true)
            }
            context.waitTicks(20)
        }
        for (on in listOf(false, true)) {
            set(context, server, "helm.throttle", on)
            stop(server, flyer)
            sit()
            context.input.holdKey { it.keyUp }
            context.waitTicks(40)
            context.input.releaseKey { it.keyUp }
            val held = speed(server, flyer)
            context.waitTicks(80)
            val after = speed(server, flyer)
            val throttle = server.computeOnServer<Double, RuntimeException> { s ->
                loaded(s.overworld(), flyer).getAttachment(EurekaShipControl::class.java)!!.throttle
            }
            check("helm.throttle=$on", if (on) after > 3.0 && throttle > 0.5 else after < held * 0.3 && held > 1.0 && throttle == 0.0,
                "W held 2 s: %.1f b/s, 4 s after letting go: %.1f b/s, throttle %.2f".format(held, after, throttle))
            if (!on) {
                // Cruise control like Eureka 1.6.4: C keeps the controls going
                context.input.holdKey { it.keyUp }
                context.waitTicks(20)
                context.input.holdKey(InputConstants.getKey("key.keyboard.c"))
                context.waitTicks(2)
                log("config cruise key down=" + context.computeOnClient<Boolean, RuntimeException> {
                    org.valkyrienskies.mod.common.config.VSKeyBindings.shipCruise.get().isDown
                } + " hotbar save key down=" + context.computeOnClient<Boolean, RuntimeException> { it.options.keySaveHotbarActivator.isDown })
                context.waitTicks(4)
                context.input.releaseKey(InputConstants.getKey("key.keyboard.c"))
                context.waitTicks(5)
                context.input.releaseKey { it.keyUp }
                context.waitTicks(60)
                val cruising = server.computeOnServer<Boolean, RuntimeException> { s -> loaded(s.overworld(), flyer).getAttachment(EurekaShipControl::class.java)!!.isCruising }
                check("helm.throttle=false cruise control", cruising && speed(server, flyer) > 1.0,
                    "C pressed: cruising=$cruising at %.1f b/s".format(speed(server, flyer)))
                context.input.holdKey(InputConstants.getKey("key.keyboard.c"))
                context.waitTicks(6)
                context.input.releaseKey(InputConstants.getKey("key.keyboard.c"))
                context.waitTicks(5)
            }
            server.runOnServer<RuntimeException> { s -> s.playerList.players[0].stopRiding() }
            stop(server, flyer)
            follow(server, flyer)
            context.waitTicks(20)
        }

        // Top speed with 10 engines, driven like a player holding W
        lateinit var fast: ServerShip
        server.runOnServer<RuntimeException> { s -> fast = flyer(s.overworld(), BlockPos(40, 20, 120), 10).first }
        follow(server, fast)
        context.waitTicks(60)
        for (on in listOf(false, true)) {
            set(context, server, "movement.engineSpeedByMass", on)
            stop(server, fast)
            server.runOnServer<RuntimeException> { s ->
                val driver = SeatedControllingPlayer(Direction.NORTH)
                driver.forwardImpulse = 1f
                loaded(s.overworld(), fast).setAttachment(driver)
            }
            var top = 0.0
            for (second in 1..12) {
                context.waitTicks(20)
                follow(server, fast)
                top = maxOf(top, speed(server, fast))
            }
            check("movement.engineSpeedByMass=$on", if (on) top > 30.0 else top in 5.0..16.0, "10 engines, 12 s flat out: %.1f b/s".format(top))
        }
        stop(server, fast)

        // Levelling: fast ships stay level, both ways run
        for (on in listOf(false, true)) {
            set(context, server, "movement.highSpeedLevelling", on)
            stop(server, fast)
            server.runOnServer<RuntimeException> { s ->
                val driver = SeatedControllingPlayer(Direction.NORTH)
                driver.forwardImpulse = 1f
                loaded(s.overworld(), fast).setAttachment(driver)
            }
            var tilt = 0.0
            for (second in 1..6) {
                context.waitTicks(20)
                follow(server, fast)
                tilt = maxOf(tilt, server.computeOnServer<Double, RuntimeException> { s ->
                    val up = loaded(s.overworld(), fast).transform.shipToWorldRotation.transform(Vector3d(0.0, 1.0, 0.0))
                    Math.toDegrees(up.angle(Vector3d(0.0, 1.0, 0.0)))
                })
            }
            check("movement.highSpeedLevelling=$on", tilt.isFinite() && tilt < 45.0, "most tilt in 6 s flat out: %.1f deg at %.1f b/s".format(tilt, speed(server, fast)))
        }
        stop(server, fast)

        // Turning with the engines paused: Eureka 1.6.4 turns a little anyway, now turning takes engines
        for (on in listOf(false, true)) {
            set(context, server, "movement.turningNeedsEngines", on)
            stop(server, fast)
            follow(server, fast)
            context.waitTicks(20)
            server.runOnServer<RuntimeException> { s ->
                val ship = loaded(s.overworld(), fast)
                ship.getAttachment(EurekaShipControl::class.java)!!.enginesPaused = true
                val driver = SeatedControllingPlayer(Direction.NORTH)
                driver.leftImpulse = 1f
                ship.setAttachment(driver)
            }
            var rate = 0.0
            for (t in 1..3) {
                context.waitTicks(20)
                follow(server, fast)
                rate = maxOf(rate, server.computeOnServer<Double, RuntimeException> { s -> Math.toDegrees(Math.abs(loaded(s.overworld(), fast).angularVelocity.y())) })
            }
            check("movement.turningNeedsEngines=$on", if (on) rate < 1.0 else rate > 3.0, "engines paused, left held 3 s: up to %.1f deg/s".format(rate))
            server.runOnServer<RuntimeException> { s -> loaded(s.overworld(), fast).getAttachment(EurekaShipControl::class.java)!!.enginesPaused = false }
            stop(server, fast)
        }

        // Block by block drag on heavy ships
        val oldLimit = EurekaConfig.SERVER.noBlockDragAboveMass
        EurekaConfig.SERVER.noBlockDragAboveMass = 1.0
        for (on in listOf(false, true)) {
            set(context, server, "movement.noBlockDragOnBigShips", on)
            context.waitTicks(10)
            val simple = server.computeOnServer<Boolean, RuntimeException> { s ->
                field<Boolean>(loaded(s.overworld(), fast).getAttachment(EurekaShipControl::class.java)!!, "simpleDrag")
            }
            check("movement.noBlockDragOnBigShips=$on", simple == on, "drag skipped=$simple")
        }
        EurekaConfig.SERVER.noBlockDragAboveMass = oldLimit
        context.waitTicks(5)

        // Water under a boat
        lateinit var boat: ServerShip
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            for (x in -2 until 9) for (z in 158 until 169) for (y in GROUND - 3 until GROUND) level.setBlock(BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 3)
            val o = BlockPos(1, GROUND, 161)
            for (x in 0 until 5) for (z in 0 until 5) put(level, o.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState())
            val helm = o.offset(2, 1, 2)
            put(level, helm, block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
            boat = ShipAssembler.collectBlocks(level, helm, OURS)!!
        }
        stand(server, 3.5, GROUND + 2.0, 156.5)
        context.waitTicks(80)
        for (on in listOf(false, true)) {
            set(context, server, "movement.boatWaterContact", on)
            context.waitTicks(30)
            val wet = server.computeOnServer<Double, RuntimeException> { s ->
                field<Double>(loaded(s.overworld(), boat).getAttachment(EurekaShipControl::class.java)!!, "waterContact")
            }
            check("movement.boatWaterContact=$on", (wet > 0.0) == on, "water measured under the boat: %.2f".format(wet))
        }
    }
    // endregion

    // region World
    private fun world(context: ClientGameTestContext, server: TestServerContext) {
        // Creative from here: the player gets dropped next to rafts, two falls in survival kill them
        server.runOnServer<RuntimeException> { s ->
            val p = s.playerList.players[0]
            p.setGameMode(GameType.CREATIVE)
            p.heal(p.maxHealth)
        }
        context.waitTicks(5)
        for (on in listOf(false, true)) {
            set(context, server, "world.buildCommands", on)
            reload(context, server)
            val there = server.computeOnServer<Boolean, RuntimeException> { s ->
                s.commands.dispatcher.root.getChild("vs")?.getChild("build") != null
            }
            check("world.buildCommands=$on", there == on, "/vs build there=$there")
        }

        for (on in listOf(false, true)) {
            set(context, server, "world.welcomeMessage", on)
            val before = welcomes
            server.runOnServer<RuntimeException> { s -> org.valkyrienskies.eureka.WelcomeMessage.onPlayerJoin(s.playerList.players[0]) }
            context.waitTicks(10)
            check("world.welcomeMessage=$on", (welcomes > before) == on, "welcome shown=${welcomes > before}")
        }

        for (on in listOf(false, true)) {
            set(context, server, "world.paperWorldImport", on)
            val dir = Files.createTempDirectory("vs-paper-test")
            val paper = dir.resolve("dimensions/minecraft/overworld/data/minecraft")
            Files.createDirectories(paper)
            Files.write(paper.resolve("world_gen_settings.dat"), byteArrayOf(1, 2, 3))
            org.valkyrienskies.mod.util.PaperWorldImport.importIfNeeded(dir)
            val imported = Files.exists(dir.resolve("data/minecraft/world_gen_settings.dat"))
            check("world.paperWorldImport=$on", imported == on, "a Paper world got converted=$imported")
        }

        // A mob with no AI on a ship that moves
        lateinit var raft: ServerShip
        lateinit var cow: Mob
        for (on in listOf(false, true)) {
            set(context, server, "world.noAiMobsRideShips", on)
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val o = BlockPos(if (on) 120 else 100, GROUND + 10, 200)
                for (x in 0 until 5) for (z in 0 until 5) put(level, o.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState())
                put(level, o.offset(2, -1, 2), block("vs_eureka:balloon").defaultBlockState())
                raft = ShipAssembler.collectBlocks(level, o.offset(2, 0, 2), OURS)!!
                cow = net.minecraft.world.entity.EntityTypes.COW.create(level, EntitySpawnReason.COMMAND)!!
                cow.setNoAi(true)
                cow.setPos(o.x + 2.5, o.y + 1.0, o.z + 2.5)
                level.addFreshEntity(cow)
                s.playerList.players[0].teleportTo(level, o.x + 2.5, o.y + 8.0, o.z - 6.0, setOf(), 0f, 30f, true)
            }
            context.waitTicks(40)
            val start = server.computeOnServer<Double, RuntimeException> { s ->
                val loaded = loaded(s.overworld(), raft)
                vsCore.teleportShip(s.overworld().shipObjectWorld, loaded, vsCore.newShipTeleportData(
                    newPos = Vector3d(loaded.transform.positionInWorld), newVel = Vector3d(3.0, 0.0, 0.0)
                ))
                cow.x
            }
            context.waitTicks(40)
            val moved = server.computeOnServer<Double, RuntimeException> { _ -> cow.x - start }
            val raftMoved = server.computeOnServer<Double, RuntimeException> { s -> loaded(s.overworld(), raft).transform.positionInWorld.x() }
            check("world.noAiMobsRideShips=$on", if (on) moved > 1.5 else Math.abs(moved) < 0.5,
                "a cow with no AI moved %.1f blocks with the raft (raft now at x %.1f)".format(moved, raftMoved))
            server.runOnServer<RuntimeException> { _ -> cow.discard() }
        }

        // Ground kept loaded under ships
        val under = server.computeOnServer<Long, RuntimeException> { s ->
            val pos = loaded(s.overworld(), raft).transform.positionInWorld
            net.minecraft.world.level.ChunkPos.pack(pos.x().toInt() shr 4, pos.z().toInt() shr 4)
        }
        fun groundTickets() = server.computeOnServer<Int, RuntimeException> { s ->
            val tickets = (s.overworld().chunkSource.chunkMap.distanceManager as DistanceManagerAccessor).ticketStorage.getTickets(under)
            tickets.count { BuiltInRegistries.TICKET_TYPE.getKey(it.type)?.path == "ship_terrain" }
        }
        set(context, server, "world.keepGroundLoadedUnderShips", false)
        context.waitTicks(90)
        check("world.keepGroundLoadedUnderShips=false", groundTickets() == 0, "ground tickets under a ship: ${groundTickets()}")
        set(context, server, "world.keepGroundLoadedUnderShips", true)
        context.waitTicks(30)
        check("world.keepGroundLoadedUnderShips=true", groundTickets() > 0, "ground tickets under a ship: ${groundTickets()}")

        // Physics terrain far from any ship
        // Twice: a ship carries a player along for a while after they leave it, through a teleport too
        stand(server, -400.5, GROUND.toDouble(), 0.5)
        context.waitTicks(40)
        stand(server, -400.5, GROUND.toDouble(), 0.5)
        context.waitTicks(60)
        // The chunk the player stands in, far from every ship
        val here = server.computeOnServer<net.minecraft.world.level.ChunkPos, RuntimeException> { s ->
            val p = s.playerList.players[0]
            log("config terrain player at %.1f %.1f %.1f".format(p.x, p.y, p.z))
            p.chunkPosition()
        }
        fun known() = server.computeOnServer<Boolean, RuntimeException> { s -> (s.overworld() as VSServerLevel).isChunkKnownToPhysics(here.x(), here.z()) }
        set(context, server, "world.physicsTerrainOnlyNearShips", false)
        context.waitTicks(10)
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val map = level.chunkSource.chunkMap as org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
            val key = here.pack()
            val holder = map.callGetVisibleChunkIfPresent(key)
            val full = holder?.fullChunkFuture?.getNow(net.minecraft.server.level.ChunkHolder.UNLOADED_LEVEL_CHUNK)?.isSuccess
            val known = field<Map<*, *>>(level, "vs\$knownChunks").size
            log("config terrain switch=${AddedFeatures.terrainOnlyNearShips} visible=${map.visibleChunkMap.size} holder=${holder != null} full=$full known=$known")
        }
        check("world.physicsTerrainOnlyNearShips=false", known(), "physics has the ground under the player, far from ships: ${known()}")
        set(context, server, "world.physicsTerrainOnlyNearShips", true)
        context.waitTicks(120)
        check("world.physicsTerrainOnlyNearShips=true", !known(), "physics has the ground under the player, far from ships: ${known()}")

        // Whole ship loading lets go of what it held
        set(context, server, "world.wholeShipLoading", false)
        context.waitTicks(5)
        val held = server.computeOnServer<Int, RuntimeException> { _ ->
            val f = org.valkyrienskies.mod.common.world.ChunkManagement::class.java.getDeclaredField("wholeShips")
            f.isAccessible = true
            (f.get(org.valkyrienskies.mod.common.world.ChunkManagement) as Map<*, *>).size
        }
        check("world.wholeShipLoading=false", held == 0, "ships kept whole: $held")
        set(context, server, "world.wholeShipLoading", true)
        stand(server, 120.5, GROUND.toDouble(), 190.5)
        context.waitTicks(60)
        check("world.wholeShipLoading=true", true, "back on, ships load as before")

        log("config world.elytraTakeoffFromShips: checked by VS_TEST_FEATURES with VS_TEST_SWITCH_OFF=world.elytraTakeoffFromShips")
    }
    // endregion

    // region The file itself
    private fun fileEdit(context: ClientGameTestContext, server: TestServerContext) {
        val file = FabricLoader.getInstance().configDir.resolve(AddedFeatures.FILE)
        val text = Files.readString(file)
        val line = Regex("(?m)^(\\s*cannon\\s*=\\s*)true\\s*$")
        if (!line.containsMatchIn(text)) {
            check("file", false, "no 'cannon = true' line in $file")
            return
        }
        Files.writeString(file, line.replace(text, "$1false"))
        var onServer = true
        var onClient = true
        var waited = 0
        while (waited < 900 && (onServer || onClient)) {
            context.waitTicks(20)
            waited += 20
            onServer = AddedFeatures.local()["blocks.cannon"] == true
            onClient = clientItemOn(context, "vs_eureka:cannon")
        }
        check("file edit off", !onServer && !onClient, "after ${waited / 20} s: server reads off=${!onServer}, player sees off=${!onClient}")
        Files.writeString(file, text)
        waited = 0
        while (waited < 900 && (!onServer || !onClient)) {
            context.waitTicks(20)
            waited += 20
            onServer = AddedFeatures.local()["blocks.cannon"] == true
            onClient = clientItemOn(context, "vs_eureka:cannon")
        }
        check("file edit on", onServer && onClient, "after ${waited / 20} s: server on=$onServer, player on=$onClient")
    }
    // endregion
}
