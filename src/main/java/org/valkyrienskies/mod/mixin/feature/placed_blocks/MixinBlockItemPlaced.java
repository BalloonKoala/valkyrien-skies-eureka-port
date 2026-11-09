package org.valkyrienskies.mod.mixin.feature.placed_blocks;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.PlacedBlocks;

/** Whatever a player places with a block item counts as theirs, both halves of a door or a bed included. */
@Mixin(BlockItem.class)
public abstract class MixinBlockItemPlaced {
    @WrapMethod(method = "place")
    private InteractionResult vs$rememberPlaced(final BlockPlaceContext context, final Operation<InteractionResult> original) {
        if (context.getPlayer() == null || !(context.getLevel() instanceof ServerLevel)) {
            return original.call(context);
        }
        PlacedBlocks.startPlayerPlacing();
        try {
            return original.call(context);
        } finally {
            PlacedBlocks.stopPlayerPlacing();
        }
    }
}
