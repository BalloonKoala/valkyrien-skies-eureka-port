package org.valkyrienskies.mod.mixinducks.client.render;

import net.minecraft.world.entity.Entity;

public interface EntityRenderStateDuck {
    void vs$setRenderedEntity(Entity entity, float partialTicks);

    Entity vs$getRenderedEntity();

    float vs$getRenderedPartialTicks();
}
