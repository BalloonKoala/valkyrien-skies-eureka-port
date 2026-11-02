package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.mixinducks.client.render.EntityRenderStateDuck;

@Mixin(EntityRenderState.class)
public class MixinEntityRenderState implements EntityRenderStateDuck {
    @Unique
    private Entity vs$renderedEntity;
    @Unique
    private float vs$renderedPartialTicks;

    @Override
    public void vs$setRenderedEntity(final Entity entity, final float partialTicks) {
        this.vs$renderedEntity = entity;
        this.vs$renderedPartialTicks = partialTicks;
    }

    @Override
    public Entity vs$getRenderedEntity() {
        return this.vs$renderedEntity;
    }

    @Override
    public float vs$getRenderedPartialTicks() {
        return this.vs$renderedPartialTicks;
    }
}
