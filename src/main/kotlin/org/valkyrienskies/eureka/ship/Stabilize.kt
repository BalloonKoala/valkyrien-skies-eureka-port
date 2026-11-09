package org.valkyrienskies.eureka.ship

import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.eureka.EurekaConfig
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

fun stabilize(
    ship: PhysShip,
    omega: Vector3dc,
    vel: Vector3dc,
    forces: PhysShip,
    linear: Boolean,
    yaw: Boolean
) {
    val shipUp = Vector3d(0.0, 1.0, 0.0)
    val worldUp = Vector3d(0.0, 1.0, 0.0)
    ship.transform.shipToWorldRotation.transform(shipUp)

    val angleBetween = shipUp.angle(worldUp)
    val idealAngularAcceleration = Vector3d()
    if (angleBetween > .01) {
        val stabilizationRotationAxisNormalized = shipUp.cross(worldUp, Vector3d()).normalize()
        idealAngularAcceleration.add(
            stabilizationRotationAxisNormalized.mul(
                angleBetween,
                stabilizationRotationAxisNormalized
            )
        )
    }

    // Only subtract the x/z components of omega.
    // We still want to allow rotation along the Y-axis (yaw).
    // Except if yaw is true, then we stabilize
    idealAngularAcceleration.sub(
        omega.x(),
        if (!yaw) 0.0 else omega.y(),
        omega.z()
    )

    val stabilizationTorque = ship.transform.shipToWorldRotation.transform(
        ship.momentOfInertia.transform(
            ship.transform.shipToWorldRotation.transformInverse(idealAngularAcceleration)
        )
    )

    val speed = ship.velocity.length()

    // Ships get less steady as they speed up to stableSpeed, the speeds Eureka was made for. Past that the air pushes
    // much harder on them, so they hold themselves level harder instead of tipping over.
    val steadySpeed = EurekaConfig.SERVER.stableSpeed
    val wobble = min(speed, steadySpeed)
    var strength = EurekaConfig.SERVER.stabilizationTorqueConstant / max(1.0, wobble * wobble * EurekaConfig.SERVER.scaledInstability / ship.mass + wobble * EurekaConfig.SERVER.unscaledInstability)
    if (steadySpeed > 0.0 && speed > steadySpeed) strength *= min((speed / steadySpeed).pow(2), MAX_HIGH_SPEED_HOLD)
    stabilizationTorque.mul(strength)
    forces.applyInvariantTorque(stabilizationTorque)

    if (linear) {
        val idealVelocity = Vector3d(vel).negate()
        idealVelocity.y = 0.0

        // ideally this should work the same way as input is scaled
        val s = EurekaConfig.SERVER.linearStabilizeMaxAntiVelocity * (1 - 1 / smoothingATanMax(EurekaConfig.SERVER.linearMaxMass, ship.mass * EurekaConfig.SERVER.linearMassScaling + 1.0)) / 10.0

        if (idealVelocity.lengthSquared() > s * s)
            idealVelocity.normalize(s)

        idealVelocity.mul(ship.mass * (10 - EurekaConfig.SERVER.antiVelocityMassRelevance))
        forces.applyInvariantForce(idealVelocity)
    }
}

// Holding level any harder makes the physics overshoot
private const val MAX_HIGH_SPEED_HOLD = 6.0

private fun smoothingATan(smoothing: Double, x: Double): Double = atan(x * smoothing) / smoothing
private fun smoothingATanMax(max: Double, x: Double): Double = smoothingATan(1 / (max * 0.638), x)
