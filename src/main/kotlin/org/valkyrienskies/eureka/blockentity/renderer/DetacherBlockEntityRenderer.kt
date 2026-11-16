package org.valkyrienskies.eureka.blockentity.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.valkyrienskies.eureka.block.DetacherBlock
import org.valkyrienskies.eureka.blockentity.DetacherBlockEntity
import kotlin.math.PI
import kotlin.math.min

class DetacherRenderState : BlockEntityRenderState() {
    var length = 0f
    var facing: Direction = Direction.NORTH
}

/** The grabbing pole, going out of the red face and coming back. The block itself is the block model. */
class DetacherBlockEntityRenderer(@Suppress("UNUSED_PARAMETER") ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<DetacherBlockEntity, DetacherRenderState> {

    override fun createRenderState() = DetacherRenderState()

    override fun extractRenderState(
        blockEntity: DetacherBlockEntity,
        state: DetacherRenderState,
        partialTicks: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPos, crumblingOverlay)
        state.facing = blockEntity.blockState.getValue(DirectionalBlock.FACING)
        // The detacher itself is solid and dark inside, so light the pole like the air it reaches into
        blockEntity.level?.let { state.lightCoords = LightCoordsUtil.getLightCoords(it, blockEntity.blockPos.relative(state.facing)) }
        val time = blockEntity.level?.gameTime ?: 0L
        state.length = if (!blockEntity.poleOut) 0f
        else DetacherBlock.poleLength(blockEntity.poleReach, (time - blockEntity.poleStart) + partialTicks)
    }

    // The pole reaches well outside the block
    override fun shouldRenderOffScreen(): Boolean = true

    override fun submit(state: DetacherRenderState, matrixStack: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        if (state.length <= MIN_LENGTH) return
        matrixStack.pushPose()
        // Both models point north, out of the red face
        matrixStack.translate(0.5, 0.5, 0.5)
        matrixStack.rotate(turnFor(state.facing))
        matrixStack.translate(-0.5, -0.5, -0.5)
        // One block of pole at a time, so its texture doesn't stretch
        var done = 0f
        while (done < state.length) {
            val piece = min(1f, state.length - done)
            matrixStack.pushPose()
            matrixStack.translate(0.0, 0.0, -(done + piece).toDouble())
            matrixStack.scale(1f, 1f, piece)
            DetacherModels.render(DetacherModels.pole, matrixStack, collector, state)
            matrixStack.popPose()
            done += piece
        }
        matrixStack.pushPose()
        matrixStack.translate(0.0, 0.0, -state.length.toDouble())
        DetacherModels.render(DetacherModels.head, matrixStack, collector, state)
        matrixStack.popPose()
        matrixStack.popPose()
    }

    private companion object {
        const val MIN_LENGTH = 0.01f

        fun turnFor(facing: Direction): Quaternionf = when (facing) {
            Direction.NORTH -> Quaternionf()
            Direction.SOUTH -> Quaternionf().rotationY(PI.toFloat())
            Direction.EAST -> Quaternionf().rotationY((-PI / 2).toFloat())
            Direction.WEST -> Quaternionf().rotationY((PI / 2).toFloat())
            Direction.UP -> Quaternionf().rotationX((PI / 2).toFloat())
            Direction.DOWN -> Quaternionf().rotationX((-PI / 2).toFloat())
        }
    }
}

// A block of pole and the grabbing plate at its end, loaded through Fabric's model loading API
object DetacherModels {
    private val random = RandomSource.create()

    val pole: ExtraModelKey<BlockStateModel> = ExtraModelKey.create()
    val head: ExtraModelKey<BlockStateModel> = ExtraModelKey.create()

    fun render(key: ExtraModelKey<BlockStateModel>, matrixStack: PoseStack, collector: SubmitNodeCollector, state: DetacherRenderState) {
        val model = Minecraft.getInstance().modelManager.getModel(key) ?: return
        random.setSeed(state.blockPos.asLong())
        val parts = ArrayList<BlockStateModelPart>()
        model.collectParts(random, parts)
        collector.submitBlockModel(
            matrixStack, RenderTypes.cutoutMovingBlock(), parts, IntArray(0), state.lightCoords, OverlayTexture.NO_OVERLAY, 0
        )
    }
}
