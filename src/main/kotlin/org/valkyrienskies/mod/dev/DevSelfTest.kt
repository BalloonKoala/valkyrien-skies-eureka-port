package org.valkyrienskies.mod.dev

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.GoalSelector
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.decoration.Cushion
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.GameType
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.blockentity.CannonBlockEntity
import org.valkyrienskies.eureka.blockentity.EngineBlockEntity
import org.valkyrienskies.eureka.blockentity.WirelessButtonBlockEntity
import org.valkyrienskies.eureka.entity.Cannonball
import org.valkyrienskies.eureka.ship.EurekaShipControl
import org.valkyrienskies.eureka.util.ShipAssembler
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsPipeline
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import org.valkyrienskies.mod.common.util.toJOML
import org.joml.Vector3d

/** Development only, never shipped: exercises real ships on a running server and logs what happened. */
object DevSelfTest {
    var ticks = 0
    var baseY = 100

    @JvmField
    @Volatile
    var helmY = 0.0
    var lifted: ServerShip? = null
    var ballast: ServerShip? = null
    private var engineLocal: BlockPos? = null

    @JvmStatic
    fun install() {
        val mode = System.getProperty("vs.selfTest")
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticks++
            try {
                when (mode) {
                    "reload" -> reload(server)
                    "join" -> join(server)
                    "motion" -> motion(server)
                    else -> build(server)
                }
            } catch (t: Throwable) {
                println("SELFTEST-FAIL at tick $ticks: $t")
                t.printStackTrace()
            }
        }
    }

    private fun block(id: String): Block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))

    private fun log(s: String) = println("SELFTEST $s")

    private var joinStart = -1

    /** Waits for a client to connect, then runs the ship test with it on board. */
    private fun join(server: MinecraftServer) {
        val level = server.overworld()
        val player = server.playerList.players.firstOrNull()
        if (joinStart < 0) {
            if (ticks > 3000) {
                log("join no client connected")
                server.halt(false)
            }
            if (player == null) return
            joinStart = ticks
            baseY = 150
            for (x in -3..3) for (z in -3..3) level.setChunkForced(x, z, true)
            log("join player connected, physics running=${server.vsPipeline.arePhysicsRunning}")
            player.setGameMode(GameType.CREATIVE)
            player.abilities.flying = true
            player.onUpdateAbilities()
            player.teleportTo(12.5, 156.0, -13.5)
        }
        val t = ticks - joinStart
        when (t) {
            40 -> buildShips(level)
            60 -> assemble(level)
            70 -> player?.teleportTo(12.5, 156.0, -13.5)
            160 -> addFuel(level)
            280 -> log("join sit ${player?.let { helm(level)?.sit(it, true) }}")
            760 -> {
                log("join final ships=${server.shipObjectWorld.allShips.size} physics tps=${server.vsPipeline.computePhysTps()}")
                server.halt(false)
            }
        }
        if (t >= 100 && t % 100 == 0) {
            log("join t=$t")
            report(level)
        }
    }

    private var cruiser: ServerShip? = null
    private var onDeck: List<Entity> = listOf()

    @JvmField
    @Volatile
    var motionDone = false

    private var motionStarted = false

    /** Keeps entities on a moving ship so the client can measure how steady they look, see DevClientTest.motionTest. */
    fun startMotion() {
        if (motionStarted) return
        motionStarted = true
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticks++
            try {
                motion(server)
                watchWiring(server)
            } catch (t: Throwable) {
                println("SELFTEST-FAIL at tick $ticks: $t")
                t.printStackTrace()
                motionDone = true
            }
        }
    }

    private fun motion(server: MinecraftServer) {
        if (motionDone) return
        val level = server.overworld()
        if (joinStart < 0) {
            if (server.playerList.players.size < (System.getenv("VS_TEST_PLAYERS") ?: "1").toInt()) return
            joinStart = ticks
            baseY = 150
            for (x in -6..6) for (z in -6..6) level.setChunkForced(x, z, true)
            for (p in server.playerList.players) {
                p.setGameMode(GameType.CREATIVE)
                p.abilities.flying = true
                p.onUpdateAbilities()
                p.teleportTo(0.5, 156.0, 0.5)
            }
            log("motion players connected ${server.playerList.players.map { it.name.string }}")
        }
        val y = baseY
        val t = ticks - joinStart
        if (System.getenv("VS_TEST_WIRING_LAN") != null) {
            if (t == 140) {
                val second = server.playerList.players.last()
                log("wiring lan cushion ${placeCushion(level, buildWiringRig(level).cushion)}")
                log("wiring lan helm menu ${second.openMenu(helm(level)!!)}")
            }
            if (t == 420) log("wiring lan sat ${helm(level)!!.sit(server.playerList.players.last(), true)}")
            if (t >= 420 && t % 40 == 0) {
                val rig = watched!!
                val second = server.playerList.players.last()
                log("wiring lan t=$t ${wiringState(level, rig)} vehicle=${second.vehicle?.type} cushion there=${cushion(level, rig.cushion) != null}")
            }
            if (t == 860) {
                motionDone = true
                if (server.isDedicatedServer) server.halt(false)
            }
            if (t > 110) {
                watchWiring(server)
                return
            }
        }
        if ((System.getenv("VS_TEST_STANCE") != null || System.getenv("VS_TEST_DECK") != null || System.getenv("VS_TEST_FEATURES") != null) && t > 110) return
        when (t) {
            40 -> {
                val testSpeed = (System.getenv("VS_TEST_SPEED") ?: "12").toDouble()
                EurekaConfig.SERVER.smallShipTopSpeed = testSpeed
                EurekaConfig.SERVER.hugeShipTopSpeed = testSpeed
                EurekaConfig.SERVER.engineSpeedPerEngine = 10000.0
                EurekaConfig.SERVER.enginePowerLinear = 50000000f
                for (x in -6..6) for (z in -6..6) {
                    level.setBlock(BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 3)
                    level.setBlock(BlockPos(x, y - 1, z), block("vs_eureka:balloon").defaultBlockState(), 3)
                }
                level.setBlock(
                    BlockPos(0, y + 1, 5),
                    block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
                    3
                )
                level.setBlock(BlockPos(4, y + 1, 5), block("vs_eureka:engine").defaultBlockState(), 3)
                if (System.getenv("VS_TEST_BE_LAN") != null) {
                    // Blocks with parts drawn apart from the chunk, to watch on a client that joined over the network
                    level.setBlock(BlockPos(2, y + 1, 2), block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH), 3)
                    level.setBlock(BlockPos(3, y + 1, 1), Blocks.CHEST.defaultBlockState(), 3)
                    level.setBlock(BlockPos(1, y + 1, 1), Blocks.BELL.defaultBlockState(), 3)
                    for (pos in listOf(BlockPos(2, y + 1, 2), BlockPos(3, y + 1, 1), BlockPos(1, y + 1, 1), BlockPos(0, y + 1, 5))) {
                        org.valkyrienskies.mod.common.PlacedBlocks.markPlaced(level, pos)
                    }
                }
                for (x in -5..-1) for (z in -5..-1) {
                    if (x == -5 || x == -1 || z == -5 || z == -1) {
                        level.setBlock(BlockPos(x, y + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3)
                    }
                }
            }
            60 -> {
                cruiser = ShipAssembler.collectBlocks(level, BlockPos(0, y + 1, 5)) { !it.isAir }
                lifted = cruiser
                log("motion assembled ${cruiser?.slug}")
            }
            100 -> {
                val deck = level.shipObjectWorld.allShips.getById(cruiser!!.id)!!.transform.positionInWorld
                val still = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND)!!
                still.setPos(deck.x() + 1.0, y + 1.1, deck.z() - 2.0)
                still.getAttribute(Attributes.MOVEMENT_SPEED)!!.baseValue = 0.0
                still.customName = Component.literal("still")
                level.addFreshEntity(still)
                val penned = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND)!!
                penned.setPos(deck.x() - 3.0, y + 1.1, deck.z() - 3.0)
                penned.customName = Component.literal("penned")
                level.addFreshEntity(penned)
                val item = net.minecraft.world.entity.item.ItemEntity(level, deck.x() + 3.0, y + 1.1, deck.z() - 2.0, ItemStack(Items.DIAMOND))
                item.setNeverPickUp()
                item.setUnlimitedLifetime()
                item.customName = Component.literal("item")
                level.addFreshEntity(item)
                val noAi = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND)!!
                noAi.setPos(deck.x() + 3.0, y + 1.1, deck.z() - 4.0)
                noAi.setNoAi(true)
                noAi.customName = Component.literal("noai")
                level.addFreshEntity(noAi)
                onDeck = listOf(still, penned, item, noAi)
                for ((i, p) in server.playerList.players.withIndex()) {
                    p.abilities.flying = false
                    p.onUpdateAbilities()
                    if (i == 0) {
                        p.teleportTo(level, deck.x(), y + 1.1, deck.z() + 4.0, setOf(), 180f, 12f, true)
                    } else {
                        p.teleportTo(level, deck.x() + i, y + 1.1, deck.z() - 5.0, setOf(), 0f, 12f, true)
                    }
                }
            }
            160 -> {
                addFuel(level)
                drive(level) { forwardImpulse = 1f }
                log("motion phase cruise")
            }
            400 -> {
                drive(level) { forwardImpulse = 1f; leftImpulse = 1f }
                log("motion phase turning")
            }
            560 -> {
                drive(level) { forwardImpulse = 1f; leftImpulse = 0f; sprintOn = true }
                log("motion phase sprint")
            }
            760 -> {
                drive(level) { forwardImpulse = 0f; sprintOn = false }
                log("motion phase stopping")
            }
            860 -> {
                log("motion final physics tps=${server.vsPipeline.computePhysTps()}")
                motionDone = true
                if (server.isDedicatedServer) server.halt(false)
            }
        }
        if (t >= 100 && t % 50 == 0) {
            val ship = level.shipObjectWorld.allShips.getById(cruiser!!.id) ?: return
            log("motion t=$t speed=${"%.2f".format(ship.velocity.length())} pos=${ship.transform.positionInWorld}")
            for (e in onDeck + server.playerList.players) {
                val p = ship.transform.worldToShip.transformPosition(Vector3d(e.x, e.y, e.z)).sub(ship.transform.positionInShip)
                val drag = (e as IEntityDraggingInformationProvider).draggingInformation
                val brain = (e as? Mob)?.let { mob ->
                    val goals = Mob::class.java.getDeclaredField("goalSelector").apply { isAccessible = true }.get(mob) as GoalSelector
                    "water=${mob.isInWater} target=${mob.navigation.targetPos} wanted=${mob.moveControl.hasWanted()} goals=" +
                        goals.availableGoals.filter { it.isRunning }.map { it.goal.javaClass.simpleName }
                }
                log("  server ${e.customName?.string ?: e.name.string} local=(${"%.2f %.2f %.2f".format(p.x, p.y, p.z)}) onGround=${e.onGround()} stoodOn=${drag.lastShipStoodOn} since=${drag.ticksSinceStoodOnShip} $brain")
            }
        }
    }

    private fun drive(level: ServerLevel, setup: SeatedControllingPlayer.() -> Unit) {
        val ship = level.shipObjectWorld.loadedShips.getById(cruiser!!.id) as? LoadedServerShip ?: return
        val driver = ship.getAttachment(SeatedControllingPlayer::class.java) ?: SeatedControllingPlayer(Direction.NORTH)
        driver.setup()
        ship.setAttachment(driver)
    }

    private fun reload(server: MinecraftServer) {
        if (ticks == 80) {
            val ships = server.shipObjectWorld.allShips
            log("reload seed=${server.worldGenSettings.options().seed()} dimensions=${server.levelKeys().map { it.identifier().toString() }}")
            log("reload ships=${ships.size} slugs=${ships.map { it.slug }}")
            for (ship in ships) log("reload ${ship.slug} pos=${ship.transform.positionInWorld}")
            server.halt(false)
        }
    }

    private fun build(server: MinecraftServer) {
        val level = server.overworld()
        when (ticks) {
            20 -> {
                for (x in -3..3) for (z in -3..3) level.setChunkForced(x, z, true)
                log("forced chunks")
            }
            60 -> buildShips(level)
            80 -> assemble(level)
            120, 200, 300, 400, 500 -> report(level)
            320 -> addFuel(level)
            520 -> {
                log("final ships=${server.shipObjectWorld.allShips.size}")
                server.halt(false)
            }
        }
    }

    fun buildShips(level: ServerLevel) {
        val y = baseY
        for (x in -2..2) for (z in -2..2) level.setBlock(BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 3)
        level.setBlock(
            BlockPos(0, y + 1, 0),
            block("vs_eureka:oak_ship_helm").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            3
        )
        for (x in -2..2) for (z in -2..2) {
            if (x != 0 || z != 0) level.setBlock(BlockPos(x, y + 2, z), block("vs_eureka:balloon").defaultBlockState(), 3)
        }
        level.setBlock(BlockPos(2, y + 1, 2), block("vs_eureka:engine").defaultBlockState(), 3)
        level.setBlock(BlockPos(-1, y + 1, -1), Blocks.CHEST.defaultBlockState(), 3)
        level.setBlock(BlockPos(1, y + 1, -1), Blocks.OAK_SIGN.defaultBlockState(), 3)
        for (x in 20..22) for (z in 0..2) level.setBlock(BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3)
        log("built blocks, helm=${level.getBlockState(BlockPos(0, y + 1, 0))}")
    }

    fun assemble(level: ServerLevel) {
        lifted = ShipAssembler.collectBlocks(level, BlockPos(0, baseY + 1, 0)) { !it.isAir }
        log("eureka assembled: ${lifted?.slug} id=${lifted?.id}")
        val stone = org.valkyrienskies.mod.common.assembly.ShipAssembler.assembleToShip(
            level, (20..22).flatMap { x -> (0..2).map { z -> BlockPos(x, baseY, z) } }.toSet(), 1.0
        )
        ballast = stone
        log("vs assembled: ${stone.slug} id=${stone.id}")
        log("world block left behind at helm: ${level.getBlockState(BlockPos(0, baseY + 1, 0))}")
    }

    fun report(level: ServerLevel) {
        for ((name, ship) in listOf("lifted" to lifted, "ballast" to ballast)) {
            val s = ship ?: continue
            val live = level.shipObjectWorld.allShips.getById(s.id)
            if (live == null) {
                log("t=$ticks $name is gone")
                continue
            }
            val p: org.joml.Vector3dc = live.transform.positionInWorld
            val v = live.velocity
            log("t=$ticks $name pos=(%.2f, %.2f, %.2f) vel=(%.2f, %.2f, %.2f)".format(p.x(), p.y(), p.z(), v.x(), v.y(), v.z()))
        }
        val ship = lifted?.let { level.shipObjectWorld.loadedShips.getById(it.id) }
        val control = ship?.getAttachment(EurekaShipControl::class.java)
        log("t=$ticks control balloons=${control?.balloons} anchors=${control?.anchors} helms=${control?.helms}")
        engine(level)?.let { log("t=$ticks engine fuel=${it.fuel} heat=${it.data.get(0)}") }
    }

    fun helm(level: ServerLevel): org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity? {
        val ship = lifted ?: return null
        val aabb = level.shipObjectWorld.allShips.getById(ship.id)?.shipAABB ?: return null
        for (x in aabb.minX()..aabb.maxX()) for (y in aabb.minY()..aabb.maxY()) for (z in aabb.minZ()..aabb.maxZ()) {
            val be = level.getBlockEntity(BlockPos(x, y, z))
            if (be is org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity) {
                helmY = y.toDouble()
                return be
            }
        }
        return null
    }

    fun engine(level: ServerLevel): EngineBlockEntity? {
        val ship = lifted ?: return null
        val aabb = level.shipObjectWorld.allShips.getById(ship.id)?.shipAABB ?: return null
        for (x in aabb.minX()..aabb.maxX()) for (y in aabb.minY()..aabb.maxY()) for (z in aabb.minZ()..aabb.maxZ()) {
            val be = level.getBlockEntity(BlockPos(x, y, z))
            if (be is EngineBlockEntity) return be
        }
        return null
    }

    fun seatPos(level: ServerLevel): BlockPos {
        val helm = helm(level)!!
        return helm.blockPos.relative(helm.blockState.getValue(BlockStateProperties.HORIZONTAL_FACING))
    }

    // The same way a player would, right clicking the floor
    fun placeCushion(level: ServerLevel, at: BlockPos = seatPos(level)): Any {
        val player = level.server.playerList.players[0]
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.CUSHION.pick(net.minecraft.world.item.DyeColor.RED)))
        val hit = BlockHitResult(Vec3.atCenterOfWithY(at, at.y.toDouble()), Direction.UP, at.below(), false)
        return player.mainHandItem.useOn(UseOnContext(player, InteractionHand.MAIN_HAND, hit))
    }

    fun cushion(level: ServerLevel, at: BlockPos = seatPos(level)): Cushion? =
        level.getEntitiesOfClass(Cushion::class.java, AABB(at)).firstOrNull()

    /** Puts the player on the deck [away] blocks to the side of the seat, on the middle of the ship. */
    fun standNearSeat(level: ServerLevel, away: Int) {
        val player = level.server.playerList.players[0]
        val ship = level.shipObjectWorld.loadedShips.getById(lifted!!.id)!!
        val spot = seatPos(level).relative(Direction.EAST, away)
        val world = ship.shipToWorld.transformPosition(Vector3d(spot.x + 0.5, spot.y + 0.05, spot.z + 0.5))
        player.abilities.flying = false
        player.onUpdateAbilities()
        player.teleportTo(world.x, world.y, world.z)
    }

    fun removeCushion(level: ServerLevel) {
        cushion(level)?.discard()
    }

    fun driving(level: ServerLevel): Boolean =
        (level.shipObjectWorld.loadedShips.getById(lifted!!.id) as LoadedServerShip).getAttachment(SeatedControllingPlayer::class.java) != null

    class WiringRig(
        val helm: BlockPos, val seat: BlockPos, val lampButton: BlockPos, val lamp: BlockPos, val cannonButton: BlockPos,
        val cannon: BlockPos, val cushion: BlockPos, val shown: BlockPos
    )

    private fun wirelessButton(powered: Boolean) = block("vs_eureka:oak_wireless_button").defaultBlockState()
        .setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
        .setValue(BlockStateProperties.POWERED, powered)

    fun buildWiringRig(level: ServerLevel): WiringRig {
        val helm = helm(level)!!
        val seat = seatPos(level)
        val rig = WiringRig(
            helm.blockPos, seat, seat.offset(3, 0, -2), seat.offset(4, 0, -2), seat.offset(-1, 0, 0), seat.offset(-2, 0, 0),
            seat.offset(-2, 0, -2), seat.offset(1, 0, 1)
        )
        level.setBlock(rig.lampButton, wirelessButton(false), 3)
        level.setBlock(rig.lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 3)
        level.setBlock(rig.cannon, block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH), 3)
        level.setBlock(rig.cannonButton, wirelessButton(false), 3)
        level.setBlock(rig.shown, wirelessButton(true), 3)
        (level.getBlockEntity(rig.cannon) as CannonBlockEntity).setItem(0, ItemStack(org.valkyrienskies.eureka.EurekaItems.SMALL_CANNONBALL.get(), 3))
        watched = rig
        return rig
    }

    private var watched: WiringRig? = null
    private var watchedLevel: ServerLevel? = null

    @Volatile
    var lampPulses = ArrayList<Int>()
    private var lampRun = 0
    private var ballSeen = false

    /** Writes down how long the first button stays on, and what happens to anything the cannon fires. */
    fun watchWiring(server: MinecraftServer) {
        val rig = watched ?: return
        val level = server.overworld()
        val on = level.getBlockState(rig.lampButton).getValue(BlockStateProperties.POWERED)
        if (on) {
            if (lampRun == 0) log("wiring lamp button on, lamp lit=${level.getBlockState(rig.lamp).getValue(BlockStateProperties.LIT)}")
            lampRun++
        } else if (lampRun > 0) {
            lampPulses.add(lampRun)
            log("wiring lamp button pulse lasted $lampRun ticks")
            lampRun = 0
        }
        val balls = level.allEntities.filterIsInstance<Cannonball>()
        if (balls.isNotEmpty() && !ballSeen) {
            ballSeen = true
            log("wiring cannonball in the air at ${balls[0].position()} moving ${balls[0].deltaMovement}")
        } else if (balls.isEmpty() && ballSeen) {
            ballSeen = false
            log("wiring cannonball landed")
        }
    }

    fun wiringState(level: ServerLevel, rig: WiringRig): String {
        val lamp = level.getBlockEntity(rig.lampButton) as WirelessButtonBlockEntity
        val cannon = level.getBlockEntity(rig.cannonButton) as WirelessButtonBlockEntity
        return "lamp button key=${lamp.key} contact=${lamp.contact} | cannon button key=${cannon.key} contact=${cannon.contact} | " +
            "tnt=${(level.getBlockEntity(rig.cannon) as CannonBlockEntity).getItem(0).count} pulses=$lampPulses"
    }

    class TwoCannonRig(
        val cannons: List<BlockPos>, val buttons: List<BlockPos>, val cushions: List<BlockPos>, val center: BlockPos
    )

    /** Each cannon has its own button. The first pair stands apart, the second pair stands side by side. */
    fun buildTwoCannonRig(level: ServerLevel): TwoCannonRig {
        val seat = seatPos(level)
        val cannonA = seat.offset(-5, 0, -2)
        val cannonB = seat.offset(-5, 0, 1)
        val cannonC = seat.offset(5, 0, -4)
        val cannonD = seat.offset(5, 0, -3)
        val cannonE = seat.offset(5, 0, 0)
        val cannonF = cannonE.above()
        val rig = TwoCannonRig(
            listOf(cannonA, cannonB, cannonC, cannonD, cannonE, cannonF),
            listOf(cannonA.east(), cannonB.east(), cannonC.above(), cannonD.above(), cannonE.north(), cannonF.west()),
            listOf(seat.offset(-3, 0, -4), seat.offset(-1, 0, -3)),
            seat.offset(0, 0, -3)
        )
        val west = block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST)
        val east = block("vs_eureka:cannon").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
        level.setBlock(cannonA, west, 3)
        level.setBlock(cannonB, west, 3)
        level.setBlock(cannonC, east, 3)
        level.setBlock(cannonD, east, 3)
        level.setBlock(cannonE, east, 3)
        level.setBlock(cannonF, east, 3)
        for (cannon in rig.cannons) (level.getBlockEntity(cannon) as CannonBlockEntity).setItem(0, ItemStack(org.valkyrienskies.eureka.EurekaItems.SMALL_CANNONBALL.get(), 8))
        for (button in rig.buttons.dropLast(1)) level.setBlock(button, wirelessButton(false), 3)
        level.setBlock(
            rig.buttons.last(),
            wirelessButton(false).setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST),
            3
        )
        return rig
    }

    fun twoCannonState(level: ServerLevel, rig: TwoCannonRig): String =
        "tnt=" + rig.cannons.map { (level.getBlockEntity(it) as CannonBlockEntity).getItem(0).count } +
            " wired=" + rig.buttons.map { button ->
                val entity = level.getBlockEntity(button) as WirelessButtonBlockEntity
                "${entity.key?.removePrefix("key.keyboard.")}->" + rig.cushions.indexOf(entity.contact)
            }

    fun allEngines(level: ServerLevel): List<EngineBlockEntity> {
        val aabb = level.shipObjectWorld.allShips.getById(lifted!!.id)?.shipAABB ?: return listOf()
        val found = ArrayList<EngineBlockEntity>()
        for (x in aabb.minX()..aabb.maxX()) for (y in aabb.minY()..aabb.maxY()) for (z in aabb.minZ()..aabb.maxZ()) {
            (level.getBlockEntity(BlockPos(x, y, z)) as? EngineBlockEntity)?.let { found.add(it) }
        }
        return found
    }

    fun addSecondEngine(level: ServerLevel) {
        val first = engine(level)!!
        val pos = first.blockPos.north()
        level.setBlock(pos, block("vs_eureka:engine").defaultBlockState(), 3)
        (level.getBlockEntity(pos) as EngineBlockEntity).setItem(0, ItemStack(Items.COAL, 8))
    }

    fun engineState(level: ServerLevel): String {
        val ship = level.shipObjectWorld.loadedShips.getById(lifted!!.id) as LoadedServerShip
        val control = ship.getAttachment(EurekaShipControl::class.java)!!
        val force = EurekaShipControl::class.java.getDeclaredField("extraForceLinear").apply { isAccessible = true }.get(control)
        return "paused=${control.enginesPaused} fuelLeft=${allEngines(level).map { it.data.get(1) }} coal=${allEngines(level).map { it.fuel.count }} engineForce=$force"
    }

    fun addFuel(level: ServerLevel) {
        val be = engine(level)
        log("engine found on ship: ${be != null} at ${be?.blockPos}")
        be?.setItem(0, ItemStack(Items.COAL, 8))
    }
}
