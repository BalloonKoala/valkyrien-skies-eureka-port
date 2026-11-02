package org.valkyrienskies.mod.mixin.accessors.world.level.block;

import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StairBlock.class)
public interface StairBlockAccessor {
    @Accessor("SHAPE_BOTTOM_OUTER")
    static Map<Direction, VoxelShape> getBottomOuterShapes() {
        throw new AssertionError();
    }

    @Accessor("SHAPE_BOTTOM_STRAIGHT")
    static Map<Direction, VoxelShape> getBottomStraightShapes() {
        throw new AssertionError();
    }

    @Accessor("SHAPE_BOTTOM_INNER")
    static Map<Direction, VoxelShape> getBottomInnerShapes() {
        throw new AssertionError();
    }

    @Accessor("SHAPE_TOP_OUTER")
    static Map<Direction, VoxelShape> getTopOuterShapes() {
        throw new AssertionError();
    }

    @Accessor("SHAPE_TOP_STRAIGHT")
    static Map<Direction, VoxelShape> getTopStraightShapes() {
        throw new AssertionError();
    }

    @Accessor("SHAPE_TOP_INNER")
    static Map<Direction, VoxelShape> getTopInnerShapes() {
        throw new AssertionError();
    }
}
