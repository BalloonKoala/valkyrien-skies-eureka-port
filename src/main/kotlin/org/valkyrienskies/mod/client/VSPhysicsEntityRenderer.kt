package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier
import org.joml.Quaternionf
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.client.VSPhysicsEntityModel.Companion.LAYER_LOCATION
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity

class VSPhysicsEntityRenderState : LivingEntityRenderState() {
    var shipToWorldRotation: Quaternionf? = null
}

class VSPhysicsEntityRenderer<T : VSPhysicsEntity>(context: Context) :
    MobRenderer<T, VSPhysicsEntityRenderState, VSPhysicsEntityModel>(
        context,
        // Bake the layer
        VSPhysicsEntityModel(context.bakeLayer(LAYER_LOCATION)),
        // Shadow size
        0.0F
    ) {

    override fun createRenderState() = VSPhysicsEntityRenderState()

    override fun extractRenderState(mob: T, state: VSPhysicsEntityRenderState, f: Float) {
        super.extractRenderState(mob, state, f)
        val renderTransform = mob.getRenderTransform(
            ((Minecraft.getInstance() as IShipObjectWorldClientProvider).shipObjectWorld as VsiClientShipWorld)
        )
        state.shipToWorldRotation = renderTransform?.let { Quaternionf(it.shipToWorldRotation) }
    }

    override fun submit(
        state: VSPhysicsEntityRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState
    ) {
        val rotation = state.shipToWorldRotation ?: return

        // Rotate with the ship
        poseStack.rotate(rotation)

        // Offset model from hitbox
        poseStack.translate(0.0, -0.5, 0.0)

        super.submit(state, poseStack, collector, camera)
    }

    override fun getTextureLocation(state: VSPhysicsEntityRenderState): Identifier {
        return Identifier.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "textures/test_sphere.png")
    }
}
