package org.valkyrienskies.eureka.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.eureka.ship.EurekaShipControl
import org.valkyrienskies.eureka.util.ShipAssembler
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.util.vsHasPermission
import org.valkyrienskies.mod.util.logger
import kotlin.math.max
import kotlin.math.sqrt
import org.valkyrienskies.mod.common.assembly.ShipAssembler as VSShipAssembler

/**
 * `/vs build unassemble <all|distance>` turns ships back into blocks where they are, `/vs build delete <all|distance>`
 * removes them with everything on them. A distance is in blocks from where the command runs, all is every ship in
 * every dimension.
 */
object BuildCommand {
    private val logger by logger()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("vs").then(
                literal("build")
                    .requires { it.vsHasPermission(VSGameConfig.SERVER.Commands.deleteShipCommandPerms) }
                    .then(action("unassemble", delete = false))
                    .then(action("delete", delete = true))
            )
        )
    }

    private fun action(name: String, delete: Boolean): LiteralArgumentBuilder<CommandSourceStack> =
        literal(name)
            .then(literal("all").executes { run(it, delete, null) })
            .then(
                argument("distance", IntegerArgumentType.integer(0))
                    .executes { run(it, delete, IntegerArgumentType.getInteger(it, "distance")) }
            )

    private fun run(context: CommandContext<CommandSourceStack>, delete: Boolean, distance: Int?): Int {
        val source = context.source
        val server = source.server
        val targets = ArrayList<Pair<ServerLevel, ServerShip>>()
        for (ship in source.level.shipObjectWorld.allShips.toList()) {
            val level = server.getLevelFromDimensionId(ship.chunkClaimDimension) ?: continue
            if (distance != null) {
                if (level != source.level) continue
                val box = ship.worldAABB
                val at = source.position
                val dx = max(max(box.minX() - at.x, 0.0), at.x - box.maxX())
                val dy = max(max(box.minY() - at.y, 0.0), at.y - box.maxY())
                val dz = max(max(box.minZ() - at.z, 0.0), at.z - box.maxZ())
                if (sqrt(dx * dx + dy * dy + dz * dz) > distance) continue
            }
            targets.add(level to ship)
        }
        if (targets.isEmpty()) {
            source.sendFailure(Component.translatable("command.valkyrienskies.build.none"))
            return 0
        }

        var done = 0
        for ((level, ship) in targets) {
            try {
                if (delete) {
                    VSShipAssembler.deleteShip(level, ship, deleteBlocks = true, dropBlocks = false)
                } else {
                    val playerBuilt = (ship as? LoadedServerShip)?.getAttachment(EurekaShipControl::class.java)?.playerBuilt == true
                    ShipAssembler.unfillShip(level, ship, BlockPos.ZERO, BlockPos.ZERO, playerBuilt)
                    VSShipAssembler.deleteShip(level, ship, deleteBlocks = false, dropBlocks = false)
                }
                done++
            } catch (e: RuntimeException) {
                // One ship that can't be handled mustn't stop the rest
                logger.error("Couldn't ${if (delete) "delete" else "unassemble"} ship ${ship.slug}", e)
            }
        }
        val key = if (delete) "command.valkyrienskies.build.deleted" else "command.valkyrienskies.build.unassembled"
        source.sendSuccess({ Component.translatable(key, done) }, true)
        return done
    }
}
