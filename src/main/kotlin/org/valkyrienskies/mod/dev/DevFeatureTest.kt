package org.valkyrienskies.mod.dev

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.client.CameraType
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.eureka.blockentity.CannonBlockEntity
import org.valkyrienskies.eureka.blockentity.EngineBlockEntity
import org.valkyrienskies.eureka.entity.Cannonball
import org.valkyrienskies.eureka.util.ShipAssembler
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import kotlin.math.hypot

/** Development only, never shipped: aiming cannons, taking off with an elytra and ship speeds, with real input. */
object DevFeatureTest {
    private fun log(s: String) = println("SELFTEST $s")
    private fun block(id: String): Block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))
    private fun f(v: Vector3d) = "(%.3f %.3f %.3f)".format(v.x, v.y, v.z)

    private fun ship(level: ServerLevel) = level.shipObjectWorld.loadedShips.getById(DevSelfTest.lifted!!.id)!!

    private fun teleportOnShip(level: ServerLevel, inShip: Vector3d, yaw: Float, pitch: Float) {
        val p = level.server.playerList.players[0]
        val world = ship(level).shipToWorld.transformPosition(Vector3d(inShip))
        p.abilities.flying = false
        p.onUpdateAbilities()
        p.teleportTo(level, world.x, world.y, world.z, setOf(), yaw, pitch, true)
    }

    // Turns the player to look along a direction given in ship space
    private fun lookAlongShip(context: ClientGameTestContext, inShip: Vector3d) {
        context.runOnClient<RuntimeException> { mc ->
            val ships = mc.level!!.shipObjectWorld.loadedShips
            val ship = ships.getById(DevSelfTest.lifted!!.id) ?: ships.first()
            val d = ship.renderTransform.shipToWorld.transformDirection(Vector3d(inShip)).normalize()
            mc.player!!.yRot = Math.toDegrees(Math.atan2(-d.x, d.z)).toFloat()
            mc.player!!.xRot = Math.toDegrees(-Math.atan2(d.y, hypot(d.x, d.z))).toFloat()
        }
    }

    @Volatile
    private var caught: Vector3d? = null
    private val parked = ArrayList<Cannonball>()
    private var catching = false

    fun cannonTest(context: ClientGameTestContext, server: TestServerContext, aim: (Vector3d) -> Unit) {
        if (!catching) {
            catching = true
            // Note how a cannonball leaves and park it high up, so it can't blow up the test deck
            ServerEntityEvents.ENTITY_LOAD.register(ServerEntityEvents.Load { entity, _ ->
                if (entity is Cannonball) {
                    caught = Vector3d(entity.deltaMovement.x, entity.deltaMovement.y, entity.deltaMovement.z)
                    entity.isNoGravity = true
                    entity.deltaMovement = net.minecraft.world.phys.Vec3.ZERO
                    entity.setPos(entity.x, entity.y + 300.0, entity.z)
                    parked.add(entity)
                }
            })
        }
        lateinit var spot: BlockPos
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            spot = DevSelfTest.seatPos(level).offset(3, 0, -5)
            val p = s.playerList.players[0]
            p.setGameMode(GameType.SURVIVAL)
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("vs_eureka:cannon")), 4))
            teleportOnShip(level, Vector3d(spot.x - 1.5, spot.y + 0.05, spot.z + 2.5), 0f, 30f)
        }
        context.waitTicks(15)
        // Place it the way a player does, clicking the top of the deck while facing north on the ship
        aim(Vector3d(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5))
        context.input.pressKey { it.keyUse }
        context.waitTicks(10)
        fun state(tag: String) = server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val be = level.getBlockEntity(spot) as? CannonBlockEntity
            log("cannon $tag block=${level.getBlockState(spot)} yaw=${be?.yaw} pitch=${be?.pitch} dir=${be?.direction()?.let(::f)}")
        }
        state("placed")
        server.runOnServer<RuntimeException> { s -> s.playerList.players[0].setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY) }
        context.waitTicks(2)

        val targets = listOf(Vector3d(1.0, 1.0, -1.0), Vector3d(-0.3, -1.6, -1.0), Vector3d(0.0, 0.0, 1.0), Vector3d(0.6, 3.0, 0.2))
        for ((i, target) in targets.withIndex()) {
            // Sneak, right click the cannon, look where it should fire and let go of sneak
            context.input.holdKey { it.keyShift }
            context.waitTicks(4)
            aim(Vector3d(spot.x + 0.5, spot.y + 0.5, spot.z + 0.5))
            context.input.pressKey { it.keyUse }
            context.waitTicks(4)
            lookAlongShip(context, target)
            context.waitTicks(12)
            context.input.releaseKey { it.keyShift }
            context.waitTicks(8)
            val want = Vector3d(target).normalize()
            server.runOnServer<RuntimeException> { s ->
                val be = s.overworld().getBlockEntity(spot) as CannonBlockEntity
                log("cannon aim $i want=${f(want)} got=${f(be.direction())} off=%.2f deg".format(Math.toDegrees(be.direction().angle(want))))
            }
            context.runOnClient<RuntimeException> { mc ->
                val be = mc.level!!.getBlockEntity(spot) as? CannonBlockEntity
                log("cannon aim $i client yaw=${be?.yaw} pitch=${be?.pitch}")
            }
            // Look at it from the side and fire it
            server.runOnServer<RuntimeException> { s -> teleportOnShip(s.overworld(), Vector3d(spot.x + 2.5, spot.y + 0.05, spot.z + 2.5), 0f, 0f) }
            context.waitTicks(10)
            aim(Vector3d(spot.x + 0.5, spot.y + 0.7, spot.z + 0.5))
            context.waitTicks(20)
            DevClientTest.shotOf(context, "cannon_aim_$i")
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                caught = null
                val be = level.getBlockEntity(spot) as CannonBlockEntity
                be.setItem(0, ItemStack(Items.TNT, 4))
                level.setBlock(spot.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3)
            }
            context.waitTicks(8)
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val ship = ship(level)
                val v = caught
                if (v != null) {
                    val inShip = ship.worldToShip.transformDirection(Vector3d(v)).normalize()
                    log("cannon fired $i speed=%.2f dirInShip=${f(inShip)} off=%.2f deg".format(v.length(), Math.toDegrees(inShip.angle(want))))
                } else log("cannon fired $i no ball")
                for (ball in parked) ball.discard()
                parked.clear()
                level.setBlock(spot.east(), Blocks.AIR.defaultBlockState(), 3)
                teleportOnShip(level, Vector3d(spot.x - 1.5, spot.y + 0.05, spot.z + 2.5), 0f, 30f)
            }
            context.waitTicks(10)
        }
        // The item in the inventory
        server.runOnServer<RuntimeException> { s ->
            s.playerList.players[0].setItemInHand(InteractionHand.MAIN_HAND, ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("vs_eureka:cannon")), 3))
        }
        context.waitTicks(3)
        context.runOnClient<RuntimeException> { mc -> mc.gui.setScreen(InventoryScreen(mc.player!!)) }
        context.waitTicks(10)
        DevClientTest.shotOf(context, "cannon_inventory")
        context.input.pressKey(InputConstants.getKey("key.keyboard.escape"))
        context.waitTicks(5)
    }

    // Survival player with an elytra jumps on the deck and presses jump again in the air, first on a still ship
    fun elytraTest(context: ClientGameTestContext, server: TestServerContext) {
        for (moving in listOf(false, true)) {
            val tag = if (moving) "moving" else "still"
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val p = s.playerList.players[0]
                p.setGameMode(GameType.SURVIVAL)
                p.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                p.resetFallDistance()
                p.heal(20f)
                val seat = DevSelfTest.seatPos(level)
                teleportOnShip(level, Vector3d(seat.x + 4.5, seat.y + 0.05, seat.z - 3.5), 180f, 0f)
                if (moving) {
                    DevSelfTest.addFuel(level)
                    val ship = ship(level) as LoadedServerShip
                    val driver = ship.getAttachment(SeatedControllingPlayer::class.java) ?: SeatedControllingPlayer(Direction.NORTH)
                    driver.forwardImpulse = 1f
                    ship.setAttachment(driver)
                }
            }
            context.waitTicks(if (moving) 100 else 30)
            fun sample(t: Int) {
                val client = context.computeOnClient<String, RuntimeException> { mc ->
                    val p = mc.player!!
                    val drag = (p as IEntityDraggingInformationProvider).draggingInformation
                    val ships = mc.level!!.shipObjectWorld.loadedShips
                    val ship = ships.getById(DevSelfTest.lifted!!.id) ?: ships.first()
                    val local = ship.transform.worldToShip.transformPosition(Vector3d(p.x, p.y, p.z))
                    val seat = ship.transform.positionInShip
                    val world = hypot(p.x - p.xo, p.z - p.zo) * 20
                    "client moved=%.1f ".format(world) + "client gliding=${p.isFallFlying} ground=${p.onGround()} dragged=${drag.isEntityBeingDraggedByAShip()} onShip=(%.2f %.2f %.2f) vel=(%.1f %.1f %.1f)".format(
                        local.x - seat.x(), local.y - seat.y(), local.z - seat.z(), p.deltaMovement.x * 20, p.deltaMovement.y * 20, p.deltaMovement.z * 20)
                }
                server.runOnServer<RuntimeException> { s ->
                    val p = s.playerList.players[0]
                    val ship = ship(s.overworld())
                    log("elytra $tag t=$t $client | server gliding=${p.isFallFlying} ground=${p.onGround()} shipSpeed=%.1f".format(ship.velocity.length()))
                }
            }
            sample(-1)
            // Tap jump to jump off the deck, and tap it again in the air
            context.input.holdKey { it.keyJump }
            context.waitTicks(2)
            context.input.releaseKey { it.keyJump }
            for (t in 0..3) { sample(t); context.waitTicks(1) }
            context.input.holdKey { it.keyJump }
            context.waitTicks(2)
            context.input.releaseKey { it.keyJump }
            // Turn to the side of the ship, the way a player heads off it
            lookAlongShip(context, Vector3d(1.0, -0.15, 0.0))
            for (t in 4..44) {
                context.waitTicks(1)
                if (t <= 16 || t % 4 == 0) sample(t)
                if (t == 12) {
                    context.runOnClient<RuntimeException> { mc -> mc.options.cameraType = CameraType.THIRD_PERSON_BACK }
                    context.waitTicks(1)
                    DevClientTest.shotOf(context, "elytra_$tag")
                    context.runOnClient<RuntimeException> { mc -> mc.options.cameraType = CameraType.FIRST_PERSON }
                }
            }
            server.runOnServer<RuntimeException> { s ->
                val ship = ship(s.overworld()) as LoadedServerShip
                ship.getAttachment(SeatedControllingPlayer::class.java)?.forwardImpulse = 0f
                val p = s.playerList.players[0]
                p.stopFallFlying()
                p.setGameMode(GameType.CREATIVE)
            }
            context.waitTicks(60)
        }
    }

    /**
     * The Info screen before assembling, with real clicks: a boat on its own, a boat touching a dock and a boat touching
     * something far too big.
     */
    fun assemblyTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("gamemode creative @a")
            world.connection.waitForChunksRender()
            val cases = listOf("boat", "dock", "huge")
            for ((i, case) in cases.withIndex()) {
                val origin = BlockPos(i * 120, 10, 0)
                lateinit var helm: BlockPos
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    helm = buildBoat(level, origin, case)
                    val p = s.playerList.players[0]
                    // Flying, so nothing falls through a deck that hasn't reached the client yet
                    p.abilities.flying = true
                    p.onUpdateAbilities()
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                    // Over the deck, a step behind the helm
                    p.teleportTo(level, helm.x + 0.5, helm.y + 0.2, helm.z + 1.7, setOf(), 180f, 20f, true)
                }
                context.waitTicks(60)
                world.connection.waitForChunksRender()
                // Again, the ship of the last case carries the player along for a moment after they leave it
                server.runOnServer<RuntimeException> { s ->
                    s.playerList.players[0].teleportTo(s.overworld(), helm.x + 0.5, helm.y + 0.2, helm.z + 1.7, setOf(), 180f, 20f, true)
                }
                context.waitTicks(10)
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld(); val p = s.playerList.players[0]
                    log("assembly $case server helm=${level.getBlockState(helm).block.descriptionId} deck=${level.getBlockState(helm.below()).block.descriptionId} player=(%.1f %.1f %.1f)".format(p.x, p.y, p.z))
                }
                log("assembly $case client " + context.computeOnClient<String, RuntimeException> { mc ->
                    val p = mc.player!!
                    "helm=${mc.level!!.getBlockState(helm).block.descriptionId} deck=${mc.level!!.getBlockState(helm.below()).block.descriptionId} chunkLoaded=${mc.level!!.hasChunk(helm.x shr 4, helm.z shr 4)} player=(%.1f %.1f %.1f) flying=${p.abilities.flying}".format(p.x, p.y, p.z)
                })
                // Land on the deck now that it's there
                server.runOnServer<RuntimeException> { s ->
                    val p = s.playerList.players[0]
                    p.abilities.flying = false
                    p.onUpdateAbilities()
                }
                context.waitTicks(20)
                // Open the helm the way a player does off a ship, sneaking and right clicking it
                for (attempt in 1..3) {
                    context.runOnClient<RuntimeException> { mc ->
                        val eye = mc.player!!.getEyePosition(1f)
                        val dx = helm.x + 0.5 - eye.x; val dy = helm.y + 0.3 - eye.y; val dz = helm.z + 0.5 - eye.z
                        mc.player!!.yRot = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
                        mc.player!!.xRot = Math.toDegrees(-Math.atan2(dy, hypot(dx, dz))).toFloat()
                    }
                    context.waitTicks(3)
                    log("assembly $case looking at ${context.computeOnClient<String, RuntimeException> { mc -> (mc.hitResult as? net.minecraft.world.phys.BlockHitResult)?.blockPos?.let { "$it ${mc.level!!.getBlockState(it).block.descriptionId}" } ?: "${mc.hitResult?.type}" }} helm=$helm")
                    context.input.holdKey { it.keyShift }
                    context.waitTicks(3)
                    context.input.pressKey { it.keyUse }
                    context.waitTicks(8)
                    context.input.releaseKey { it.keyShift }
                    context.waitTicks(4)
                    if (context.computeOnClient<Boolean, RuntimeException> { it.gui.screen() != null }) break
                }
                log("assembly $case screen=${context.computeOnClient<String, RuntimeException> { it.gui.screen()?.javaClass?.simpleName ?: "none" }}")
                context.clickScreenButton("gui.vs_eureka.assemble")
                context.waitTicks(20)
                val info = context.computeOnClient<String, RuntimeException> { mc ->
                    val screen = mc.gui.screen() as? org.valkyrienskies.eureka.gui.assembly.ShipAssemblyScreen
                    if (screen == null) "no info screen, showing ${mc.gui.screen()?.javaClass?.simpleName}"
                    else "blocks=${screen.blockCount()} drawn=${screen.shownBlocks()} canAssemble=${screen.canAssemble()}"
                }
                log("assembly $case info $info")
                server.runOnServer<RuntimeException> { s -> log("assembly $case ships before=${s.overworld().shipObjectWorld.allShips.size}") }
                DevClientTest.shotOf(context, "assembly_${case}")
                // Drag the view around with the mouse
                val middle = context.computeOnClient<DoubleArray, RuntimeException> { mc ->
                    val w = mc.window.guiScaledWidth.toDouble(); doubleArrayOf(w / 2, 90.0)
                }
                context.input.setCursorPos(middle[0], middle[1])
                context.waitTicks(2)
                context.input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT)
                for (step in 1..6) {
                    context.input.setCursorPos(middle[0] + step * 20, middle[1] + step * 4)
                    context.waitTicks(1)
                }
                context.input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT)
                context.waitTicks(5)
                DevClientTest.shotOf(context, "assembly_${case}_turned")
                if (case == "boat") {
                    context.clickScreenButton("gui.vs_eureka.assembly.confirm")
                } else {
                    context.clickScreenButton("gui.cancel")
                }
                context.waitTicks(30)
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    val ships = level.shipObjectWorld.allShips
                    log("assembly $case ships after=${ships.size} helm still in world=${level.getBlockState(helm).block.descriptionId}")
                }
                log("assembly $case screen after=${context.computeOnClient<String, RuntimeException> { it.gui.screen()?.javaClass?.simpleName ?: "none" }}")
            }
        }
        log("assembly test finished")
    }

    // A small boat floating in the air, on its own, touching a dock, or touching a block far too big for a ship
    private fun buildBoat(level: ServerLevel, origin: BlockPos, case: String): BlockPos {
        val planks = Blocks.SPRUCE_PLANKS.defaultBlockState()
        for (x in 0 until 5) for (z in 0 until 9) {
            level.setBlock(origin.offset(x, 0, z), planks, 3)
            if (x == 0 || x == 4 || z == 0 || z == 8) level.setBlock(origin.offset(x, 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3)
        }
        level.setBlock(origin.offset(2, -1, 4), block("vs_eureka:balloon").defaultBlockState(), 3)
        val helm = origin.offset(2, 1, 6)
        level.setBlock(helm, block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3)
        when (case) {
            "dock" -> {
                // A dock right next to the boat, touching its side
                for (x in 5 until 13) for (z in 2 until 7) level.setBlock(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState(), 3)
                for (x in 6 until 13 step 3) for (y in -6 until 0) level.setBlock(origin.offset(x, y, 4), Blocks.OAK_LOG.defaultBlockState(), 3)
            }
            "huge" -> {
                // Far more blocks than a ship may have, touching the back of the boat
                val big = Blocks.OAK_PLANKS.defaultBlockState()
                for (x in -14 until 19) for (y in -30 until 1) for (z in 9 until 42) level.setBlock(origin.offset(x, y, z), big, 2)
            }
        }
        return helm
    }

    class SpeedRun(val name: String, val width: Int, val length: Int, val engines: Int)

    /** Builds a flying deck, puts engines on it and drives it flat out, then reports the speed it settles at. */
    fun speedTest(context: ClientGameTestContext) {
        val runs = (System.getenv("VS_TEST_SPEED_RUNS") ?: "tiny:3x3x1,tiny:3x3x4,tiny:3x3x10,small:5x16x4,medium:7x40x10,medium:7x40x30,long:5x150x30,huge:5x500x100")
            .split(",").map { r ->
                val (name, dims) = r.split(":")
                val (w, l, e) = dims.split("x").map(String::toInt)
                SpeedRun(name, w, l, e)
            }
        context.worldBuilder().create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            context.runOnClient<RuntimeException> { mc ->
                mc.options.renderDistance().set((System.getenv("VS_TEST_VIEW") ?: "4").toInt())
                System.getenv("VS_TEST_SIM")?.let { mc.options.simulationDistance().set(it.toInt()) }
            }
            for ((i, run) in runs.withIndex()) {
                var ship: ServerShip? = null
                val origin = BlockPos(i * 40, 200, 0)
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    val p = s.playerList.players[0]
                    p.setGameMode(GameType.SPECTATOR)
                    p.teleportTo(level, origin.x + 0.5, origin.y + 12.0, origin.z + run.length / 2.0, setOf(), 180f, 30f, true)
                }
                // Assembly needs every chunk under the ship ticking
                context.waitTicks((System.getenv("VS_TEST_SPEED_WAIT") ?: "1").toInt())
                server.runOnServer<RuntimeException> { s -> ship = buildSpeedShip(s.overworld(), origin, run) }
                context.waitTicks(40)
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    val loaded = level.shipObjectWorld.loadedShips.getById(ship!!.id) as LoadedServerShip
                    val driver = SeatedControllingPlayer(Direction.NORTH)
                    driver.forwardImpulse = 1f
                    loaded.setAttachment(driver)
                }
                var last = 0.0
                for (second in 1..30) {
                    context.waitTicks(20)
                    server.runOnServer<RuntimeException> { s ->
                        val level = s.overworld()
                        val loaded = level.shipObjectWorld.loadedShips.getById(ship!!.id) ?: return@runOnServer
                        val pos = loaded.transform.positionInWorld
                        val p = s.playerList.players[0]
                        p.teleportTo(level, pos.x(), pos.y() + 15.0, pos.z(), setOf(), 180f, 30f, true)
                        val v = loaded.velocity
                        last = hypot(v.x(), v.z())
                        val up = loaded.transform.shipToWorldRotation.transform(Vector3d(0.0, 1.0, 0.0))
                        val tilt = Math.toDegrees(up.angle(Vector3d(0.0, 1.0, 0.0)))
                        if (second % 5 == 0) {
                            val box = loaded.shipAABB!!
                            log("speed ${run.name} ${run.width}x${run.length} engines=${run.engines} t=${second}s speed=%.1f mass=%.0f long=%d tilt=%.1f y=%.1f".format(
                                last, loaded.inertiaData.mass, maxOf(box.maxX() - box.minX(), box.maxZ() - box.minZ()), tilt, pos.y()))
                        }
                    }
                }
                if (i == 0) DevClientTest.shotOf(context, "speed_${run.name}")
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    (level.shipObjectWorld.loadedShips.getById(ship!!.id) as? LoadedServerShip)?.let {
                        it.getAttachment(SeatedControllingPlayer::class.java)?.forwardImpulse = 0f
                    }
                }
            }
        }
        log("speed test finished")
    }

    private fun buildSpeedShip(level: ServerLevel, origin: BlockPos, run: SpeedRun): ServerShip? {
        val balloon = block("vs_eureka:balloon").defaultBlockState()
        for (x in 0 until run.width) for (z in 0 until run.length) {
            level.setBlock(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState(), 3)
            level.setBlock(origin.offset(x, -1, z), balloon, 3)
        }
        val helm = origin.offset(run.width / 2, 1, run.length - 1)
        level.setBlock(helm, block("vs_eureka:oak_ship_helm").defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3)
        var placed = 0
        var y = 1
        while (placed < run.engines) {
            for (z in 0 until run.length) for (x in 0 until run.width) {
                if (placed >= run.engines) break
                val pos = origin.offset(x, y, z)
                if (pos == helm) continue
                level.setBlock(pos, block("vs_eureka:engine").defaultBlockState(), 3)
                (level.getBlockEntity(pos) as EngineBlockEntity).setItem(0, ItemStack(Items.COAL, 64))
                placed++
            }
            y++
        }
        return ShipAssembler.collectBlocks(level, helm) { !it.isAir }
    }
}
