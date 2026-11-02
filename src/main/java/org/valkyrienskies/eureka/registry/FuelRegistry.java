package org.valkyrienskies.eureka.registry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class FuelRegistry {
    public static FuelRegistry INSTANCE = null;

    public abstract int get(Level level, ItemStack stack);
}
