package org.valkyrienskies.eureka.entity

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import org.valkyrienskies.eureka.EurekaEntities
import org.valkyrienskies.eureka.EurekaItems
import org.valkyrienskies.eureka.item.CannonballItem

class Cannonball : ThrowableItemProjectile {
    constructor(type: EntityType<Cannonball>, level: Level) : super(type, level)

    constructor(level: Level, x: Double, y: Double, z: Double) : super(EurekaEntities.CANNONBALL.get() as EntityType<Cannonball>, level) {
        setPos(x, y, z)
    }

    // Cannonballs from before there were sizes count as small
    override fun getDefaultItem(): Item = EurekaItems.SMALL_CANNONBALL.get()

    /** Each size of cannonball explodes twice as hard as the one before. */
    private val explosionPower: Float
        get() = (item.item as? CannonballItem)?.size?.explosionPower ?: CannonballItem.Size.SMALL.explosionPower

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount > MAX_AGE_TICKS) discard()
    }

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (!level().isClientSide) {
            level().explode(this, x, y, z, explosionPower, Level.ExplosionInteraction.TNT)
            discard()
        }
    }

    private companion object {
        const val MAX_AGE_TICKS = 600
    }
}
