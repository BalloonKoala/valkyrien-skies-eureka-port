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
        // Left click with an empty hand, left click holding a block, right click with an empty hand, left click again
        val clicks = listOf("left", "left+cobblestone", "right", "left+cobblestone")
        for ((i, target) in targets.withIndex()) {
            val click = clicks[i]
            server.runOnServer<RuntimeException> { s ->
                s.playerList.players[0].setItemInHand(InteractionHand.MAIN_HAND,
                    if (click.endsWith("cobblestone")) ItemStack(Blocks.COBBLESTONE.asItem(), 8) else ItemStack.EMPTY)
            }
            // Sneak, click the cannon, look where it should fire and let go of sneak
            context.input.holdKey { it.keyShift }
            context.waitTicks(4)
            aim(Vector3d(spot.x + 0.5, spot.y + 0.5, spot.z + 0.5))
            if (click.startsWith("left")) context.input.pressKey { it.keyAttack } else context.input.pressKey { it.keyUse }
            context.waitTicks(4)
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val be = level.getBlockEntity(spot) as? CannonBlockEntity
                log("cannon click $i $click aiming=${be?.isBeingAimed == true} block=${level.getBlockState(spot).block} " +
                    "cobblestone=${s.playerList.players[0].mainHandItem.count}")
            }
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
                be.setItem(0, ItemStack(org.valkyrienskies.eureka.EurekaItems.SMALL_CANNONBALL.get(), 4))
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
        // Sneaking with a button in hand still puts it on the cannon instead of aiming
        server.runOnServer<RuntimeException> { s ->
            s.playerList.players[0].setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Blocks.STONE_BUTTON.asItem(), 2))
        }
        context.input.holdKey { it.keyShift }
        context.waitTicks(4)
        aim(Vector3d(spot.x + 0.5, spot.y + 0.5, spot.z + 1.0))
        context.input.pressKey { it.keyUse }
        context.waitTicks(4)
        context.input.releaseKey { it.keyShift }
        context.waitTicks(4)
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val be = level.getBlockEntity(spot) as? CannonBlockEntity
            log("cannon sneak button south=${level.getBlockState(spot.south()).block} aiming=${be?.isBeingAimed == true} cannon=${level.getBlockState(spot).block}")
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
        if (!countingDrawn) {
            countingDrawn = true
            net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION.register(
                net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.EndExtraction { ctx ->
                    drawnKinds = ctx.levelState().blockEntityRenderStates.groupingBy { it.javaClass.simpleName }.eachCount()
                }
            )
        }
        context.worldBuilder().create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("gamemode creative @a")
            world.connection.waitForChunksRender()
            val cases = listOf("boat", "dock", "ore", "huge")
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
                log("assembly $case info $info red=${context.computeOnClient<Int, RuntimeException> { mc -> (mc.gui.screen() as? org.valkyrienskies.eureka.gui.assembly.ShipAssemblyScreen)?.worldGeneratedCount() ?: -1 }}")
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
                    log("assembly boat drawn before ${drawnKinds.toSortedMap()}")
                    context.clickScreenButton("gui.vs_eureka.assembly.confirm")
                    for (wait in listOf(5, 20, 60)) {
                        context.waitTicks(wait)
                        log("assembly boat drawn after +$wait ticks ${drawnKinds.toSortedMap()}")
                    }
                    DevClientTest.shotOf(context, "assembly_boat_assembled")
                } else {
                    context.clickScreenButton("gui.cancel")
                }
                if (case == "ore") {
                    // Going around the screen doesn't help: the server checks again
                    context.runOnClient<RuntimeException> {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(org.valkyrienskies.eureka.assembly.AssemblyConfirmPayload(helm))
                    }
                    context.waitTicks(10)
                    server.runOnServer<RuntimeException> { s -> log("assembly ore ships after forcing=${s.overworld().shipObjectWorld.allShips.size}") }
                    // Break both red blocks for real from beside the boat, then it assembles
                    server.runOnServer<RuntimeException> { s ->
                        val p = s.playerList.players[0]
                        p.abilities.flying = true
                        p.onUpdateAbilities()
                        p.teleportTo(s.overworld(), origin.x + 8.5, origin.y.toDouble(), origin.z + 4.5, setOf(), 90f, 20f, true)
                    }
                    context.waitTicks(10)
                    log("assembly ore breaking from ${context.computeOnClient<String, RuntimeException> { mc -> "%.1f %.1f %.1f".format(mc.player!!.x, mc.player!!.y, mc.player!!.z) }}")
                    for (red in listOf(origin.offset(5, 0, 4), origin.offset(5, 0, 5))) {
                        context.runOnClient<RuntimeException> { mc ->
                            val eye = mc.player!!.getEyePosition(1f)
                            val dx = red.x + 0.5 - eye.x; val dy = red.y + 0.5 - eye.y; val dz = red.z + 0.5 - eye.z
                            mc.player!!.yRot = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
                            mc.player!!.xRot = Math.toDegrees(-Math.atan2(dy, hypot(dx, dz))).toFloat()
                        }
                        context.waitTicks(3)
                        log("assembly ore aiming at ${lookTarget(context)}")
                        context.input.pressKey { it.keyAttack }
                        context.waitTicks(6)
                    }
                    server.runOnServer<RuntimeException> { s ->
                        val level = s.overworld()
                        log("assembly ore broken: ${level.getBlockState(origin.offset(5, 0, 4)).block.descriptionId} ${level.getBlockState(origin.offset(5, 0, 5)).block.descriptionId}")
                        log("assembly ore helm menu ${s.playerList.players[0].openMenu(level.getBlockEntity(helm) as org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity)}")
                    }
                    context.waitTicks(10)
                    context.clickScreenButton("gui.vs_eureka.assemble")
                    context.waitTicks(20)
                    log("assembly ore after breaking " + context.computeOnClient<String, RuntimeException> { mc ->
                        val screen = mc.gui.screen() as? org.valkyrienskies.eureka.gui.assembly.ShipAssemblyScreen
                        "blocks=${screen?.blockCount()} red=${screen?.worldGeneratedCount()} canAssemble=${screen?.canAssemble()}"
                    })
                    context.clickScreenButton("gui.vs_eureka.assembly.confirm")
                    context.waitTicks(30)
                    var assembled = false
                    server.runOnServer<RuntimeException> { s -> assembled = s.overworld().getBlockState(helm).isAir }
                    if (assembled) oreHelm = helm
                }
                context.waitTicks(30)
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    val ships = level.shipObjectWorld.allShips
                    log("assembly $case ships after=${ships.size} helm still in world=${level.getBlockState(helm).block.descriptionId}")
                }
                log("assembly $case screen after=${context.computeOnClient<String, RuntimeException> { it.gui.screen()?.javaClass?.simpleName ?: "none" }}")
            }

            // A block placed with a real right click counts as the player's, one set by the world doesn't
            val dockDeck = BlockPos(120 + 8, 10, 3)
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.BIRCH_PLANKS, 8))
                p.teleportTo(s.overworld(), dockDeck.x + 0.5, dockDeck.y + 1.0, dockDeck.z + 2.5, setOf(), 180f, 40f, true)
                s.overworld().setBlock(BlockPos(120 + 10, 11, 5), Blocks.STONE.defaultBlockState(), 3)
            }
            context.waitTicks(15)
            context.runOnClient<RuntimeException> { mc ->
                val eye = mc.player!!.getEyePosition(1f)
                val dx = dockDeck.x + 0.5 - eye.x; val dy = dockDeck.y + 1.0 - eye.y; val dz = dockDeck.z + 0.5 - eye.z
                mc.player!!.yRot = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
                mc.player!!.xRot = Math.toDegrees(-Math.atan2(dy, hypot(dx, dz))).toFloat()
            }
            context.waitTicks(3)
            context.input.pressKey { it.keyUse }
            context.waitTicks(6)
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val placed = dockDeck.above()
                log("assembly placing ${level.getBlockState(placed).block.descriptionId} placed by player=${org.valkyrienskies.mod.common.PlacedBlocks.isPlaced(level, placed)}" +
                    " stone set by the world placed by player=${org.valkyrienskies.mod.common.PlacedBlocks.isPlaced(level, BlockPos(120 + 10, 11, 5))}")
            }

            // The commands, typed by the player
            fun ships() = context.computeOnClient<Int, RuntimeException> { 0 }.let {
                var n = 0
                server.runOnServer<RuntimeException> { s -> n = s.overworld().shipObjectWorld.allShips.size }
                n
            }
            val ore = oreHelm
            log("assembly commands ships=${ships()} ore boat assembled=${ore != null}")
            if (ore != null) {
                server.runOnServer<RuntimeException> { s ->
                    val p = s.playerList.players[0]
                    p.teleportTo(s.overworld(), ore.x + 0.5, ore.y + 20.0, ore.z + 0.5, setOf(), 0f, 0f, true)
                }
                context.waitTicks(10)
                // Commands need operator rights, the test player isn't an operator
                server.runCommand("execute as @p at @p run vs build unassemble 120")
                context.waitTicks(20)
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    // Where did the helm land, and is it the player's
                    var found: BlockPos? = null
                    for (pos in BlockPos.betweenClosed(ore.offset(-12, -80, -12), ore.offset(12, 4, 12))) {
                        if (level.getBlockState(pos).block.descriptionId == "block.vs_eureka.oak_ship_helm") { found = pos.immutable(); break }
                    }
                    log("assembly unassembled helm at=$found placed by player=${found?.let { org.valkyrienskies.mod.common.PlacedBlocks.isPlaced(level, it) }}")
                }
                log("assembly after unassemble 120 ships=${ships()}")
            }
            server.runCommand("execute as @p at @p run vs build delete all")
            context.waitTicks(20)
            log("assembly after delete all ships=${ships()}")
        }
        log("assembly test finished")
    }

    @Volatile
    private var oreHelm: BlockPos? = null

    private fun lookTarget(context: ClientGameTestContext): String = context.computeOnClient<String, RuntimeException> { mc ->
        (mc.hitResult as? net.minecraft.world.phys.BlockHitResult)?.blockPos?.let { "$it ${mc.level!!.getBlockState(it).block.descriptionId}" } ?: "${mc.hitResult?.type}"
    }

    // A small boat floating in the air, on its own, touching a dock, or touching a block far too big for a ship
    private fun buildBoat(level: ServerLevel, origin: BlockPos, case: String): BlockPos {
        // The test builds with setBlock, these stand in for blocks a player placed
        fun put(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState, flags: Int = 3) {
            level.setBlock(pos, state, flags)
            org.valkyrienskies.mod.common.PlacedBlocks.markPlaced(level, pos)
        }
        val planks = Blocks.SPRUCE_PLANKS.defaultBlockState()
        for (x in 0 until 5) for (z in 0 until 9) {
            put(origin.offset(x, 0, z), planks)
            if (x == 0 || x == 4 || z == 0 || z == 8) put(origin.offset(x, 1, z), Blocks.OAK_FENCE.defaultBlockState())
        }
        put(origin.offset(2, -1, 4), block("vs_eureka:balloon").defaultBlockState())
        val helm = origin.offset(2, 1, 6)
        put(helm, block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
        if (case == "boat") put(origin.offset(2, 1, 2), block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH))
        when (case) {
            "dock" -> {
                // A dock right next to the boat, touching its side
                for (x in 5 until 13) for (z in 2 until 7) put(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState())
                for (x in 6 until 13 step 3) for (y in -6 until 0) put(origin.offset(x, y, 4), Blocks.OAK_LOG.defaultBlockState())
            }
            "ore" -> {
                // Something no player placed, touching the side of the boat
                level.setBlock(origin.offset(5, 0, 4), Blocks.COAL_ORE.defaultBlockState(), 3)
                level.setBlock(origin.offset(5, 0, 5), Blocks.OAK_LOG.defaultBlockState(), 3)
            }
            "huge" -> {
                // Far more blocks than a ship may have, touching the back of the boat
                val big = Blocks.OAK_PLANKS.defaultBlockState()
                for (x in -14 until 19) for (y in -30 until 1) for (z in 9 until 42) put(origin.offset(x, y, z), big, 2)
            }
        }
        return helm
    }

    /** Placed blocks have to be remembered after the world is saved and opened again, like a server restart. */
    fun placedSaveTest(context: ClientGameTestContext) {
        val spot = BlockPos(3, -60, 3)
        val worldStone = BlockPos(5, -60, 3)
        val save = context.worldBuilder().create().use { world ->
            val server = world.server
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                p.setGameMode(GameType.CREATIVE)
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.OAK_PLANKS, 4))
                p.teleportTo(s.overworld(), spot.x + 0.5, spot.y.toDouble(), spot.z + 2.5, setOf(), 180f, 50f, true)
                s.overworld().setBlock(worldStone, Blocks.STONE.defaultBlockState(), 3)
            }
            world.connection.waitForChunksRender()
            context.waitTicks(10)
            context.runOnClient<RuntimeException> { mc ->
                val eye = mc.player!!.getEyePosition(1f)
                val dx = spot.x + 0.5 - eye.x; val dy = spot.y - eye.y; val dz = spot.z + 0.5 - eye.z
                mc.player!!.yRot = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
                mc.player!!.xRot = Math.toDegrees(-Math.atan2(dy, hypot(dx, dz))).toFloat()
            }
            context.waitTicks(3)
            context.input.pressKey { it.keyUse }
            context.waitTicks(10)
            server.runOnServer<RuntimeException> { s -> log("placedsave before ${placedState(s.overworld(), spot, worldStone)}") }
            world.worldSave
        }
        save.open().use { world ->
            context.waitTicks(20)
            world.server.runOnServer<RuntimeException> { s -> log("placedsave after reopening ${placedState(s.overworld(), spot, worldStone)}") }
        }
        log("placedsave finished")
    }

    private fun placedState(level: ServerLevel, spot: BlockPos, worldStone: BlockPos): String {
        val placed = level.getBlockState(spot)
        val stone = level.getBlockState(worldStone)
        return "placed=${placed.block.descriptionId} byPlayer=${org.valkyrienskies.mod.common.PlacedBlocks.isPlaced(level, spot)} " +
            "worldGenerated=${org.valkyrienskies.mod.common.PlacedBlocks.isWorldGenerated(level, spot, placed)} | " +
            "stone byPlayer=${org.valkyrienskies.mod.common.PlacedBlocks.isPlaced(level, worldStone)} " +
            "worldGenerated=${org.valkyrienskies.mod.common.PlacedBlocks.isWorldGenerated(level, worldStone, stone)}"
    }

    /** The cannonball recipes, what a cannon will take, and how big each size of cannonball blows up. */
    fun cannonballTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val iron = ItemStack(Items.IRON_INGOT); val tnt = ItemStack(Items.TNT); val gun = ItemStack(Items.GUNPOWDER)
                val e = ItemStack.EMPTY
                val small = ItemStack(org.valkyrienskies.eureka.EurekaItems.SMALL_CANNONBALL.get())
                val medium = ItemStack(org.valkyrienskies.eureka.EurekaItems.MEDIUM_CANNONBALL.get())
                fun craft(tag: String, vararg grid: ItemStack) {
                    val input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid.map { it.copy() })
                    val found = s.recipeManager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                    val result = found.map { it.value().assemble(input) }.orElse(ItemStack.EMPTY)
                    log("cannonball recipe $tag -> ${if (result.isEmpty) "nothing" else "${result.count} ${result.item}"} (stacks to ${result.maxStackSize})")
                }
                craft("small", e, iron, e, iron, tnt, iron, e, iron, e)
                craft("medium", gun, iron, gun, iron, small, iron, gun, iron, gun)
                craft("large", tnt, iron, tnt, iron, medium, iron, tnt, iron, tnt)
                craft("small with iron in the corners", iron, e, iron, e, tnt, e, iron, e, iron)
                craft("medium without the small one", gun, iron, gun, iron, tnt, iron, gun, iron, gun)
            }

            // Fire each size at a wall of planks and count the blocks it takes out
            val cannon = BlockPos(0, -50, 0)
            fun wall(level: ServerLevel) {
                for (x in 12..21) for (y in -62..-38) for (z in -12..12) {
                    level.setBlock(BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 2)
                }
                for (e in level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java, net.minecraft.world.phys.AABB(-5.0, -70.0, -20.0, 40.0, -20.0, 20.0))) e.discard()
            }
            fun count(level: ServerLevel): Int {
                var n = 0
                for (x in 12..21) for (y in -62..-38) for (z in -12..12) if (!level.getBlockState(BlockPos(x, y, z)).`is`(Blocks.OAK_PLANKS)) n++
                return n
            }
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                for (y in -60 until -50) level.setBlock(BlockPos(0, y, 0), Blocks.STONE.defaultBlockState(), 3)
                level.setBlock(cannon, block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST), 3)
                val be = level.getBlockEntity(cannon) as CannonBlockEntity
                log("cannonball cannon takes tnt=${be.canPlaceItem(0, ItemStack(Items.TNT))} small=${be.canPlaceItem(0, ItemStack(org.valkyrienskies.eureka.EurekaItems.SMALL_CANNONBALL.get()))}")
                val p = s.playerList.players[0]
                p.setGameMode(GameType.SPECTATOR)
                p.teleportTo(level, 6.5, -30.0, -24.0, setOf(), 0f, 30f, true)
            }
            world.connection.waitForChunksRender()
            val shots = listOf(
                "tnt" to ItemStack(Items.TNT),
                "small" to ItemStack(org.valkyrienskies.eureka.EurekaItems.SMALL_CANNONBALL.get()),
                "medium" to ItemStack(org.valkyrienskies.eureka.EurekaItems.MEDIUM_CANNONBALL.get()),
                "large" to ItemStack(org.valkyrienskies.eureka.EurekaItems.LARGE_CANNONBALL.get())
            )
            for ((name, ammo) in shots) {
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    wall(level)
                    val be = level.getBlockEntity(cannon) as CannonBlockEntity
                    for (i in 0 until be.containerSize) be.setItem(i, ItemStack.EMPTY)
                    be.setItem(0, ammo.copy())
                    level.setBlock(cannon.below(2), Blocks.STONE.defaultBlockState(), 3)
                    level.setBlock(cannon.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3)
                }
                context.waitTicks(40)
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    val be = level.getBlockEntity(cannon) as CannonBlockEntity
                    log("cannonball shot $name: left in cannon=${be.getItem(0).count} blocks blown out of the wall=${count(level)}")
                    level.setBlock(cannon.north(), Blocks.AIR.defaultBlockState(), 3)
                }
                DevClientTest.shotOf(context, "cannonball_$name")
                context.waitTicks(10)
            }
        }
        log("cannonball test finished")
    }

    @Volatile
    private var drawnKinds: Map<String, Int> = emptyMap()
    private var countingDrawn = false

    // Blocks drawn apart from the chunk mesh, each one has to keep showing on a ship
    private val drawnExtras = listOf(
        "minecraft:chest", "minecraft:oak_sign", "minecraft:white_banner", "minecraft:bell", "minecraft:skeleton_skull",
        "minecraft:lectern", "minecraft:enchanting_table", "minecraft:campfire", "minecraft:decorated_pot", "minecraft:shulker_box"
    )

    /** The helm's wheel, the cannon's barrel and every other block drawn apart from the chunk have to stay after assembling. */
    fun helmWheelTest(context: ClientGameTestContext) {
        if (!countingDrawn) {
            countingDrawn = true
            net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION.register(
                net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.EndExtraction { ctx ->
                    drawnKinds = ctx.levelState().blockEntityRenderStates.groupingBy { it.javaClass.simpleName }.eachCount()
                }
            )
        }
        context.worldBuilder().create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            lateinit var helm: BlockPos
            lateinit var middle: BlockPos
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val origin = BlockPos(0, 0, 0)
                helm = buildBoat(level, origin, "boat")
                for (x in 0 until 5) for (z in 0 until 9) {
                    level.setBlock(origin.offset(x, -1, z), block("vs_eureka:balloon").defaultBlockState(), 3)
                    org.valkyrienskies.mod.common.PlacedBlocks.markPlaced(level, origin.offset(x, -1, z))
                }
                fun put(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
                    level.setBlock(pos, state, 3)
                    org.valkyrienskies.mod.common.PlacedBlocks.markPlaced(level, pos)
                }
                put(origin.offset(2, 1, 2), block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH))
                for ((i, id) in drawnExtras.withIndex()) {
                    val pos = origin.offset(1 + (i % 3), 1, 3 + i / 3).let { if (it == helm) it.east() else it }
                    if (pos == helm || pos == origin.offset(2, 1, 2)) continue
                    put(pos, block(id).defaultBlockState())
                }
                middle = origin.offset(2, 1, 4)
                s.playerList.players[0].setGameMode(GameType.SPECTATOR)
            }
            // Front, behind, from above and from further away
            val views = listOf(
                "near" to Vector3d(3.0, 2.0, 5.0), "behind" to Vector3d(-4.0, 3.0, -6.0),
                "above" to Vector3d(0.5, 9.0, 0.5), "far" to Vector3d(22.0, 10.0, 24.0)
            )
            fun look(tag: String) {
                for ((name, offset) in views) {
                    server.runOnServer<RuntimeException> { s ->
                        val level = s.overworld()
                        val ship = level.shipObjectWorld.allShips.firstOrNull()
                        val target = Vector3d(middle.x + 0.5, middle.y + 0.5, middle.z + 0.5)
                        ship?.shipToWorld?.transformPosition(target)
                        val eye = Vector3d(target).add(offset)
                        val d = Vector3d(target).sub(eye)
                        val yaw = Math.toDegrees(Math.atan2(-d.x, d.z)).toFloat()
                        val pitch = Math.toDegrees(-Math.atan2(d.y, hypot(d.x, d.z))).toFloat()
                        s.playerList.players[0].teleportTo(level, eye.x, eye.y - 1.62, eye.z, setOf(), yaw, pitch, true)
                    }
                    context.waitTicks(15)
                    world.connection.waitForChunksRender()
                    context.waitTicks(10)
                    log("helmwheel $tag $name drawn ${drawnKinds.toSortedMap()}")
                    DevClientTest.shotOf(context, "helmwheel_${tag}_$name")
                }
            }
            context.runOnClient<RuntimeException> { mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) }
            look("before")
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                (level.getBlockEntity(helm) as org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity).assemble(s.playerList.players[0])
            }
            context.waitTicks(20)
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val ship = level.shipObjectWorld.allShips.first()
                val offset = ship.transform.positionInShip
                // Where the middle of the boat ended up in the shipyard
                val box = ship.shipAABB!!
                middle = BlockPos(box.minX() + 2, box.minY() + 2, box.minZ() + 4)
                log("helmwheel assembled ${ship.slug} middle now $middle")
            }
            look("after")
            context.waitTicks(100)
            look("later")
        }
        log("helmwheel test finished")
    }

    /** Stands still in a normal world without any ship and writes down the frame rate, for profiling. */
    fun idleTest(context: ClientGameTestContext) {
        context.worldBuilder().setUseConsistentSettings(false).create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            context.runOnClient<RuntimeException> { mc ->
                mc.options.renderDistance().set(12)
                mc.options.framerateLimit().set(260)
                mc.options.enableVsync().set(false)
                if (!mc.gui.hud.isHidden) mc.gui.hud.toggle()
            }
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                p.setGameMode(GameType.SPECTATOR)
                p.teleportTo(s.overworld(), 0.5, 110.0, 0.5, setOf(), 30f, 20f, true)
            }
            // Let the chunks around load and build
            context.waitTicks(500)
            log("idle measuring")
            for (i in 1..8) {
                context.waitTicks(100)
                log("idle t=${i * 5}s fps=${context.computeOnClient<Int, RuntimeException> { it.fps }}")
            }
        }
        log("idle test finished")
    }

    /**
     * Physics only gets the terrain near ships. A ship dropped onto ground nobody went near must land on it, and one
     * sliding fast along the ground must not run off the terrain physics has.
     */
    fun groundTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val server = world.server
            context.runOnClient<RuntimeException> { mc -> mc.options.renderDistance().set(8) }
            for ((case, spot) in listOf("drop" to BlockPos(900, 0, 900), "fall" to BlockPos(-900, 0, 900), "slide" to BlockPos(900, 0, -900))) {
                server.runOnServer<RuntimeException> { s ->
                    val p = s.playerList.players[0]
                    p.setGameMode(GameType.SPECTATOR)
                    p.teleportTo(s.overworld(), spot.x + 0.5, 140.0, spot.z + 0.5, setOf(), 0f, 60f, true)
                }
                context.waitTicks(200)
                var ship: ServerShip? = null
                var ground = 0
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.x, spot.z)
                    val start = spot.atY(ground + if (case == "fall") 90 else if (case == "slide") 2 else 10)
                    for (x in 0 until 3) for (z in 0 until 3) level.setBlock(start.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3)
                    val helm = start.offset(1, 1, 1)
                    level.setBlock(helm, block("vs_eureka:oak_ship_helm").defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3)
                    ship = ShipAssembler.collectBlocks(level, helm) { !it.isAir }
                    log("ground $case ground=$ground start y=${start.y} ship=${ship != null} physics terrain known here=" +
                        (level as org.valkyrienskies.mod.common.util.VSServerLevel).isChunkKnownToPhysics(spot.x shr 4, spot.z shr 4))
                }
                context.waitTicks(5)
                if (case == "slide") server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    val loaded = level.shipObjectWorld.loadedShips.getById(ship!!.id) ?: return@runOnServer
                    val data = org.valkyrienskies.mod.common.vsCore.newShipTeleportData(
                        newPos = Vector3d(loaded.transform.positionInWorld), newVel = Vector3d(0.0, 0.0, 60.0)
                    )
                    org.valkyrienskies.mod.common.vsCore.teleportShip(level.shipObjectWorld, loaded, data)
                }
                var lowest = Double.MAX_VALUE
                for (half in 1..16) {
                    context.waitTicks(10)
                    server.runOnServer<RuntimeException> { s ->
                        val level = s.overworld()
                        val loaded = level.shipObjectWorld.loadedShips.getById(ship!!.id) ?: return@runOnServer
                        val box = loaded.worldAABB
                        val cx = ((box.minX() + box.maxX()) / 2).toInt()
                        val cz = ((box.minZ() + box.maxZ()) / 2).toInt()
                        val under = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz)
                        val sink = under - box.minY()
                        lowest = minOf(lowest, box.minY() - under)
                        val p = s.playerList.players[0]
                        p.teleportTo(level, cx + 0.5, box.maxY() + 25.0, cz + 0.5, setOf(), 0f, 60f, true)
                        if (half % 2 == 0) log("ground $case t=${half / 2}s bottom=%.2f groundThere=%d sunkIn=%.2f speed=%.1f at x=%d z=%d"
                            .format(box.minY(), under, sink, loaded.velocity.length(), cx, cz))
                    }
                }
                log("ground $case result ${if (lowest > -1.0) "ON GROUND" else "FELL THROUGH"} lowest above ground=%.2f".format(lowest))
            }
        }
        log("ground test finished")
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
                for (second in 1..(System.getenv("VS_TEST_SPEED_SECONDS") ?: "30").toInt()) {
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
                if (System.getenv("VS_TEST_TURN") != null) {
                    // Hold left for 3 seconds, let go and see how fast the turning stops, going flat out and then standing
                    for ((phase, forward) in listOf("moving" to 1f, "still" to 0f)) {
                        fun drive(left: Float) = server.runOnServer<RuntimeException> { s ->
                            val loaded = s.overworld().shipObjectWorld.loadedShips.getById(ship!!.id) as? LoadedServerShip ?: return@runOnServer
                            val driver = loaded.getAttachment(SeatedControllingPlayer::class.java) ?: return@runOnServer
                            driver.forwardImpulse = forward
                            driver.leftImpulse = left
                        }
                        fun turnRate(tag: String) = server.runOnServer<RuntimeException> { s ->
                            val level = s.overworld()
                            val loaded = level.shipObjectWorld.loadedShips.getById(ship!!.id) ?: return@runOnServer
                            val pos = loaded.transform.positionInWorld
                            s.playerList.players[0].teleportTo(level, pos.x(), pos.y() + 15.0, pos.z(), setOf(), 180f, 30f, true)
                            log("turn ${run.name} $phase $tag yaw rate=%.1f deg/s speed=%.1f".format(Math.toDegrees(loaded.angularVelocity.y()), loaded.velocity.length()))
                        }
                        if (forward == 0f) { drive(0f); context.waitTicks(200) }
                        drive(1f)
                        for (t in 1..6) { context.waitTicks(10); turnRate("holding left ${t * 0.5}s") }
                        drive(0f)
                        for (t in 1..12) { context.waitTicks(10); turnRate("let go ${t * 0.5}s") }
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
