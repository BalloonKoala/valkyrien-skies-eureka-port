package org.valkyrienskies.eureka.gui.wiring

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.client.IVSCamera
import kotlin.math.cos
import kotlin.math.sin

/**
 * The camera of the wiring screen. It flies around in ship space, so the ship stays put even while it's moving.
 */
object WiringCamera {
    private const val MAX_PITCH = 89.9f
    private const val MIN_SPEED = 1.0
    private const val MAX_SPEED = 64.0
    private const val SPRINT_FACTOR = 3.0
    private const val NANOS_PER_SECOND = 1.0e9

    private var ship: ClientShip? = null
    private var lastFrame = 0L
    private var flying = true

    val position = Vector3d()
    var yaw = 0f
    var pitch = 0f
    var speed = 8.0
        set(value) {
            field = value.coerceIn(MIN_SPEED, MAX_SPEED)
        }

    fun start(ship: ClientShip, position: Vector3dc, yaw: Float, pitch: Float) {
        this.ship = ship
        this.position.set(position)
        this.yaw = yaw
        this.pitch = pitch
        lastFrame = System.nanoTime()
    }

    fun stop() {
        ship = null
    }

    /** Stops flying around for as long as a key is being picked for a button. */
    fun setFlying(flying: Boolean) {
        this.flying = flying
    }

    fun turn(deltaYaw: Float, deltaPitch: Float) {
        yaw += deltaYaw
        pitch = (pitch + deltaPitch).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun forward(): Vector3d {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        return Vector3d(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
    }

    /** Runs once a frame in place of following the player. */
    @JvmStatic
    fun apply(camera: IVSCamera): Boolean {
        val ship = ship ?: return false
        val now = System.nanoTime()
        val seconds = (now - lastFrame) / NANOS_PER_SECOND
        lastFrame = now
        if (flying) fly(seconds)
        val world = ship.renderTransform.shipToWorld.transformPosition(position, Vector3d())
        camera.setCameraInShip(yaw, pitch, ship.renderTransform, world.x, world.y, world.z)
        return true
    }

    private fun held(mapping: KeyMapping): Boolean = InputConstants.isKeyDown(InputConstants.getKey(mapping.saveString()).value)

    private fun fly(seconds: Double) {
        val options = Minecraft.getInstance().options
        val forward = forward()
        val yawRad = Math.toRadians(yaw.toDouble())
        val right = Vector3d(-cos(yawRad), 0.0, -sin(yawRad))
        val step = Vector3d()
        if (held(options.keyUp)) step.add(forward)
        if (held(options.keyDown)) step.sub(forward)
        if (held(options.keyRight)) step.add(right)
        if (held(options.keyLeft)) step.sub(right)
        if (held(options.keyJump)) step.y += 1.0
        if (held(options.keyShift)) step.y -= 1.0
        if (step.lengthSquared() > 0.0) {
            val distance = seconds * speed * if (held(options.keySprint)) SPRINT_FACTOR else 1.0
            position.fma(distance, step.normalize())
        }
    }
}
