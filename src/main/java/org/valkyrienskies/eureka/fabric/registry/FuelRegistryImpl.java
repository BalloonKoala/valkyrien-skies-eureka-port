package org.valkyrienskies.eureka.fabric.registry;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class FuelRegistryImpl extends org.valkyrienskies.eureka.registry.FuelRegistry {
    public FuelRegistryImpl() {
        INSTANCE = this;
    }

    @Override
    public int get(Level level, ItemStack stack) {
        final CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
        if (fuel == null) {
            return 0;
        }
        // Burn times can only be worked out on the server, the client just needs to know this item is a fuel
        if (!(level instanceof final ServerLevel serverLevel)) {
            return 1;
        }
        final LootContext context = new LootContext.Builder(
            new LootParams.Builder(serverLevel).create(LootContextParamSets.EMPTY)
        ).create(Optional.empty());
        return fuel.burnTime().get(context, 0);
    }
}
