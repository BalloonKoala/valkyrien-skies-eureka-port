package org.valkyrienskies.eureka.blockentity.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity
import org.valkyrienskies.mod.common.getShipManagingPos

class ShipHelmRenderState : BlockEntityRenderState() {
    var helmState: BlockState = Blocks.AIR.defaultBlockState()
    var shipOmegaY = 0.0
}

class ShipHelmBlockEntityRenderer(val ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<ShipHelmBlockEntity, ShipHelmRenderState> {

    override fun createRenderState() = ShipHelmRenderState()

    override fun extractRenderState(
        blockEntity: ShipHelmBlockEntity,
        state: ShipHelmRenderState,
        partialTicks: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPos, crumblingOverlay)
        state.helmState = blockEntity.blockState
        val ship = blockEntity.level?.getShipManagingPos(blockEntity.blockPos)
        state.shipOmegaY = ship?.omega?.y() ?: 0.0
    }

    override fun submit(
        state: ShipHelmRenderState,
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        camera: CameraRenderState
    ) {
        matrixStack.pushPose()
        // Wheel offset of the base
        matrixStack.translate(0.5, 0.60, 0.5)
        // Rotate wheel towards the direction its facing
        matrixStack.rotate(
            Quaternionf(
                AxisAngle4f(
                    (-state.helmState.getValue(BlockStateProperties.HORIZONTAL_FACING)
                        .toYRot() * Math.PI / 180.0).toFloat(), 0.0f, 1.0f, 0.0f
                )
            )
        )
        // Add offset of the base based of rotation
        matrixStack.translate(0.0, 0.0, 0.19)
        // Rotate the wheel based of the ship omega
        matrixStack.rotate(
            Quaternionf(AxisAngle4f((state.shipOmegaY / 20f * Math.PI.toFloat()).toFloat(), 0.0f, 0.0f, 1.0f))
        )
        // Render the wheel
        WheelModels.render(matrixStack, collector, state.helmState, state.blockPos, state.lightCoords, OverlayTexture.NO_OVERLAY)

        matrixStack.popPose()
    }
}
