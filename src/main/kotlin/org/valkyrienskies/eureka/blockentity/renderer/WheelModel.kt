package org.valkyrienskies.eureka.blockentity.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.eureka.block.IWoodType
import org.valkyrienskies.eureka.block.ShipHelmBlock
import org.valkyrienskies.eureka.block.WoodType

// One standalone wheel model per wood type, loaded through Fabric's model loading API
object WheelModels {
    private val mc get() = Minecraft.getInstance()
    private val random = RandomSource.create()

    val keys: Map<IWoodType, ExtraModelKey<BlockStateModel>> =
        WoodType.entries.associateWith { ExtraModelKey.create<BlockStateModel>() }

    fun render(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        blockState: BlockState,
        blockPos: BlockPos,
        lightCoords: Int,
        combinedOverlay: Int
    ) {
        val woodType = (blockState.block as ShipHelmBlock).woodType
        val model = mc.modelManager.getModel(keys[woodType]!!) ?: return

        matrixStack.pushPose()
        // Model isn't centered calculated and need to use 0.625 on y and z 0.25
        matrixStack.translate(-0.5, -0.625, -0.25)

        random.setSeed(blockState.getSeed(blockPos))
        val parts = ArrayList<BlockStateModelPart>()
        model.collectParts(random, parts)
        collector.submitBlockModel(matrixStack, RenderTypes.cutoutMovingBlock(), parts, IntArray(0), lightCoords, combinedOverlay, 0)

        matrixStack.popPose()
    }
}
