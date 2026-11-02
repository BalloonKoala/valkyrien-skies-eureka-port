package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionOcclusionGraph.class)
public interface SectionOcclusionGraphAccessor {
    @Accessor("needsFrustumUpdate")
    AtomicBoolean getNeedsFrustumUpdate();
}
