package org.valkyrienskies.mod.client

import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity

class EmptyRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<Entity, EntityRenderState>(context) {

    override fun createRenderState(): EntityRenderState = EntityRenderState()
}
