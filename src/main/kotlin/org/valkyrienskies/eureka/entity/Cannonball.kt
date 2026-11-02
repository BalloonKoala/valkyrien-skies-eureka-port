package org.valkyrienskies.eureka.entity

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import org.valkyrienskies.eureka.EurekaEntities

class Cannonball : ThrowableItemProjectile {
    constructor(type: EntityType<Cannonball>, level: Level) : super(type, level)

    constructor(level: Level, x: Double, y: Double, z: Double) : super(EurekaEntities.CANNONBALL.get() as EntityType<Cannonball>, level) {
        setPos(x, y, z)
    }

    override fun getDefaultItem(): Item = Items.FIRE_CHARGE

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount > MAX_AGE_TICKS) discard()
    }

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (!level().isClientSide) {
            level().explode(this, x, y, z, EXPLOSION_POWER, Level.ExplosionInteraction.TNT)
            discard()
        }
    }

    private companion object {
        const val EXPLOSION_POWER = 3.0f
        const val MAX_AGE_TICKS = 600
    }
}
