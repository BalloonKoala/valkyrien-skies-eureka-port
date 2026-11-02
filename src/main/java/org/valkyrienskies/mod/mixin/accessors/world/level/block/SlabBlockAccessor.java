package org.valkyrienskies.mod.mixin.accessors.world.level.block;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SlabBlock.class)
public interface SlabBlockAccessor {
    @Accessor("SHAPE_BOTTOM")
    static VoxelShape getBottomAABB() {
        throw new AssertionError();
    }

    @Accessor("SHAPE_TOP")
    static VoxelShape getTopAABB() {
        throw new AssertionError();
    }
}
