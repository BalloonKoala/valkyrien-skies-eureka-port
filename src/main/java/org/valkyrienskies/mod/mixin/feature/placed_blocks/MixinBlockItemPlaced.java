package org.valkyrienskies.mod.mixin.feature.placed_blocks;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.eureka.util.TurnedOff;
import org.valkyrienskies.mod.common.PlacedBlocks;

/** Whatever a player places with a block item counts as theirs, both halves of a door or a bed included. */
@Mixin(BlockItem.class)
public abstract class MixinBlockItemPlaced {
    @WrapMethod(method = "place")
    private InteractionResult vs$rememberPlaced(final BlockPlaceContext context, final Operation<InteractionResult> original) {
        // Blocks turned off in added-features.toml can't be placed, by players or dispensers
        if (!TurnedOff.itemOn((BlockItem) (Object) this)) {
            TurnedOff.tell(context.getPlayer());
            return InteractionResult.FAIL;
        }
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
