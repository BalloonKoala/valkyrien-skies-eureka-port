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
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.valkyrienskies.eureka.block.CannonBlock
import org.valkyrienskies.eureka.blockentity.CannonBlockEntity
import kotlin.math.exp

class CannonRenderState : BlockEntityRenderState() {
    var yaw = 0f
    var pitch = 0f
}

/** The swivel mount turns with the barrel's yaw, the barrel also tilts. The base under them is the block model. */
class CannonBlockEntityRenderer(@Suppress("UNUSED_PARAMETER") ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<CannonBlockEntity, CannonRenderState> {

    override fun createRenderState() = CannonRenderState()

    override fun extractRenderState(
        blockEntity: CannonBlockEntity,
        state: CannonRenderState,
        partialTicks: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPos, crumblingOverlay)
        val now = System.nanoTime()
        if (blockEntity.shownYaw.isNaN()) {
            blockEntity.shownYaw = blockEntity.yaw
            blockEntity.shownPitch = blockEntity.pitch
        } else {
            // Swing over to a new aim within a few frames, whatever the frame rate
            val seconds = ((now - blockEntity.shownAtNanos) / 1e9).coerceIn(0.0, 0.25)
            val step = (1.0 - exp(-seconds * SWING_RATE)).toFloat()
            blockEntity.shownYaw += Mth.wrapDegrees(blockEntity.yaw - blockEntity.shownYaw) * step
            blockEntity.shownPitch += (blockEntity.pitch - blockEntity.shownPitch) * step
        }
        blockEntity.shownAtNanos = now
        state.yaw = blockEntity.shownYaw
        state.pitch = blockEntity.shownPitch
    }

    override fun submit(state: CannonRenderState, matrixStack: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        // Both models point north, a yaw of 180
        val turn = Quaternionf().rotationY(Math.toRadians(180.0 - state.yaw).toFloat())

        matrixStack.pushPose()
        matrixStack.translate(0.5, 0.0, 0.5)
        matrixStack.rotate(turn)
        matrixStack.translate(-0.5, 0.0, -0.5)
        CannonModels.render(CannonModels.mount, matrixStack, collector, state)
        matrixStack.popPose()

        matrixStack.pushPose()
        matrixStack.translate(0.5, CannonBlock.PIVOT_HEIGHT, 0.5)
        matrixStack.rotate(turn)
        matrixStack.rotate(Quaternionf().rotationX(Math.toRadians(-state.pitch.toDouble()).toFloat()))
        matrixStack.translate(-0.5, -CannonBlock.PIVOT_HEIGHT, -0.5)
        CannonModels.render(CannonModels.barrel, matrixStack, collector, state)
        matrixStack.popPose()
    }

    private companion object {
        const val SWING_RATE = 12.0
    }
}

// The swivel mount and the barrel, loaded through Fabric's model loading API
object CannonModels {
    private val random = RandomSource.create()

    val mount: ExtraModelKey<BlockStateModel> = ExtraModelKey.create()
    val barrel: ExtraModelKey<BlockStateModel> = ExtraModelKey.create()

    fun render(key: ExtraModelKey<BlockStateModel>, matrixStack: PoseStack, collector: SubmitNodeCollector, state: CannonRenderState) {
        val model = Minecraft.getInstance().modelManager.getModel(key) ?: return
        random.setSeed(state.blockPos.asLong())
        val parts = ArrayList<BlockStateModelPart>()
        model.collectParts(random, parts)
        collector.submitBlockModel(
            matrixStack, RenderTypes.cutoutMovingBlock(), parts, IntArray(0), state.lightCoords, OverlayTexture.NO_OVERLAY, 0
        )
    }
}
