package org.valkyrienskies.mod.dev

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import org.valkyrienskies.eureka.gui.shiphelm.ShipHelmScreen
import org.valkyrienskies.eureka.gui.wiring.ShipWiringScreen
import org.valkyrienskies.eureka.gui.wiring.WiringCamera
import org.valkyrienskies.eureka.wiring.WiringConnectPayload
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.client.gui.screens.TitleScreen
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import java.util.concurrent.TimeUnit
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.server.IntegratedServer
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.recipe.RecipeIngredientRole
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionHand
import net.minecraft.network.Connection
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.eureka.ship.EurekaShipControl
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl
import org.valkyrienskies.mod.mixinducks.client.render.EntityRenderStateDuck

/** Development only, never shipped: plays through ship basics in a real client and takes screenshots. */
class DevClientTest : FabricClientGameTest {
    private val LAN_PORT = 25599

    private fun log(s: String) = println("SELFTEST $s")

    // The test framework redraws outside a normal frame for its screenshots, which Sodium 0.9 does not allow
    private fun shot(context: ClientGameTestContext, name: String) {
        if (System.getenv("VS_TEST_SODIUM") == null) {
            context.takeScreenshot(name)
            return
        }
        context.runOnClient<RuntimeException> { mc ->
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "$name.png", mc.gameRenderer.mainRenderTarget(), 1) { }
        }
        context.waitTicks(5)
    }

    // Joins a real dedicated server running in its own process, see DevSelfTest.join
    private fun joinTest(context: ClientGameTestContext) {
        fun positions(mc: Minecraft) {
            for (ship in mc.level!!.shipObjectWorld.loadedShips) log("join client ${ship.slug} render=${ship.renderTransform.positionInWorld}")
        }
        context.runOnClient<RuntimeException> { mc ->
            ConnectScreen.startConnecting(
                TitleScreen(), mc, ServerAddress("localhost", 25565),
                ServerData("test", "localhost", ServerData.Type.OTHER), false, null
            )
        }
        context.waitFor({ mc -> mc.level != null && mc.player != null }, 2400)
        log("join connected")
        context.waitFor({ mc -> mc.level!!.shipObjectWorld.loadedShips.size >= 2 }, 2400)
        context.runOnClient<RuntimeException> { mc ->
            log("join client ships=${mc.level!!.shipObjectWorld.loadedShips.size}")
            positions(mc)
        }
        context.waitTicks(60)
        context.runOnClient<RuntimeException> { mc -> positions(mc) }
        shot(context, "join_1_assembled")
        context.waitFor({ mc -> mc.player!!.vehicle != null }, 1200)
        log("join seated")
        context.waitTicks(20)
        context.input.holdKey { it.keyUp }
        context.waitTicks(100)
        context.input.releaseKey { it.keyUp }
        context.runOnClient<RuntimeException> { mc -> positions(mc) }
        shot(context, "join_2_flying")
        context.input.holdKey { it.keyLeft }
        context.waitTicks(40)
        context.input.releaseKey { it.keyLeft }
        context.input.holdKey { it.keyShift }
        context.waitTicks(10)
        context.input.releaseKey { it.keyShift }
        context.waitTicks(20)
        context.runOnClient<RuntimeException> { mc -> log("join dismounted vehicle=${mc.player!!.vehicle}") }
        shot(context, "join_3_dismounted")
        // The server stops on its own once it has finished its side of the test
        context.waitFor({ mc -> mc.level == null }, 2400)
        context.runOnClient<RuntimeException> { mc -> mc.gui.setScreen(TitleScreen()) }
        context.waitTicks(5)
        log("join finished")
    }

    // Moves the mouse right then down, and logs which way the view went. Positive numbers mean right and down.
    private fun mouseTurn(context: ClientGameTestContext, tag: String) {
        fun look() = context.computeOnClient<Vector3d, RuntimeException> { mc ->
            val f = mc.gameRenderer.mainCamera().forwardVector()
            Vector3d(f.x().toDouble(), f.y().toDouble(), f.z().toDouble())
        }
        // The game ignores the first move after it grabs the mouse
        context.input.moveCursor(1.0, 0.0)
        context.waitTicks(2)
        val start = look()
        context.input.moveCursor(120.0, 0.0)
        context.waitTicks(4)
        val turned = look()
        val right = -(start.x * turned.z - start.z * turned.x)
        context.input.moveCursor(0.0, 120.0)
        context.waitTicks(4)
        val down = turned.y - look().y
        log("mouse $tag right=${"%.3f".format(right)} down=${"%.3f".format(down)} look=$start")
        context.input.moveCursor(-120.0, -120.0)
        context.waitTicks(4)
    }

    private class Spread(val name: String) {
        private var min = Vector3d(Double.MAX_VALUE)
        private var max = Vector3d(-Double.MAX_VALUE)
        private var last: Vector3d? = null
        private var jump = 0.0
        private var frames = 0
        var mean = Vector3d()

        fun add(p: Vector3dc) {
            min.min(p)
            max.max(p)
            last?.let { jump = maxOf(jump, it.distance(p)) }
            last = Vector3d(p)
            mean.add(p)
            frames++
        }

        fun report(tag: String) {
            if (frames == 0) return
            val m = Vector3d(mean).div(frames.toDouble())
            log2("motion $tag $name frames=$frames spread=(${f(max.x - min.x)} ${f(max.y - min.y)} ${f(max.z - min.z)}) jump=${f(jump)} avg=(${f(m.x)} ${f(m.y)} ${f(m.z)})")
            min = Vector3d(Double.MAX_VALUE)
            max = Vector3d(-Double.MAX_VALUE)
            last = null
            jump = 0.0
            frames = 0
            mean = Vector3d()
        }

        private fun f(d: Double) = "%.4f".format(d)
        private fun log2(s: String) = println("SELFTEST $s")
    }

    // Measures where entities are drawn relative to the ship they stand on, every frame, while the ship moves
    private fun measureFrames() {
        val camera = Spread("camera")
        val tracked = LinkedHashMap<String, Spread>()
        var frames = 0
        LevelExtractionEvents.END_EXTRACTION.register(LevelExtractionEvents.EndExtraction { ctx ->
            val level = ctx.level()
            val ship = level.shipObjectWorld.loadedShips.firstOrNull() ?: return@EndExtraction
            val toShip = ship.renderTransform.worldToShip
            if (++frames % 120 == 0) {
                val tag = "f${frames / 120} v=${"%.1f".format(ship.velocity.length())} fps=${Minecraft.getInstance().fps}"
                for (spread in tracked.values) spread.report(tag)
                camera.report(tag)
            }
            val camPos = ctx.camera().position()
            camera.add(toShip.transformPosition(Vector3d(camPos.x, camPos.y, camPos.z)).sub(ship.transform.positionInShip))
            for (state in ctx.levelState().entityRenderStates) {
                val entity = (state as EntityRenderStateDuck).`vs$getRenderedEntity`() ?: continue
                val name = entity.customName?.string ?: (entity as? Player)?.name?.string ?: continue
                val local = toShip.transformPosition(Vector3d(state.x, state.y, state.z))
                local.sub(ship.transform.positionInShip)
                tracked.getOrPut(name) { Spread(name) }.add(local)
            }
        })
    }

    // Everything after this runs like normal play: the framework no longer keeps client and server in step
    private fun freeRun() {
        ThreadingImpl::class.java.getDeclaredMethod("deregisterTestThread").apply { isAccessible = true }.invoke(null)
    }

    private fun motionTest(context: ClientGameTestContext) {
        if (System.getenv("VS_TEST_BE_LAN") != null) watchDrawnBlockEntities(context, "host")
        measureFrames()
        context.worldBuilder().setUseConsistentSettings(false).create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            world.connection.waitForChunksRender()
            if (System.getenv("VS_TEST_PLAYERS") != null) {
                server.runOnServer<RuntimeException> { s ->
                    s.setUsesAuthentication(false)
                    log("motion lan open ${(s as IntegratedServer).publishServer(net.minecraft.server.MinecraftServer.MultiplayerScope.LAN, true, LAN_PORT)}")
                }
            }
            server.runOnServer<RuntimeException> { DevSelfTest.startMotion() }
            context.waitFor({ mc -> mc.level!!.shipObjectWorld.loadedShips.isNotEmpty() }, 4800)
            log("motion client sees ship")
            freeRun()
            while (!DevSelfTest.motionDone) Thread.sleep(100)
            log("motion finished")
            Thread.sleep(500)
            Runtime.getRuntime().halt(0)
        }
    }

    // Holds back everything on the connection, the way a friend's internet would
    private fun addLag(connection: Connection, lagMs: Long, jitterMs: Long) {
        val channel = Connection::class.java.getDeclaredField("channel").apply { isAccessible = true }.get(connection) as Channel
        val random = java.util.Random()
        class Delayed(val at: Long, val send: () -> Unit)

        fun delayLine(ctx: ChannelHandlerContext, line: java.util.ArrayDeque<Delayed>) {
            while (line.isNotEmpty() && line.peek().at <= System.nanoTime()) line.poll().send()
            if (line.isNotEmpty()) {
                ctx.executor().schedule({ delayLine(ctx, line) }, line.peek().at - System.nanoTime(), TimeUnit.NANOSECONDS)
            }
        }

        fun ready(line: java.util.ArrayDeque<Delayed>): Long {
            val at = System.nanoTime() + (lagMs + (random.nextDouble() * jitterMs).toLong()) * 1_000_000
            return maxOf(at, line.peekLast()?.at ?: 0L)
        }

        channel.pipeline().addFirst("vs_test_lag", object : ChannelDuplexHandler() {
            private val inbound = java.util.ArrayDeque<Delayed>()
            private val outbound = java.util.ArrayDeque<Delayed>()

            private fun enqueue(ctx: ChannelHandlerContext, line: java.util.ArrayDeque<Delayed>, send: () -> Unit) {
                val first = line.isEmpty()
                line.add(Delayed(ready(line), send))
                if (first) ctx.executor().schedule({ delayLine(ctx, line) }, line.peek().at - System.nanoTime(), TimeUnit.NANOSECONDS)
            }

            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                enqueue(ctx, inbound) { ctx.fireChannelRead(msg) }
            }

            override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
                enqueue(ctx, outbound) { ctx.writeAndFlush(msg, promise) }
            }
        })
    }

    // Wires a button up over a real network connection, where everything has to go through the packet codecs
    private fun lanWiring(context: ClientGameTestContext) {
        context.waitFor({ mc -> mc.gui.screen() is ShipHelmScreen }, 1200)
        context.waitTicks(10)
        context.clickScreenButton("gui.vs_eureka.wiring")
        context.waitFor({ mc -> wiringScreen(mc) != null }, 600)
        context.waitTicks(20)
        val button = context.computeOnClient<BlockPos, RuntimeException> { mc -> wiringScreen(mc)!!.buttons().first().pos }
        val cushion = context.computeOnClient<BlockPos, RuntimeException> { mc -> wiringScreen(mc)!!.contacts().first { !it.isHelm }.pos }
        log("wiring lan got ${context.computeOnClient<Int, RuntimeException> { mc -> wiringScreen(mc)!!.buttons().size }} buttons over the network")
        val dot = screenPixel(context, button)
        context.input.setCursorPos(dot[0], dot[1])
        context.waitTicks(3)
        context.input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitTicks(5)
        context.input.pressKey(InputConstants.getKey("key.keyboard.g"))
        context.waitFor({ mc -> wiringScreen(mc)!!.buttons().any { it.key == "key.keyboard.g" } }, 200)
        log("wiring lan key came back from the server")
        context.input.pressKey(InputConstants.getKey("key.keyboard.escape"))
        context.waitFor({ mc -> mc.player!!.vehicle != null }, 1200)
        context.waitTicks(10)
        context.input.pressKey(InputConstants.getKey("key.keyboard.g"))
        context.waitTicks(20)
        log("wiring lan pressed the key at the helm")

        // A cushion over a real connection, with real clicks
        val middle = Vector3d(cushion.x + 0.5, cushion.y + 0.12, cushion.z + 0.5)
        context.input.holdKey { it.keyShift }
        context.waitTicks(10)
        context.input.releaseKey { it.keyShift }
        context.waitFor({ mc -> mc.player!!.vehicle == null }, 400)
        context.waitTicks(30)
        aimAtShipPoint(context, middle)
        log("wiring lan crosshair ${lookingAt(context)}")
        context.input.pressKey { it.keyUse }
        context.waitFor({ mc -> mc.player!!.vehicle != null }, 200)
        log("wiring lan sat on ${context.computeOnClient<String, RuntimeException> { mc -> mc.player!!.vehicle!!.type.toString() }}")
        context.waitTicks(20)
        context.input.holdKey { it.keyShift }
        context.waitTicks(10)
        context.input.releaseKey { it.keyShift }
        context.waitFor({ mc -> mc.player!!.vehicle == null }, 400)
        context.waitTicks(30)
        aimAtShipPoint(context, middle)
        log("wiring lan crosshair ${lookingAt(context)}")
        context.input.pressKey { it.keyAttack }
        context.waitTicks(20)
        log("wiring lan hit the cushion")
    }

    // Second player on the world opened by motionTest, or the player on a dedicated server running DevSelfTest.motion
    private fun lanJoin(context: ClientGameTestContext) {
        measureFrames()
        val port = (System.getenv("VS_TEST_PORT") ?: "$LAN_PORT").toInt()
        context.runOnClient<RuntimeException> { mc ->
            ConnectScreen.startConnecting(
                TitleScreen(), mc, ServerAddress("localhost", port),
                ServerData("lan", "localhost:$port", ServerData.Type.LAN), false, null
            )
        }
        context.waitFor({ mc -> mc.level != null && mc.player != null }, 2400)
        log("motion joined lan")
        val lag = (System.getenv("VS_TEST_LAG") ?: "0").toLong()
        context.runOnClient<RuntimeException> { mc -> addLag(mc.connection!!.connection, lag / 2, lag / 8) }
        context.waitFor({ mc -> mc.level!!.shipObjectWorld.loadedShips.isNotEmpty() }, 2400)
        log("motion client sees ship")
        if (System.getenv("VS_TEST_WIRING_LAN") != null) lanWiring(context)
        if (System.getenv("VS_TEST_BE_LAN") != null) watchDrawnBlockEntities(context, "joined")
        freeRun()
        val start = System.currentTimeMillis()
        val walk = System.getenv("VS_TEST_WALK") != null
        val mc = Minecraft.getInstance()
        while (System.currentTimeMillis() - start < 100_000 && mc.level != null) {
            if (walk) {
                val phase = (System.currentTimeMillis() - start) / 1500 % 4
                mc.execute {
                    mc.options.keyUp.setDown(phase == 1L)
                    mc.options.keyDown.setDown(phase == 3L)
                }
            }
            Thread.sleep(100)
        }
        log("motion finished")
        Thread.sleep(500)
        Runtime.getRuntime().halt(0)
    }

    @Volatile
    private var drawnOnShip: Map<String, Int> = emptyMap()

    // Counts the block entities drawn each frame for a while, and takes pictures of the deck
    private fun watchDrawnBlockEntities(context: ClientGameTestContext, tag: String) {
        LevelExtractionEvents.END_EXTRACTION.register(LevelExtractionEvents.EndExtraction { ctx ->
            drawnOnShip = ctx.levelState().blockEntityRenderStates.groupingBy { it.javaClass.simpleName.removeSuffix("RenderState") }.eachCount()
        })
        Thread {
            for (i in 1..12) {
                Thread.sleep(5000)
                val mc = Minecraft.getInstance()
                val ships = mc.level?.shipObjectWorld?.loadedShips?.size
                log("bewatch $tag t=${i * 5}s ships=$ships drawn=${drawnOnShip.toSortedMap()}")
                if (i == 6 || i == 11) mc.execute {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory, "bewatch_${tag}_$i.png", mc.gameRenderer.mainRenderTarget(), 1) { }
                }
            }
        }.start()
    }

    // Turns the player to look at a spot on the ship, from there the crosshair does the work as it does for a real player
    private fun aimAtShipPoint(context: ClientGameTestContext, inShip: Vector3d) {
        context.runOnClient<RuntimeException> { mc ->
            val player = mc.player!!
            val ships = mc.level!!.shipObjectWorld.loadedShips
            val ship = DevSelfTest.lifted?.let { ships.getById(it.id) } ?: ships.first()
            val target = ship.renderTransform.shipToWorld.transformPosition(Vector3d(inShip))
            val eye = player.getEyePosition(1f)
            val dx = target.x - eye.x
            val dy = target.y - eye.y
            val dz = target.z - eye.z
            player.yRot = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
            player.xRot = Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz))).toFloat()
        }
        context.waitTicks(4)
    }

    private fun lookingAt(context: ClientGameTestContext): String = context.computeOnClient<String, RuntimeException> { mc ->
        val hit = mc.hitResult
        if (hit is net.minecraft.world.phys.EntityHitResult) "entity ${hit.entity.type}" else "${hit?.type}"
    }

    // Everything a player does with a cushion on a ship, with real clicks: sit, drive, get up and break it
    private fun cushionCheck(context: ClientGameTestContext, server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) {
        var seat = BlockPos.ZERO
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            seat = DevSelfTest.seatPos(level)
            log("cushion placed ${DevSelfTest.placeCushion(level)}")
            s.playerList.players[0].setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            DevSelfTest.standNearSeat(level, 2)
        }
        context.waitTicks(10)
        val middle = Vector3d(seat.x + 0.5, seat.y + 0.12, seat.z + 0.5)

        aimAtShipPoint(context, middle)
        log("cushion crosshair ${lookingAt(context)}")
        context.input.pressKey { it.keyUse }
        context.waitTicks(10)
        server.runOnServer<RuntimeException> { s ->
            val p = s.playerList.players[0]
            log("cushion right click vehicle=${p.vehicle?.type} driving=${DevSelfTest.driving(s.overworld())}")
        }
        context.runOnClient<RuntimeException> { mc ->
            val data = org.valkyrienskies.mod.common.getShipMountedToData(mc.player!!, 1f)
            log("cushion feet=${data?.let { "%.3f".format(it.mountPosInShip.y() - DevSelfTest.helmY) }}")
            mc.options.cameraType = CameraType.THIRD_PERSON_BACK
        }
        context.waitTicks(10)
        shot(context, "06b_on_cushion")
        context.runOnClient<RuntimeException> { mc -> mc.options.cameraType = CameraType.FIRST_PERSON }
        server.runOnServer<RuntimeException> { s -> DevSelfTest.report(s.overworld()) }
        context.input.holdKey { it.keyUp }
        context.waitTicks(60)
        context.input.releaseKey { it.keyUp }
        server.runOnServer<RuntimeException> { s -> DevSelfTest.report(s.overworld()) }
        context.input.holdKey { it.keyShift }
        context.waitTicks(10)
        context.input.releaseKey { it.keyShift }

        server.runOnServer<RuntimeException> { s -> DevSelfTest.standNearSeat(s.overworld(), 2) }
        context.waitTicks(10)
        aimAtShipPoint(context, middle)
        log("cushion crosshair ${lookingAt(context)}")
        context.input.pressKey { it.keyAttack }
        context.waitTicks(10)
        server.runOnServer<RuntimeException> { s -> log("cushion left click still there=${DevSelfTest.cushion(s.overworld()) != null}") }

        // A cushion away from the helm is just a seat
        val spare = seat.relative(net.minecraft.core.Direction.EAST, 4)
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            log("cushion spare placed ${DevSelfTest.placeCushion(level, spare)}")
            s.playerList.players[0].setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            DevSelfTest.standNearSeat(level, 2)
        }
        context.waitTicks(10)
        val spareMiddle = Vector3d(spare.x + 0.5, spare.y + 0.12, spare.z + 0.5)
        aimAtShipPoint(context, spareMiddle)
        log("cushion spare crosshair ${lookingAt(context)}")
        context.input.pressKey { it.keyUse }
        context.waitTicks(10)
        server.runOnServer<RuntimeException> { s ->
            val p = s.playerList.players[0]
            log("cushion spare right click vehicle=${p.vehicle?.type} driving=${DevSelfTest.driving(s.overworld())}")
        }
        context.input.holdKey { it.keyShift }
        context.waitTicks(10)
        context.input.releaseKey { it.keyShift }
        server.runOnServer<RuntimeException> { s -> DevSelfTest.standNearSeat(s.overworld(), 2) }
        context.waitTicks(10)
        aimAtShipPoint(context, spareMiddle)
        context.input.pressKey { it.keyAttack }
        context.waitTicks(10)
        server.runOnServer<RuntimeException> { s ->
            log("cushion spare left click still there=${DevSelfTest.cushion(s.overworld(), spare) != null}")
            DevSelfTest.removeCushion(s.overworld())
        }
        context.waitTicks(5)
    }

    private fun wiringScreen(mc: Minecraft) = mc.gui.screen() as? ShipWiringScreen

    private fun screenPixel(context: ClientGameTestContext, at: BlockPos): DoubleArray = context.computeOnClient<DoubleArray, RuntimeException> { mc ->
        val marker = wiringScreen(mc)!!.markerPosition(at)!!
        val scale = mc.window.screenWidth.toDouble() / mc.window.guiScaledWidth
        doubleArrayOf(marker[0] * scale, marker[1] * scale)
    }

    // The whole wireless button story the way a player goes through it
    private fun wiringCheck(context: ClientGameTestContext, server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) {
        val g = InputConstants.getKey("key.keyboard.g")
        val j = InputConstants.getKey("key.keyboard.j")
        lateinit var rig: DevSelfTest.WiringRig
        fun state(tag: String) = server.runOnServer<RuntimeException> { s -> log("wiring $tag ${DevSelfTest.wiringState(s.overworld(), rig)}") }

        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            rig = DevSelfTest.buildWiringRig(level)
            log("wiring cushion placed ${DevSelfTest.placeCushion(level, rig.cushion)}")
            val p = s.playerList.players[0]
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            DevSelfTest.standNearSeat(level, 2)
        }
        context.waitTicks(20)
        aimAtShipPoint(context, Vector3d(rig.shown.x + 0.5, rig.shown.y + 0.1, rig.shown.z + 0.5))
        shot(context, "new_blocks")
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val ship = level.shipObjectWorld.loadedShips.getById(DevSelfTest.lifted!!.id)!!
            val spot = ship.shipToWorld.transformPosition(Vector3d(rig.cannon.x + 1.8, rig.cannon.y + 0.05, rig.cannon.z + 2.0))
            s.playerList.players[0].teleportTo(spot.x, spot.y, spot.z)
        }
        context.waitTicks(10)
        aimAtShipPoint(context, Vector3d(rig.cannon.x + 0.5, rig.cannon.y + 0.5, rig.cannon.z + 0.5))
        shot(context, "cannon_muzzle")

        // Into the helm and through the new button
        server.runOnServer<RuntimeException> { s -> log("wiring helm menu ${s.playerList.players[0].openMenu(DevSelfTest.helm(s.overworld())!!)}") }
        context.waitTicks(10)
        shot(context, "wiring_helm_button")
        context.clickScreenButton("gui.vs_eureka.wiring")
        context.waitTicks(20)
        context.runOnClient<RuntimeException> { mc -> log("wiring screen open=${wiringScreen(mc) != null} hud hidden=${mc.gui.hud.isHidden} camera detached=${mc.gameRenderer.mainCamera().isDetached}") }
        shot(context, "wiring_screen")

        // Click a red dot and press a key
        val lampDot = screenPixel(context, rig.lampButton)
        context.input.setCursorPos(lampDot[0], lampDot[1])
        context.waitTicks(3)
        context.input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitTicks(5)
        shot(context, "wiring_binding")
        context.input.pressKey(g)
        context.waitTicks(5)
        state("after binding")

        // Fly in, it has to keep showing the same ship
        val before = context.computeOnClient<Vector3d, RuntimeException> { Vector3d(WiringCamera.position) }
        context.input.holdKey { it.keyUp }
        context.waitTicks(20)
        context.input.releaseKey { it.keyUp }
        context.waitTicks(5)
        val after = context.computeOnClient<Vector3d, RuntimeException> { Vector3d(WiringCamera.position) }
        log("wiring flew ${"%.2f".format(before.distance(after))} blocks")
        shot(context, "wiring_flown")

        // Look around with the right mouse button
        val yaw = context.computeOnClient<Float, RuntimeException> { WiringCamera.yaw }
        val middle = context.computeOnClient<DoubleArray, RuntimeException> { mc -> doubleArrayOf(mc.window.screenWidth / 2.0, 30.0) }
        context.input.setCursorPos(middle[0], middle[1])
        context.waitTicks(2)
        context.input.holdMouse(InputConstants.MOUSE_BUTTON_RIGHT)
        context.waitTicks(2)
        context.input.moveCursor(60.0, 0.0)
        context.waitTicks(3)
        context.input.releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT)
        context.waitTicks(3)
        log("wiring looked around ${"%.1f".format(context.computeOnClient<Float, RuntimeException> { WiringCamera.yaw } - yaw)} degrees")

        // Go back so everything is in view and drag the red dot of the cannon button onto the cushion
        context.runOnClient<RuntimeException> {
            WiringCamera.position.set(before)
            WiringCamera.yaw = yaw
        }
        context.waitTicks(5)
        val cannonDot = screenPixel(context, rig.cannonButton)
        val cushionDot = screenPixel(context, rig.cushion)
        context.input.setCursorPos(cannonDot[0], cannonDot[1])
        context.waitTicks(3)
        context.input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.input.setCursorPos((cannonDot[0] + cushionDot[0]) / 2, (cannonDot[1] + cushionDot[1]) / 2)
        context.waitTicks(3)
        shot(context, "wiring_dragging")
        context.input.setCursorPos(cushionDot[0], cushionDot[1])
        context.waitTicks(3)
        context.input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitTicks(5)
        state("after dragging")

        // Bind the cannon button too, then try to cheat a contact point that isn't on the ship
        val cannonDotAgain = screenPixel(context, rig.cannonButton)
        context.input.setCursorPos(cannonDotAgain[0], cannonDotAgain[1])
        context.waitTicks(3)
        context.input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitTicks(5)
        context.input.pressKey(j)
        context.waitTicks(5)
        context.runOnClient<RuntimeException> {
            ClientPlayNetworking.send(WiringConnectPayload(rig.helm, rig.cannonButton, BlockPos(0, 64, 0)))
        }
        context.waitTicks(5)
        state("after trying a contact point off the ship")

        context.input.pressKey(InputConstants.getKey("key.keyboard.escape"))
        context.waitTicks(10)
        context.runOnClient<RuntimeException> { mc -> log("wiring closed screen=${mc.gui.screen()?.javaClass?.simpleName} hud hidden=${mc.gui.hud.isHidden}") }

        // Sitting on nothing, keys do nothing
        context.input.pressKey(g)
        context.input.pressKey(j)
        context.waitTicks(10)
        state("pressed keys while standing")

        // At the helm the first key works, the one wired to the cushion doesn't
        server.runOnServer<RuntimeException> { s -> log("wiring sat at helm ${DevSelfTest.helm(s.overworld())!!.sit(s.playerList.players[0], true)}") }
        context.waitTicks(10)
        context.input.pressKey(g)
        context.waitTicks(10)
        context.input.pressKey(j)
        context.waitTicks(10)
        state("pressed keys at the helm")
        context.input.holdKey { it.keyShift }
        context.waitTicks(10)
        context.input.releaseKey { it.keyShift }

        // On the cushion it's the other way around
        server.runOnServer<RuntimeException> { s -> DevSelfTest.standNearSeat(s.overworld(), 0) }
        context.waitTicks(10)
        aimAtShipPoint(context, Vector3d(rig.cushion.x + 0.5, rig.cushion.y + 0.12, rig.cushion.z + 0.5))
        log("wiring crosshair ${lookingAt(context)}")
        context.input.pressKey { it.keyUse }
        context.waitTicks(10)
        server.runOnServer<RuntimeException> { s -> log("wiring on cushion vehicle=${s.playerList.players[0].vehicle?.type}") }
        context.input.pressKey(g)
        context.waitTicks(10)
        context.input.pressKey(j)
        context.waitTicks(30)
        state("pressed keys on the cushion")
        context.waitTicks(20)
        state("after the shot")

        // A cannonball that hits something has to blow it up, here the deck itself
        val target = rig.seat.offset(5, -1, 0)
        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            log("wiring impact target before=${level.getBlockState(target)}")
            val ship = level.shipObjectWorld.loadedShips.getById(DevSelfTest.lifted!!.id)!!
            val start = ship.shipToWorld.transformPosition(Vector3d(target.x + 0.5, target.y + 5.0, target.z + 0.5))
            val ball = org.valkyrienskies.eureka.entity.Cannonball(level, start.x, start.y, start.z)
            ball.setDeltaMovement(0.0, -1.0, 0.0)
            level.addFreshEntity(ball)
        }
        context.waitTicks(30)
        server.runOnServer<RuntimeException> { s -> log("wiring impact target after=${s.overworld().getBlockState(target)}") }
        shot(context, "after_shot")
    }

    // Two seats, each with its own cannons, all on one key. Only the seat you're on may fire.
    private fun twoCannonCheck(context: ClientGameTestContext, server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) {
        val q = InputConstants.getKey("key.keyboard.q")
        lateinit var rig: DevSelfTest.TwoCannonRig
        fun state(tag: String) = server.runOnServer<RuntimeException> { s -> log("two $tag ${DevSelfTest.twoCannonState(s.overworld(), rig)}") }

        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            rig = DevSelfTest.buildTwoCannonRig(level)
            for (cushion in rig.cushions) log("two cushion ${DevSelfTest.placeCushion(level, cushion).javaClass.simpleName}")
            val p = s.playerList.players[0]
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            log("two helm menu ${p.openMenu(DevSelfTest.helm(level)!!)}")
        }
        context.waitTicks(10)
        context.clickScreenButton("gui.vs_eureka.wiring")
        context.waitTicks(20)
        // From straight above, so no two dots sit on top of each other
        context.runOnClient<RuntimeException> {
            WiringCamera.position.set(rig.center.x + 0.5, rig.center.y + 16.0, rig.center.z + 0.5)
            WiringCamera.yaw = 0f
            WiringCamera.pitch = 89f
        }
        context.waitTicks(5)
        shot(context, "two_wiring")
        val wiredTo = listOf(0, 1, 0, 1, 0, 1)
        for ((index, button) in rig.buttons.withIndex()) {
            // Drag the red dot onto its cushion, then click it and press Q, the same way a player does
            val from = screenPixel(context, button)
            val to = screenPixel(context, rig.cushions[wiredTo[index]])
            context.input.setCursorPos(from[0], from[1])
            context.waitTicks(3)
            context.input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT)
            context.waitTicks(3)
            context.input.setCursorPos((from[0] + to[0]) / 2, (from[1] + to[1]) / 2)
            context.waitTicks(3)
            context.input.setCursorPos(to[0], to[1])
            context.waitTicks(3)
            context.input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT)
            context.waitTicks(6)
            context.input.setCursorPos(from[0], from[1])
            context.waitTicks(3)
            context.input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
            context.waitTicks(5)
            context.input.pressKey(q)
            context.waitTicks(6)
        }
        state("wired")
        shot(context, "two_wired")
        context.input.pressKey(InputConstants.getKey("key.keyboard.escape"))
        context.waitTicks(10)

        for ((index, cushion) in rig.cushions.withIndex()) {
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val ship = level.shipObjectWorld.loadedShips.getById(DevSelfTest.lifted!!.id)!!
                val world = ship.shipToWorld.transformPosition(Vector3d(cushion.x + 0.5, cushion.y + 0.05, cushion.z + 2.5))
                val p = s.playerList.players[0]
                p.abilities.flying = false
                p.onUpdateAbilities()
                p.teleportTo(world.x, world.y, world.z)
            }
            context.waitTicks(15)
            aimAtShipPoint(context, Vector3d(cushion.x + 0.5, cushion.y + 0.12, cushion.z + 0.5))
            log("two crosshair ${lookingAt(context)}")
            context.input.pressKey { it.keyUse }
            context.waitTicks(10)
            server.runOnServer<RuntimeException> { s -> log("two on cushion $index vehicle=${s.playerList.players[0].vehicle?.blockPosition() == cushion}") }
            state("before Q on cushion $index")
            context.input.pressKey(q)
            context.waitTicks(16)
            state("after Q on cushion $index")
            context.input.holdKey { it.keyShift }
            context.waitTicks(10)
            context.input.releaseKey { it.keyShift }
            context.waitTicks(10)
        }
    }

    // Reads the welcome message back out of the chat and clicks its links for real
    private fun welcomeCheck(context: ClientGameTestContext) {
        context.runOnClient<RuntimeException> { mc ->
            mc.options.chatLinksPrompt().set(true)
            mc.gui.setScreen(ChatScreen("", false))
        }
        context.waitTicks(10)
        shot(context, "welcome_chat")
        val links = context.computeOnClient<Map<String, IntArray>, RuntimeException> { mc ->
            val found = LinkedHashMap<String, IntArray>()
            val window = mc.window
            val chat = mc.gui.hud.chat
            for (y in 0 until window.guiScaledHeight step 2) for (x in 0 until window.guiScaledWidth / 2 step 2) {
                val finder = ActiveTextCollector.ClickableStyleFinder(mc.font, x, y)
                chat.captureClickableText(finder, window.guiScaledHeight, mc.gui.hud.guiTicks, ChatComponent.DisplayMode.FOREGROUND)
                val click = finder.result()?.clickEvent as? ClickEvent.OpenUrl ?: continue
                found.putIfAbsent(click.uri().toString(), intArrayOf(x, y))
            }
            found
        }
        log("welcome links under the mouse ${links.keys}")
        val scale = context.computeOnClient<Double, RuntimeException> { mc -> mc.window.screenWidth.toDouble() / mc.window.guiScaledWidth }
        for ((url, at) in links) {
            context.input.setCursorPos(at[0] * scale, at[1] * scale)
            context.waitTicks(3)
            context.input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
            context.waitTicks(5)
            context.runOnClient<RuntimeException> { mc ->
                val screen = mc.gui.screen()
                log("welcome clicked $url got ${screen?.javaClass?.simpleName} ${(screen as? ConfirmLinkScreen)?.narrationMessage?.string}")
            }
            shot(context, "welcome_click_${links.keys.indexOf(url)}")
            context.runOnClient<RuntimeException> { mc -> mc.gui.setScreen(ChatScreen("", false)) }
            context.waitTicks(5)
        }
        context.runOnClient<RuntimeException> { mc -> mc.gui.setScreen(null) }
        context.waitTicks(5)
    }

    // Asks JEI what it knows about our content, then shows the helm like clicking it in the list would
    private fun jeiCheck(context: ClientGameTestContext) {
        context.input.pressKey { it.keyInventory }
        context.waitTicks(40)
        context.runOnClient<RuntimeException> { mc ->
            val runtime = mezz.jei.common.Internal.getJeiRuntime()
            val items = runtime.ingredientManager.allItemStacks
                .map { BuiltInRegistries.ITEM.getKey(it.item) }.filter { it.namespace == "vs_eureka" || it.namespace == "valkyrienskies" }
            log("jei items total=${runtime.ingredientManager.allItemStacks.size} ours=${items.size}")
            val categories = runtime.recipeManager.createRecipeCategoryLookup().get().toList().map { it.recipeType.uid.namespace }
            log("jei categories=${categories.groupingBy { it }.eachCount()}")
            runtime.ingredientFilter.setFilterText("helm")
            val helm = ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("vs_eureka:oak_ship_helm")))
            val focus = runtime.jeiHelpers.focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, helm)
            val recipes = runtime.recipeManager.createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(listOf(focus)).get().toList()
            log("jei crafting recipes for helm=${recipes.size}")
        }
        context.waitTicks(20)
        shot(context, "jei_search")
        context.runOnClient<RuntimeException> { mc ->
            val runtime = mezz.jei.common.Internal.getJeiRuntime()
            val helm = ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("vs_eureka:oak_ship_helm")))
            runtime.recipesGui.show(runtime.jeiHelpers.focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, helm))
        }
        context.waitTicks(20)
        shot(context, "jei_recipe")
        context.runOnClient<RuntimeException> { mc ->
            mezz.jei.common.Internal.getJeiRuntime().ingredientFilter.setFilterText("")
            mc.gui.setScreen(null)
        }
        context.waitTicks(5)
    }

    // Lays a world out the way a Paper server does and opens it, the mod has to take care of the rest by itself
    private fun paperWorldTest(context: ClientGameTestContext) {
        fun worldInfo(server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext, tag: String) {
            server.runOnServer<RuntimeException> { s ->
                val dimensions = s.levelKeys().map { it.identifier().toString() }
                log("paper $tag seed=${s.worldGenSettings.options().seed()} dimensions=$dimensions tick speed=${s.gameRules.get(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED)}")
            }
        }
        val save = context.worldBuilder().setUseConsistentSettings(false).create().use { world ->
            world.server.runCommand("gamerule minecraft:random_tick_speed 7")
            context.waitTicks(40)
            worldInfo(world.server, "created")
            world.worldSave
        }
        val fabricData = save.saveDirectory.resolve("data/minecraft")
        val paperData = save.saveDirectory.resolve("dimensions/minecraft/overworld/data/minecraft")
        java.nio.file.Files.createDirectories(paperData)
        for (file in listOf("game_rules.dat", "scheduled_events.dat", "wandering_trader.dat", "weather.dat", "world_gen_settings.dat")) {
            if (java.nio.file.Files.exists(fabricData.resolve(file))) {
                java.nio.file.Files.move(fabricData.resolve(file), paperData.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                log("paper moved $file away from data/minecraft")
            }
        }
        save.open().use { world ->
            context.waitTicks(40)
            worldInfo(world.server, "imported")
        }
        log("paper originals kept=${java.nio.file.Files.exists(paperData.resolve("world_gen_settings.dat"))} copy made=${java.nio.file.Files.exists(fabricData.resolve("world_gen_settings.dat"))}")
    }

    // A sharp picture of every recipe in the mod, through JEI's recipe view
    private fun recipeShots(context: ClientGameTestContext) {
        context.worldBuilder().setUseConsistentSettings(false).create().use {
            context.waitTicks(20)
            context.input.resizeWindow(1920, 1080)
            context.waitTicks(5)
            context.runOnClient<RuntimeException> { mc ->
                mc.options.guiScale().set(4)
                mc.resizeGui()
            }
            context.waitTicks(10)
            context.input.pressKey { it.keyInventory }
            context.waitTicks(40)
            val count = context.computeOnClient<Int, RuntimeException> { recipes().size }
            log("recipes $count")
            for (index in 0 until count) {
                val name = context.computeOnClient<String, RuntimeException> {
                    val runtime = mezz.jei.common.Internal.getJeiRuntime()
                    val recipe = recipes()[index]
                    runtime.recipesGui.showRecipes(runtime.recipeManager.getRecipeCategory(RecipeTypes.CRAFTING), listOf(recipe), listOf())
                    recipe.id().identifier().path
                }
                context.waitTicks(3)
                context.input.setCursorPos(2.0, 2.0)
                context.waitTicks(4)
                context.takeScreenshot("recipe_$name")
                log("recipe shot $name")
            }
        }
    }

    private fun recipes() = mezz.jei.common.Internal.getJeiRuntime().recipeManager.createRecipeLookup(RecipeTypes.CRAFTING).get().toList()
        .filter { it.id().identifier().namespace == "vs_eureka" }.sortedBy { it.id().identifier().path }

    // A clear look at a player at the helm of an open deck
    private fun stanceTest(context: ClientGameTestContext) {
        val save = context.worldBuilder().setUseConsistentSettings(false).create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            world.connection.waitForChunksRender()
            server.runOnServer<RuntimeException> { DevSelfTest.startMotion() }
            context.waitTicks(125)
            server.runOnServer<RuntimeException> { s ->
                log("stance sit ${DevSelfTest.helm(s.overworld())!!.sit(s.playerList.players[0], true)}")
            }
            context.waitTicks(20)
            context.runOnClient<RuntimeException> { mc ->
                val player = mc.player!!
                // Facing the wheel, the way you are right after clicking it
                player.yRot = 0f
                player.yBodyRot = 0f
                player.yHeadRot = 0f
                player.xRot = 10f
                mc.options.cameraType = CameraType.THIRD_PERSON_FRONT
            }
            context.waitTicks(20)
            shot(context, "stance_front")
            context.runOnClient<RuntimeException> { mc -> mc.options.cameraType = CameraType.THIRD_PERSON_BACK }
            context.waitTicks(20)
            shot(context, "stance_back")
            context.runOnClient<RuntimeException> { mc -> mc.options.cameraType = CameraType.FIRST_PERSON }
            server.runOnServer<RuntimeException> { s ->
                s.playerList.players[0].unRide()
                val ship = s.shipObjectWorld.loadedShips.first()
                ship.getAttachment(EurekaShipControl::class.java)!!.enginesPaused = true
                log("saving with engines paused")
            }
            context.waitTicks(20)
            world.worldSave
        }
        save.open().use { world ->
            context.waitTicks(40)
            world.server.runOnServer<RuntimeException> { s ->
                val ship = s.shipObjectWorld.loadedShips.first()
                log("reloaded enginesPaused=${ship.getAttachment(EurekaShipControl::class.java)?.enginesPaused}")
            }
        }
    }

    // Open deck a player can really walk on, for everything that needs real aiming and clicking
    private fun deckTest(context: ClientGameTestContext) {
        context.worldBuilder().setUseConsistentSettings(false).create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            world.connection.waitForChunksRender()
            server.runOnServer<RuntimeException> { DevSelfTest.startMotion() }
            context.waitTicks(125)
            server.runOnServer<RuntimeException> { s -> DevSelfTest.addFuel(s.overworld()) }
            context.waitTicks(10)
            if (System.getenv("VS_TEST_TWO") == null) {
                cushionCheck(context, server)
                wiringCheck(context, server)
            }
            twoCannonCheck(context, server)
        }
        log("deck test finished")
    }

    @Volatile
    private var playerDrawnSitting: Boolean? = null

    companion object {
        fun shotOf(context: ClientGameTestContext, name: String) {
            if (System.getenv("VS_TEST_SODIUM") == null) {
                context.takeScreenshot(name)
                return
            }
            context.runOnClient<RuntimeException> { mc ->
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "$name.png", mc.gameRenderer.mainRenderTarget(), 1) { }
            }
            context.waitTicks(5)
        }
    }

    // Crouch and right click the helm on a ship opens it, a plain right click sits down, in survival and in creative
    private fun helmCheck(context: ClientGameTestContext, server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) {
        val holdings = listOf(
            ItemStack.EMPTY to ItemStack.EMPTY, ItemStack(Items.OAK_PLANKS, 4) to ItemStack.EMPTY, ItemStack.EMPTY to ItemStack(Items.SHIELD)
        )
        for (mode in listOf(net.minecraft.world.level.GameType.SURVIVAL, net.minecraft.world.level.GameType.CREATIVE)) {
            for ((holding, offhand) in holdings) {
                val tag = "${mode.name.lowercase()} hand=${if (holding.isEmpty) "nothing" else holding.item.toString()} offhand=${if (offhand.isEmpty) "nothing" else offhand.item.toString()}"
                lateinit var helmPos: BlockPos
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    val p = s.playerList.players[0]
                    p.stopRiding()
                    p.setGameMode(mode)
                    p.setItemInHand(InteractionHand.MAIN_HAND, holding.copy())
                    p.setItemInHand(InteractionHand.OFF_HAND, offhand.copy())
                    helmPos = DevSelfTest.helm(level)!!.blockPos
                    DevSelfTest.standNearSeat(level, 0)
                }
                context.waitTicks(15)
                aimAtShipPoint(context, Vector3d(helmPos.x + 0.5, helmPos.y + 0.4, helmPos.z + 0.5))
                log("helm $tag crosshair ${lookingAt(context)} block=${context.computeOnClient<String, RuntimeException> { mc -> (mc.hitResult as? net.minecraft.world.phys.BlockHitResult)?.blockPos?.let { mc.level!!.getBlockState(it).block.descriptionId } ?: "-" }}")
                context.input.holdKey { it.keyShift }
                context.waitTicks(4)
                context.input.pressKey { it.keyUse }
                context.waitTicks(8)
                log("helm $tag crouch click: screen=${context.computeOnClient<String, RuntimeException> { it.gui.screen()?.javaClass?.simpleName ?: "none" }} sneaking=${context.computeOnClient<Boolean, RuntimeException> { it.player!!.isShiftKeyDown }}")
                context.input.releaseKey { it.keyShift }
                // Escape only closes the helm, with nothing open it would pause the game
                if (context.computeOnClient<Boolean, RuntimeException> { it.gui.screen() != null }) {
                    context.input.pressKey(InputConstants.getKey("key.keyboard.escape"))
                }
                context.waitTicks(6)
                // Take away anything placed against the helm, it would be in the way of the next click
                server.runOnServer<RuntimeException> { s ->
                    val level = s.overworld()
                    for (pos in BlockPos.betweenClosed(helmPos.offset(-1, -1, -1), helmPos.offset(1, 2, 1))) {
                        if (level.getBlockState(pos).`is`(net.minecraft.world.level.block.Blocks.OAK_PLANKS) && pos.y > helmPos.y - 1) {
                            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)
                            log("helm $tag a plank had been placed at $pos")
                        }
                    }
                }
                context.waitTicks(4)
                aimAtShipPoint(context, Vector3d(helmPos.x + 0.5, helmPos.y + 0.4, helmPos.z + 0.5))
                context.input.pressKey { it.keyUse }
                context.waitTicks(8)
                server.runOnServer<RuntimeException> { s -> log("helm $tag plain click: vehicle=${s.playerList.players[0].vehicle?.type}") }
                context.input.holdKey { it.keyShift }
                context.waitTicks(6)
                context.input.releaseKey { it.keyShift }
                context.waitTicks(6)
            }
        }
    }

    // Two cannons and no wireless buttons: wire them up in the wiring screen and fire them from their seats
    private fun cannonWiringCheck(context: ClientGameTestContext, server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) {
        val g = InputConstants.getKey("key.keyboard.g")
        lateinit var cannonA: BlockPos
        lateinit var cannonB: BlockPos
        lateinit var cushion: BlockPos
        fun state(tag: String) = server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            fun describe(pos: BlockPos): String {
                val cannon = level.getBlockEntity(pos) as org.valkyrienskies.eureka.blockentity.CannonBlockEntity
                val contact = when (cannon.contact) { null -> "helm"; cushion -> "cushion"; else -> "${cannon.contact}" }
                return "tnt=${cannon.getItem(0).count} key=${cannon.key} contact=$contact"
            }
            log("cannonwire $tag A(${describe(cannonA)}) B(${describe(cannonB)})")
        }

        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val seat = DevSelfTest.seatPos(level)
            cannonA = seat.offset(-4, 0, -2)
            cannonB = seat.offset(4, 0, -2)
            cushion = seat.offset(-1, 0, -4)
            val block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("vs_eureka:cannon"))
            level.setBlock(cannonA, block.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.WEST), 3)
            level.setBlock(cannonB, block.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST), 3)
            for (pos in listOf(cannonA, cannonB)) (level.getBlockEntity(pos) as org.valkyrienskies.eureka.blockentity.CannonBlockEntity).setItem(0, ItemStack(org.valkyrienskies.eureka.EurekaItems.SMALL_CANNONBALL.get(), 8))
            log("cannonwire cushion ${DevSelfTest.placeCushion(level, cushion).javaClass.simpleName}")
            val p = s.playerList.players[0]
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            DevSelfTest.standNearSeat(level, 2)
        }
        context.waitTicks(20)
        state("placed")

        server.runOnServer<RuntimeException> { s -> log("cannonwire helm menu ${s.playerList.players[0].openMenu(DevSelfTest.helm(s.overworld())!!)}") }
        context.waitTicks(10)
        context.clickScreenButton("gui.vs_eureka.wiring")
        context.waitTicks(20)
        context.runOnClient<RuntimeException> { mc ->
            val screen = wiringScreen(mc)
            log("cannonwire screen open=${screen != null} cannons=${screen?.buttons()?.count { it.isCannon }} buttons=${screen?.buttons()?.count { !it.isCannon }} contacts=${screen?.contacts()?.size}")
        }
        // From straight above, so the dots don't sit on top of each other
        context.runOnClient<RuntimeException> {
            WiringCamera.position.set(cannonA.x / 2.0 + cannonB.x / 2.0 + 0.5, cannonA.y + 14.0, cannonA.z + 0.5)
            WiringCamera.yaw = 0f
            WiringCamera.pitch = 89f
        }
        context.waitTicks(5)
        shot(context, "cannonwire_screen")

        // Click each orange dot and press G
        for (cannon in listOf(cannonA, cannonB)) {
            val dot = screenPixel(context, cannon)
            context.input.setCursorPos(dot[0], dot[1])
            context.waitTicks(3)
            context.input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
            context.waitTicks(5)
            context.input.pressKey(g)
            context.waitTicks(6)
        }
        // Drag cannon B onto the cushion
        val from = screenPixel(context, cannonB)
        val to = screenPixel(context, cushion)
        context.input.setCursorPos(from[0], from[1])
        context.waitTicks(3)
        context.input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.input.setCursorPos((from[0] + to[0]) / 2, (from[1] + to[1]) / 2)
        context.waitTicks(3)
        context.input.setCursorPos(to[0], to[1])
        context.waitTicks(3)
        context.input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT)
        context.waitTicks(8)
        shot(context, "cannonwire_wired")
        state("wired")
        context.input.pressKey(InputConstants.getKey("key.keyboard.escape"))
        context.waitTicks(10)

        context.input.pressKey(g)
        context.waitTicks(15)
        state("G while standing")

        server.runOnServer<RuntimeException> { s -> log("cannonwire sat at helm ${DevSelfTest.helm(s.overworld())!!.sit(s.playerList.players[0], true)}") }
        context.waitTicks(10)
        context.input.pressKey(g)
        context.waitTicks(15)
        state("G at the helm")
        context.input.holdKey { it.keyShift }
        context.waitTicks(10)
        context.input.releaseKey { it.keyShift }
        context.waitTicks(10)

        server.runOnServer<RuntimeException> { s ->
            val level = s.overworld()
            val ship = level.shipObjectWorld.loadedShips.getById(DevSelfTest.lifted!!.id)!!
            val world = ship.shipToWorld.transformPosition(Vector3d(cushion.x + 0.5, cushion.y + 0.05, cushion.z + 2.5))
            val p = s.playerList.players[0]
            p.teleportTo(world.x, world.y, world.z)
        }
        context.waitTicks(15)
        aimAtShipPoint(context, Vector3d(cushion.x + 0.5, cushion.y + 0.12, cushion.z + 0.5))
        context.input.pressKey { it.keyUse }
        context.waitTicks(10)
        server.runOnServer<RuntimeException> { s -> log("cannonwire on cushion vehicle=${s.playerList.players[0].vehicle?.type}") }
        context.input.pressKey(g)
        context.waitTicks(15)
        state("G on the cushion")
        // Mashing the key fires no faster than one shot every few ticks, like redstone pulses
        for (i in 1..5) { context.input.pressKey(g) }
        context.waitTicks(15)
        state("G five times quickly on the cushion")
    }

    // Aiming cannons and taking off with an elytra on the open deck, see DevFeatureTest
    private fun featureTest(context: ClientGameTestContext) {
        context.worldBuilder().setUseConsistentSettings(false).create().use { world ->
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            world.connection.waitForChunksRender()
            context.runOnClient<RuntimeException> { mc ->
                log("terrain multi draw indirect=${mc.levelRenderer.isChunkRenderingUsingMultiDrawIndirect} backend=${com.mojang.blaze3d.systems.RenderSystem.getDevice().javaClass.simpleName}")
            }
            server.runOnServer<RuntimeException> { DevSelfTest.startMotion() }
            context.waitTicks(125)
            if (System.getenv("VS_TEST_HELM") != null) {
                helmCheck(context, server)
                return@use
            }
            if (System.getenv("VS_TEST_CANNON_WIRE") != null) {
                server.runCommand("gamerule tntExplodes false")
                cannonWiringCheck(context, server)
                return@use
            }
            if (System.getenv("VS_TEST_NO_CANNON") == null) DevFeatureTest.cannonTest(context, server) { aimAtShipPoint(context, it) }
            if (System.getenv("VS_TEST_NO_ELYTRA") == null) DevFeatureTest.elytraTest(context, server)
        }
        log("feature test finished")
    }

    override fun runTest(context: ClientGameTestContext) {
        LevelExtractionEvents.END_EXTRACTION.register(LevelExtractionEvents.EndExtraction { ctx ->
            for (state in ctx.levelState().entityRenderStates) {
                if ((state as EntityRenderStateDuck).`vs$getRenderedEntity`() === Minecraft.getInstance().player) {
                    playerDrawnSitting = (state as? net.minecraft.client.renderer.entity.state.HumanoidRenderState)?.isPassenger
                }
            }
        })
        if (System.getenv("VS_TEST_RECIPES") != null) {
            recipeShots(context)
            return
        }
        if (System.getenv("VS_TEST_FEATURES") != null) {
            featureTest(context)
            return
        }
        if (System.getenv("VS_TEST_IDLE") != null) {
            DevFeatureTest.idleTest(context)
            return
        }
        if (System.getenv("VS_TEST_HELM_WHEEL") != null) {
            DevFeatureTest.helmWheelTest(context)
            return
        }
        if (System.getenv("VS_TEST_CANNONBALLS") != null) {
            DevFeatureTest.cannonballTest(context)
            return
        }
        if (System.getenv("VS_TEST_PLACED_SAVE") != null) {
            DevFeatureTest.placedSaveTest(context)
            return
        }
        if (System.getenv("VS_TEST_ASSEMBLY") != null) {
            DevFeatureTest.assemblyTest(context)
            return
        }
        if (System.getenv("VS_TEST_SPEEDS") != null) {
            DevFeatureTest.speedTest(context)
            return
        }
        if (System.getenv("VS_TEST_GROUND") != null) {
            DevFeatureTest.groundTest(context)
            return
        }
        if (System.getenv("VS_TEST_DECK") != null) {
            deckTest(context)
            return
        }
        if (System.getenv("VS_TEST_PAPER") != null) {
            paperWorldTest(context)
            return
        }
        if (System.getenv("VS_TEST_STANCE") != null) {
            stanceTest(context)
            return
        }
        if (System.getenv("VS_TEST_LAN_JOIN") != null) {
            lanJoin(context)
            return
        }
        if (System.getenv("VS_TEST_MOTION") != null) {
            motionTest(context)
            return
        }
        if (System.getenv("VS_TEST_JOIN") != null) {
            joinTest(context)
            return
        }
        ClientReceiveMessageEvents.GAME.register(ClientReceiveMessageEvents.Game { message, _ ->
            val links = message.toFlatList().mapNotNull { part ->
                (part.style.clickEvent as? ClickEvent.OpenUrl)?.let { "'${part.string}' opens ${it.uri()}" }
            }
            log("chat: ${message.string} $links")
        })
        val save = context.worldBuilder().setUseConsistentSettings(false).create().use { world ->
            val server = world.server
            welcomeCheck(context)
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("time set noon")
            server.runCommand("weather clear")
            server.runCommand("gamemode creative @a")
            DevSelfTest.baseY = 150
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                for (x in -3..3) for (z in -3..3) level.setChunkForced(x, z, true)
            }
            server.runCommand("tp @p 12 156 -14 40 18")
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                p.abilities.flying = true
                p.onUpdateAbilities()
            }
            world.connection.waitForChunksRender()
            context.waitTicks(40)
            if (System.getenv("VS_TEST_JEI") != null) jeiCheck(context)
            server.runOnServer<RuntimeException> { s -> DevSelfTest.buildShips(s.overworld()) }
            context.waitTicks(30)
            shot(context, "00_before_assembly")
            server.runCommand("tp @p 0.5 152 -5 0 5")
            context.waitTicks(20)
            shot(context, "00b_closeup_before")
            server.runOnServer<RuntimeException> { s -> DevSelfTest.assemble(s.overworld()) }
            context.waitTicks(40)
            shot(context, "01b_closeup_assembled")
            server.runCommand("tp @p 12 156 -14 40 18")
            context.waitTicks(20)
            shot(context, "01_assembled")
            mouseTurn(context, "flying")
            server.runOnServer<RuntimeException> { s -> DevSelfTest.report(s.overworld()) }
            context.waitTicks(100)
            server.runOnServer<RuntimeException> { s -> DevSelfTest.report(s.overworld()) }
            shot(context, "02_after_physics")
            context.runOnClient<RuntimeException> { mc ->
                val level = mc.level!!
                log("client ships=${level.shipObjectWorld.loadedShips.size}")
                for (ship in level.shipObjectWorld.loadedShips) log("client ship ${ship.slug} render=${ship.renderTransform.positionInWorld}")
            }
            server.runOnServer<RuntimeException> { s -> DevSelfTest.addFuel(s.overworld()) }
            context.waitTicks(120)
            server.runOnServer<RuntimeException> { s -> DevSelfTest.report(s.overworld()) }
            shot(context, "03_engine_running")
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                log("open helm menu ${p.openMenu(DevSelfTest.helm(s.overworld())!!)}")
            }
            context.waitTicks(10)
            shot(context, "03b_helm_screen")
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                p.closeContainer()
                p.teleportTo(4.5, 151.0, 4.5)
                log("open engine menu ${p.openMenu(DevSelfTest.engine(s.overworld())!!)}")
            }
            context.waitTicks(10)
            shot(context, "03c_engine_screen")
            server.runOnServer<RuntimeException> { s -> s.playerList.players[0].closeContainer() }
            context.waitTicks(5)
            // The pause button has to stop every engine on the ship from the screen of any one of them
            server.runOnServer<RuntimeException> { s -> DevSelfTest.addSecondEngine(s.overworld()) }
            context.waitTicks(30)
            server.runOnServer<RuntimeException> { s -> log("engines running ${DevSelfTest.engineState(s.overworld())}") }
            context.waitTicks(30)
            server.runOnServer<RuntimeException> { s ->
                log("engines running ${DevSelfTest.engineState(s.overworld())}")
                s.playerList.players[0].openMenu(DevSelfTest.allEngines(s.overworld())[0])
            }
            context.waitTicks(10)
            context.clickScreenButton("gui.vs_eureka.engine.pause")
            context.waitTicks(30)
            shot(context, "03d_engines_paused")
            server.runOnServer<RuntimeException> { s ->
                log("engines paused ${DevSelfTest.engineState(s.overworld())}")
                s.playerList.players[0].closeContainer()
            }
            context.waitTicks(40)
            server.runOnServer<RuntimeException> { s ->
                log("engines paused ${DevSelfTest.engineState(s.overworld())}")
                s.playerList.players[0].openMenu(DevSelfTest.allEngines(s.overworld())[1])
            }
            context.waitTicks(10)
            context.clickScreenButton("gui.vs_eureka.engine.resume")
            context.waitTicks(30)
            server.runOnServer<RuntimeException> { s ->
                log("engines resumed ${DevSelfTest.engineState(s.overworld())}")
                s.playerList.players[0].closeContainer()
            }
            context.waitTicks(5)
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val helm = DevSelfTest.helm(level)
                log("helm found ${helm != null}")
                val p = s.playerList.players[0]
                log("sit result ${helm?.sit(p, true)} vehicle=${p.vehicle}")
            }
            context.waitTicks(20)
            shot(context, "04_mounted")
            mouseTurn(context, "mounted")
            context.runOnClient<RuntimeException> { mc ->
                val player = mc.player!!
                val data = org.valkyrienskies.mod.common.getShipMountedToData(player, 1f)!!
                val mount = data.mountPosInShip
                val eye = mc.gameRenderer.mainCamera().position()
                val eyeInShip = (data.shipMountedTo as org.valkyrienskies.core.api.ships.ClientShip).renderTransform.worldToShip.transformPosition(Vector3d(eye.x, eye.y, eye.z))
                log("stance feet=${"%.3f".format(mount.y() - DevSelfTest.helmY)} eye=${"%.3f".format(eyeInShip.y - DevSelfTest.helmY)} blocks above the floor")
                mc.options.cameraType = CameraType.THIRD_PERSON_BACK
            }
            context.waitTicks(10)
            log("stance drawn sitting=$playerDrawnSitting")
            shot(context, "04b_stance_back")
            context.runOnClient<RuntimeException> { mc -> mc.options.cameraType = CameraType.THIRD_PERSON_FRONT }
            context.waitTicks(10)
            shot(context, "04c_stance_front")
            context.runOnClient<RuntimeException> { mc -> mc.options.cameraType = CameraType.FIRST_PERSON }
            context.waitTicks(5)
            context.input.holdKey { it.keyUp }
            context.waitTicks(120)
            context.input.releaseKey { it.keyUp }
            server.runOnServer<RuntimeException> { s -> DevSelfTest.report(s.overworld()) }
            shot(context, "05_after_flying")
            context.input.holdKey { it.keyLeft }
            context.waitTicks(60)
            context.input.releaseKey { it.keyLeft }
            server.runOnServer<RuntimeException> { s -> DevSelfTest.report(s.overworld()) }
            shot(context, "05b_turned")
            context.input.holdKey { it.keyShift }
            context.waitTicks(10)
            context.input.releaseKey { it.keyShift }
            server.runOnServer<RuntimeException> { s ->
                val p = s.playerList.players[0]
                log("after dismount vehicle=${p.vehicle} pos=${p.position()}")
            }
            context.waitTicks(20)
            shot(context, "06_dismounted")
            server.runOnServer<RuntimeException> { s ->
                val level = s.overworld()
                val engine = DevSelfTest.engine(level)!!
                level.removeBlock(engine.blockPos, false)
                log("broke engine on ship, ship blocks left, ships=${s.shipObjectWorld.allShips.size}")
            }
            context.waitTicks(20)
            shot(context, "07_block_broken")
            server.runOnServer<RuntimeException> { s ->
                val helm = DevSelfTest.helm(s.overworld())!!
                helm.disassemble()
            }
            context.waitTicks(80)
            server.runOnServer<RuntimeException> { s -> log("after disassemble ships=${s.shipObjectWorld.allShips.size}") }
            shot(context, "08_disassembled")
            world.worldSave
        }

        // Everything has to survive being saved and loaded again
        save.open().use { world ->
            context.waitTicks(40)
            world.server.runOnServer<RuntimeException> { s ->
                val ships = s.shipObjectWorld.allShips
                log("reopened ships=${ships.size}")
                for (ship in ships) log("reopened ${ship.slug} pos=${ship.transform.positionInWorld}")
            }
            context.runOnClient<RuntimeException> { mc -> log("reopened client ships=${mc.level!!.shipObjectWorld.loadedShips.size}") }
        }

        log("client test finished")
    }
}
