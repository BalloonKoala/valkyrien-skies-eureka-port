package org.valkyrienskies.mod.client

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform

interface IVSCamera {
    fun setupWithShipMounted(
        level: Level,
        renderViewEntity: Entity,
        thirdPerson: Boolean,
        thirdPersonReverse: Boolean,
        partialTicks: Float,
        shipMountedTo: ClientShip,
        inShipPlayerPosition: Vector3dc
    )

    /** Puts the camera at a world position, with a rotation that's relative to the ship it's looking at. */
    fun setCameraInShip(yaw: Float, pitch: Float, renderTransform: ShipTransform, x: Double, y: Double, z: Double)
}
