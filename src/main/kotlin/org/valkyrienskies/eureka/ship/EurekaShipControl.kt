package org.valkyrienskies.eureka.ship

import org.valkyrienskies.mod.common.getLevelFromDimensionId
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import org.joml.*
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.ServerTickListener
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.attachment.removeAttachment
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.mod.common.config.AddedFeatures
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.util.toJOMLD
import java.util.function.Consumer
import kotlin.math.*

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonIgnoreProperties(ignoreUnknown = true)
class EurekaShipControl : ShipPhysicsListener, ServerTickListener {

    @OptIn(GameTickOnly::class)
    @JsonIgnore
    internal var ship: LoadedServerShip? = null

    private var extraForceLinear = 0.0
    private var extraForceAngular = 0.0

    var aligning = false
    var disassembling = false // Disassembling also affects position
    var enginesPaused = false // No engine on this ship burns fuel or gives power while this is set
    var playerBuilt = false // Assembled only from blocks players placed, its blocks count as theirs when taken apart
    private var physConsumption = 0f
    private val anchored get() = anchorsActive > 0

    private var angleUntilAligned = 0.0
    private var positionUntilAligned = Vector3d()
    @OptIn(GameTickOnly::class)
    val canDisassemble
        get() = ship != null &&
            disassembling &&
                (abs(angleUntilAligned) < DISASSEMBLE_THRESHOLD &&
                positionUntilAligned.distanceSquared(this.ship!!.transform.positionInWorld) < 4.0 ||
                disassemblingTime > EurekaConfig.SERVER.disassemblingTimeUntilForced)
    var consumed = 0f
        private set

    private var controlData: ControlData? = null

    /**
     * What the engines are set to, from -1 (full reverse) to 1 (full ahead). W pushes it up and S down, and it stays
     * where it is when the keys are let go, also with nobody at the helm.
     */
    @JsonProperty("throttle")
    @Volatile
    var throttle = 0.0

    // Which way the helm faced when someone last drove, the ship keeps going that way without them
    @JsonProperty("throttleDirection")
    private var throttleDirection: Direction? = null

    @JsonIgnore
    private var throttlePause = 0

    @JsonIgnore
    private var throttleShown = Int.MIN_VALUE

    @JsonIgnore
    private var throttleShownTicks = 0

    // Eureka 1.6.4's cruise control, only while the throttle is turned off in added-features.toml
    @JsonIgnore
    private var wasCruisePressed = false

    @JsonProperty("cruise")
    var isCruising = false

    @JsonIgnore
    @Volatile
    private var cruiseChanged = false

    @JsonIgnore
    var seatedPlayer: Player? = null

    @JsonIgnore
    var oldSpeed = 0.0

    @JsonIgnore
    private var simpleDrag = false

    // How much of the bottom of the ship sits in water, from 0 to 1
    @JsonIgnore
    @Volatile
    private var waterContact = 0.0

    @JsonIgnore
    private var waterCheckTicks = 0

    @JsonIgnore
    var disassemblingTime = 0

    private data class ControlData(
        val seatInDirection: Direction,
        var forwardImpulse: Float = 0.0f,
        var leftImpulse: Float = 0.0f,
        var upImpulse: Float = 0.0f,
        var sprintOn: Boolean = false
    ) {
        companion object {
            fun create(player: SeatedControllingPlayer): ControlData {
                return ControlData(
                    player.seatInDirection,
                    player.forwardImpulse,
                    player.leftImpulse,
                    player.upImpulse,
                    player.sprintOn
                )
            }
        }
    }

    @OptIn(PhysTickOnly::class, GameTickOnly::class, VsBeta::class)
    override fun physTick(physShip: PhysShip, physLevel: PhysLevel) {
        if (helms < 1) {
            // Enable fluid drag if all the helms have been destroyed
            physShip.doFluidDrag = true

            if (EurekaConfig.SERVER.allowFloatersAndBalloonsOnNonEurekaShips) {
                val mass = physShip.mass
                val velY = physShip.velocity.y()

                var balloonForce = getBalloonForce()
                // balloon force 100% at y 100, 0% at y 320
                // the "velY * 10" reduces bobbing by sampling the height position in the future
                balloonForce *= 1 - Math.clamp(0.0, 1.0, (physShip.transform.positionInWorld.y() + velY * 10 - EurekaConfig.SERVER.passiveBalloonMinHeight) / (EurekaConfig.SERVER.passiveBalloonMaxHeight - EurekaConfig.SERVER.passiveBalloonMinHeight))
                balloonForce = min(balloonForce, max(getIdealUpwardForce(EurekaConfig.SERVER.balloonElevationMaxSpeed, velY, mass), 0.0))
                physShip.applyWorldForce(Vector3d(0.0, balloonForce, 0.0))

                physShip.buoyantFactor = getFloaterFactor(mass)
            }

            return
        }
        // Disable fluid drag when helms are present, because it makes ships hard to drive
        physShip.doFluidDrag = EurekaConfig.SERVER.doFluidDrag

        val ship = ship ?: return
        val mass = physShip.mass
        val moiTensor = physShip.momentOfInertia
        val omega: Vector3dc = physShip.angularVelocity
        val vel: Vector3dc = physShip.velocity

        physShip.buoyantFactor = getFloaterFactor(mass)

        // region Aligning

        val invRotation = physShip.transform.shipToWorldRotation.invert(Quaterniond())
        val invRotationAxisAngle = AxisAngle4d(invRotation)
        // Floor makes a number 0 to 3, which corresponds to direction
        val alignTarget = floor((invRotationAxisAngle.angle / (PI * 0.5)) + 4.5).toInt() % 4
        angleUntilAligned = (alignTarget.toDouble() * (0.5 * PI)) - invRotationAxisAngle.angle
        if (disassembling) {
            disassemblingTime++
            val pos = ship.transform.positionInWorld
            positionUntilAligned = pos.floor(Vector3d())
            val direction = pos.sub(positionUntilAligned, Vector3d())
            physShip.applyWorldForce(direction)
        } else disassemblingTime = 0
        if ((aligning) && abs(angleUntilAligned) > ALIGN_THRESHOLD) {
            if (angleUntilAligned < 0.3 && angleUntilAligned > 0.0) angleUntilAligned = 0.3
            if (angleUntilAligned > -0.3 && angleUntilAligned < 0.0) angleUntilAligned = -0.3

            val idealOmega = Vector3d(invRotationAxisAngle.x, invRotationAxisAngle.y, invRotationAxisAngle.z)
                .mul(-angleUntilAligned)
                .mul(EurekaConfig.SERVER.stabilizationSpeed)

            val idealTorque = moiTensor.transform(idealOmega)

            physShip.applyWorldTorque(idealTorque)
        }
        // endregion

        val controllingPlayer = ship.getAttachment(SeatedControllingPlayer::class.java)
        val validPlayer = controllingPlayer != null && !anchored


        val useThrottle = AddedFeatures.throttle
        if (anchored) {
            if (isCruising) {
                isCruising = false
                cruiseChanged = true
            }
            physShip.isStatic = true
            return
        }

        if (!useThrottle) {
            // Eureka 1.6.4: hold W or S to move, C keeps the last controls going
            if (validPlayer) {
                val current = getControlData(controllingPlayer)
                // only take the latest control data if the player is not cruising
                if (!isCruising) controlData = current
                wasCruisePressed = controllingPlayer.cruise
            } else if (!isCruising) {
                controlData = null
                oldSpeed = 0.0
            }
        } else if (validPlayer) {
            controlData = ControlData.create(controllingPlayer)
        } else {
            // Nobody at the helm: the throttle keeps the ship going the way it last went, without turning or climbing
            val direction = throttleDirection
            controlData = if (throttle != 0.0 && direction != null) ControlData(direction) else null
            if (controlData == null) oldSpeed = 0.0
        }

        stabilize(
            physShip,
            omega,
            vel,
            physShip,
            (if (useThrottle) controlData == null else !validPlayer) && !aligning,
            !validPlayer
        )

        var idealUpwardVel = Vector3d(0.0, 0.0, 0.0)

        val balloonForce = getBalloonForce()
        controlData?.let { control ->
            // 0-1, how much of the ship's weight the balloons carry
            val liftFraction = min(1.0, balloonForce / (mass * -GRAVITY))
            applyPlayerControl(control, physShip, liftFraction)
            idealUpwardVel = getPlayerUpwardVel(control, mass)
        }

        physShip.applyWorldForce(Vector3d(0.0,
            min(balloonForce, max(getIdealUpwardForce(idealUpwardVel.y(), vel.y(), mass), 0.0)) +
            // Add drag to the y-component
            vel.y() * -mass,
            0.0)
        )
        // endregion

        physShip.isStatic = anchored
    }

    private fun getControlData(player: SeatedControllingPlayer): ControlData {
        val currentControlData = ControlData.create(player)
        if (!wasCruisePressed && player.cruise) {
            // the player pressed the cruise button
            isCruising = !isCruising
            cruiseChanged = true
        } else if (!player.cruise && isCruising &&
            (player.leftImpulse != 0.0f || player.sprintOn || player.upImpulse != 0.0f || player.forwardImpulse != 0.0f) &&
            currentControlData != controlData
        ) {
            // The player pressed another button
            isCruising = false
            cruiseChanged = true
        }
        return currentControlData
    }

    private fun getBalloonForce(): Double {
        // Disable if maxBalloonsPerEngine <= 0
        if (EurekaConfig.SERVER.maxBalloonsPerEngine > 0) {
            // remove power from unpowered balloons
            return balloons * forcePerBalloon * min(
                1.0,
                // (currentTotalEnginePower * maxBalloonsPerEngine) / (enginePowerAtMaxHeat * balloonsCount)
                ( extraForceLinear * EurekaConfig.SERVER.maxBalloonsPerEngine ) / ( EurekaConfig.SERVER.enginePowerLinear * balloons )
            )
        }
        return balloons * forcePerBalloon
    }

    private fun getFloaterFactor(mass: Double): Double {
        return 1.0 + floaters * min(
            EurekaConfig.SERVER.floaterBuoyantLift / mass,
            EurekaConfig.SERVER.maxFloaterBuoyantFactor
        )
    }

    private fun getIdealUpwardForce(idealUpwardVelY: Double, velY: Double, mass: Double): Double {
        return (idealUpwardVelY - velY - (GRAVITY / EurekaConfig.SERVER.elevationSnappiness)) * mass * EurekaConfig.SERVER.elevationSnappiness
    }

    @OptIn(PhysTickOnly::class, GameTickOnly::class)
    private fun applyPlayerControl(control: ControlData, physShip: PhysShip, liftFraction: Double) {

        val ship = ship ?: return
        val transform = physShip.transform
        val aabb = ship.worldAABB
        val center = transform.positionInWorld

        // region Player controlled rotation
        val moiTensor = physShip.momentOfInertia
        val omega: Vector3dc = physShip.angularVelocity

        val farthest = run {
            var dist = center.distance(aabb.minX(), center.y(), aabb.minZ())
            dist = max(dist, center.distance(aabb.minX(), center.y(), aabb.maxZ()))
            dist = max(dist, center.distance(aabb.maxX(), center.y(), aabb.minZ()))
            dist = max(dist, center.distance(aabb.maxX(), center.y(), aabb.maxZ()))

            dist
        }
        val largestDistance = farthest.coerceIn(0.5, EurekaConfig.SERVER.maxSizeForTurnSpeedPenalty)

        val config = EurekaConfig.SERVER
        // acceleration = alpha * r
        // therefore: maxAlpha = maxAcceleration / r

        if (!AddedFeatures.turningNeedsEngines) {
            // Eureka 1.6.4's turning: a little without engines, more with every engine, braking as hard as it turns
            val maxOmegaY = (ORIGINAL_TURN_SPEED + extraForceAngular) / largestDistance
            val maxAlphaY = config.turnAcceleration / largestDistance
            val multiplier =
                if (abs(omega.y()) < maxOmegaY && control.leftImpulse != 0.0f) control.leftImpulse.toDouble()
                else -omega.y().coerceIn(-1.0, 1.0)
            val alphaY = multiplier * maxAlphaY
            physShip.applyWorldTorque(
                moiTensor.transform(Vector3d(0.0, alphaY, 0.0))
                    .add(getPlayerControlledBanking(control, physShip, moiTensor, -alphaY, ORIGINAL_BANK_STRENGTH))
            )
            physShip.applyWorldForce(getPlayerForwardVel(control, physShip, liftFraction))
            return
        }

        // Turning takes engines. Each running one turns the ship a bit, a heavier ship less, levelling off at the
        // fastest a ship may turn. The far end of a long ship may only swing round so fast, so long ships turn slower.
        val turnPush = runningEngines() * config.turnDegreesPerEngine * massFactor(physShip.mass)
        val topTurn = min(config.maxTurnDegrees, Math.toDegrees(config.maxTurnEdgeSpeed / max(farthest, 0.5)))
        val maxOmegaY = if (topTurn > 0.0) Math.toRadians(topTurn * tanh(turnPush / topTurn)) else 0.0
        // Up to that turning speed in about half a second, and stopped again as quickly
        val turnAlphaY = maxOmegaY * TURN_RESPONSE
        val stopAlphaY = max(config.turnAcceleration / largestDistance, turnAlphaY)

        val turning = abs(omega.y()) < maxOmegaY && control.leftImpulse != 0.0f

        // With no turn key held the ship stops turning. Past stableSpeed the air pushes harder on it, so it stops harder.
        val speed = physShip.velocity.length()
        val steady = config.stableSpeed
        val stopHarder = if (steady > 0.0 && speed > steady) min((speed / steady).pow(2), MAX_TURN_STOP_BOOST) else 1.0
        val idealAlphaY =
            if (turning) control.leftImpulse * turnAlphaY
            else -(omega.y() / TURN_STOP_EASE).coerceIn(-1.0, 1.0) * stopHarder * stopAlphaY

        // A lean into the turn while turning, none while stopping, or small ships rocked about
        val bank = if (turning) -idealAlphaY else 0.0
        physShip.applyWorldTorque(moiTensor.transform(Vector3d(0.0, idealAlphaY, 0.0)).add(getPlayerControlledBanking(control, physShip, moiTensor, bank, BANK_STRENGTH)))

        physShip.applyWorldForce(getPlayerForwardVel(control, physShip, liftFraction))
    }

    @OptIn(PhysTickOnly::class)
    private fun getPlayerControlledBanking(
        control: ControlData, physShip: PhysShip, moiTensor: Matrix3dc, strength: Double, bankStrength: Double
    ): Vector3d {
        val rotationVector = control.seatInDirection.unitVec3i.toJOMLD()
        physShip.transform.shipToWorldRotation.transform(rotationVector)
        rotationVector.y = 0.0
        rotationVector.mul(strength * bankStrength)

        physShip.transform.shipToWorldRotation.transform(
            moiTensor.transform(
                physShip.transform.shipToWorldRotation.transformInverse(rotationVector)
            )
        )

        return rotationVector
    }

    // Player controlled forward and backward thrust
    @OptIn(PhysTickOnly::class)
    private fun getPlayerForwardVel(control: ControlData, physShip: PhysShip, liftFraction: Double): Vector3d {

        val scaledMass = physShip.mass * EurekaConfig.SERVER.speedMassScale
        val vel: Vector3dc = physShip.velocity

        // region Player controlled forward and backward thrust
        val forwardVector = control.seatInDirection.unitVec3i.toJOMLD()
        physShip.transform.shipToWorldRotation.transform(forwardVector)
        forwardVector.normalize()

        val s = 1 / smoothingATanMax(
            EurekaConfig.SERVER.linearMaxMass,
            physShip.mass * EurekaConfig.SERVER.linearMassScaling + EurekaConfig.SERVER.linearBaseMass
        )

        // The throttle, or with it turned off how far W or S is held, like Eureka 1.6.4
        val target = if (AddedFeatures.throttle) throttle else control.forwardImpulse.toDouble()
        oldSpeed = oldSpeed * (1 - s) + target * s // from -1 to 1.
        val baseSpeed = EurekaConfig.SERVER.linearBaseSpeed
        var speed = oldSpeed * baseSpeed
        // Turned off in added-features.toml, engines only get what VS says is in the water
        val wetness = if (AddedFeatures.boatWaterContact) max(physShip.liquidOverlap, waterContact) else physShip.liquidOverlap

        if (extraForceLinear != 0.0 && !AddedFeatures.engineSpeedByMass) {
            // Eureka 1.6.4's engine boost
            var boost = max((extraForceLinear - EurekaConfig.SERVER.enginePowerLinear * ORIGINAL_ENGINE_BOOST_OFFSET) * ORIGINAL_ENGINE_BOOST, 0.0)
            boost = extraForceLinear + boost + boost * boost * ORIGINAL_ENGINE_BOOST_EXPONENTIAL_POWER
            val required = EurekaConfig.SERVER.waterContactRequiredForNonFlyingShipEngine
            val waterFactor = if (required <= 0.0) 1.0 else min(1.0, wetness / required)
            boost *= liftFraction + (1.0 - liftFraction) * waterFactor
            boost /= scaledMass
            speed += if (speed < 0) {
                smoothingATanMax(EurekaConfig.SERVER.maxReverseSpeedFromEngines, boost * oldSpeed)
            } else {
                smoothingATanMax(ORIGINAL_MAX_SPEED_FROM_ENGINES, boost * oldSpeed)
            }
        } else if (extraForceLinear != 0.0) {
            // Each engine pushes a heavier ship less
            var push = runningEngines() * EurekaConfig.SERVER.engineSpeedPerEngine * massFactor(physShip.mass)
            // a ship with 50% lift would allow for 50% of power for ship not touching ground.
            val required = EurekaConfig.SERVER.waterContactRequiredForNonFlyingShipEngine
            val waterFactor = if (required <= 0.0) 1.0 else min(1.0, wetness / required)
            push *= liftFraction + (1.0 - liftFraction) * waterFactor
            push /= EurekaConfig.SERVER.speedMassScale

            // Speed grows with every engine and levels off at the top speed. A heavy ship gets there too, with more engines.
            val limit = if (speed < 0) {
                EurekaConfig.SERVER.maxReverseSpeedFromEngines
            } else {
                max(EurekaConfig.SERVER.maxShipSpeed - baseSpeed, 0.0)
            }
            if (limit > 0.0) speed += limit * tanh(push * oldSpeed / limit)
        }
        // Never hand the physics a broken number, whatever the config says
        if (!speed.isFinite()) speed = 0.0

        val playerUpDirection = physShip.transform.shipToWorldRotation.transform(Vector3d(0.0, 1.0, 0.0))
        val velOrthogonalToPlayerUp = vel.sub(playerUpDirection.mul(playerUpDirection.dot(vel)), Vector3d())

        // This is the speed that the ship is always allowed to go out, without engines
        val baseForwardVel = forwardVector.mul(speed)
        // Air drag grows with the square of speed. Past stableSpeed the ship pushes harder towards its speed, or drag
        // would hold small fast ships far under it.
        val response = if (!AddedFeatures.highSpeedLevelling) 1.0 else max(1.0, abs(speed) / max(EurekaConfig.SERVER.stableSpeed, 1.0))
        val forwardForce = baseForwardVel.sub(velOrthogonalToPlayerUp).mul(scaledMass * response)

        return forwardForce
    }

    // How many fully heated engines the running ones are worth
    private fun runningEngines(): Double = extraForceLinear / max(EurekaConfig.SERVER.enginePowerLinear.toDouble(), 1.0)

    // How much less each engine does on this ship than on one of engineReferenceMass
    private fun massFactor(mass: Double): Double =
        (max(EurekaConfig.SERVER.engineReferenceMass, 1.0) / max(mass, 1.0))
            .pow(EurekaConfig.SERVER.engineMassExponent.coerceIn(0.0, 2.0))

    // Player controlled elevation
    private fun getPlayerUpwardVel(control: ControlData, mass: Double): Vector3d {
        if (control.upImpulse != 0.0f) {

            var v =
                if (control.upImpulse < 0.0f)
                    control.upImpulse * EurekaConfig.SERVER.baseImpulseDescendRate
                else
                    control.upImpulse * (
                        EurekaConfig.SERVER.baseImpulseElevationRate +
                        // Smoothing for how the elevation scales as you approaches the balloonElevationMaxSpeed
                        smoothing(2.0, EurekaConfig.SERVER.balloonElevationMaxSpeed, (balloons * forcePerBalloon) / mass)
                    )

            return Vector3d(0.0, v, 0.0)
        }
        return Vector3d(0.0, 0.0, 0.0)
    }

    var powerLinear = 0.0
    var powerAngular = 0.0
    var anchors = 0 // Amount of anchors
        set(v) {
            field = v; deleteIfEmpty()
        }

    var anchorsActive = 0 // Anchors that are active
    var balloons = 0 // Amount of balloons
        set(v) {
            field = v; deleteIfEmpty()
        }

    var helms = 0 // Amount of helms
        set(v) {
            field = v; deleteIfEmpty()
        }

    var floaters = 0 // Amount of floaters * 15
        set(v) {
            field = v; deleteIfEmpty()
        }

    @OptIn(VsBeta::class, GameTickOnly::class)
    private fun deleteIfEmpty() {
        if (helms <= 0 && floaters <= 0 && anchors <= 0 && balloons <= 0) {
            ship?.removeAttachment<EurekaShipControl>()
        }
    }

    /**
     * f(x) = max - smoothing / (x + (smoothing / max))
     */
    private fun smoothing(smoothing: Double, max: Double, x: Double): Double = max - smoothing / (x + (smoothing / max))

    /**
     * g(x) = (tan^(-1)(x * smoothing)) / smoothing
     */
    private fun smoothingATan(smoothing: Double, x: Double): Double = atan(x * smoothing) / smoothing

    // limit x to max using ATan
    private fun smoothingATanMax(max: Double, x: Double): Double = smoothingATan(1 / (max * 0.638), x)

    companion object {
        @OptIn(GameTickOnly::class)
        fun getOrCreate(ship: LoadedServerShip): EurekaShipControl {
            return ship.getAttachment<EurekaShipControl>()
                ?: EurekaShipControl().also { ship.setAttachment(it) }
        }

        @OptIn(GameTickOnly::class, VsBeta::class)
        fun deferUntilLoaded(ship: ServerShip, consumer: Consumer<EurekaShipControl>){
            if(ship is LoadedServerShip) {
                consumer.accept(getOrCreate(ship))
            } else {
                ValkyrienSkiesMod.vsCore.shipLoadEvent.once(
                    { event -> event.ship.id == ship.id },
                    {event -> consumer.accept(getOrCreate(event.ship))}
                )
            }
        }

        // Below this turn rate in radians a second, letting go eases the turn out instead of braking at full strength
        private const val TURN_STOP_EASE = 0.2
        // Stopping a turn any harder makes the physics overshoot
        private const val MAX_TURN_STOP_BOOST = 6.0
        private const val ALIGN_THRESHOLD = 0.01
        private const val DISASSEMBLE_THRESHOLD = 0.02
        // Full throttle from 0 in 2 seconds
        private const val THROTTLE_STEP = 0.025
        private const val THROTTLE_ZERO_TICKS = 4
        private const val THROTTLE_SHOW_TICKS = 20
        private const val TURN_RESPONSE = 2.0
        private const val BANK_STRENGTH = 0.5
        private const val WATER_CHECK_TICKS = 10
        private const val WATER_CHECK_GRID = 5
        private val forcePerBalloon get() = EurekaConfig.SERVER.massPerBalloon * -GRAVITY

        // Eureka 1.6.4's defaults, for the parts of it added-features.toml can bring back
        const val ORIGINAL_ENGINE_POWER_ANGULAR = 1.0
        private const val ORIGINAL_TURN_SPEED = 3.0
        private const val ORIGINAL_BANK_STRENGTH = 1.5
        private const val ORIGINAL_ENGINE_BOOST = 0.2
        private const val ORIGINAL_ENGINE_BOOST_OFFSET = 2.5
        private const val ORIGINAL_ENGINE_BOOST_EXPONENTIAL_POWER = 0.000001
        private const val ORIGINAL_MAX_SPEED_FROM_ENGINES = 12.0

        private const val GRAVITY = -10.0
    }

    @OptIn(GameTickOnly::class)
    override fun onServerTick() {
        extraForceLinear = powerLinear
        powerLinear = 0.0
        extraForceAngular = powerAngular
        powerAngular = 0.0

        useSimpleDrag()
        if (AddedFeatures.boatWaterContact) measureWaterContact() else waterContact = 0.0
        if (AddedFeatures.throttle) {
            updateThrottle()
        } else {
            throttleShown = Int.MIN_VALUE
            if (cruiseChanged) {
                cruiseChanged = false
                seatedPlayer?.sendOverlayMessage(
                    Component.translatable(if (isCruising) "hud.vs_eureka.start_cruising" else "hud.vs_eureka.stop_cruising")
                )
            }
        }


        consumed = physConsumption * /* should be physics ticks based*/ 0.1f
        physConsumption = 0.0f
    }

    /**
     * W pushes the throttle up and S down while someone drives. Going through 0 it stops there for a moment first, so
     * reverse only comes on when S stays held.
     */
    @OptIn(GameTickOnly::class)
    private fun updateThrottle() {
        val driver = ship?.getAttachment(SeatedControllingPlayer::class.java)
        if (driver != null) {
            throttleDirection = driver.seatInDirection
            val push = driver.forwardImpulse.toDouble().coerceIn(-1.0, 1.0)
            if (push != 0.0) {
                if (throttlePause > 0) {
                    throttlePause--
                } else {
                    val next = (throttle + push * THROTTLE_STEP).coerceIn(-1.0, 1.0)
                    if (throttle != 0.0 && (next == 0.0 || sign(next) != sign(throttle))) {
                        throttle = 0.0
                        throttlePause = THROTTLE_ZERO_TICKS
                    } else {
                        throttle = next
                    }
                }
            }
        }
        showThrottle(driver != null)
    }

    // Above the XP bar of whoever drives, whenever it changes and every second so it doesn't fade
    private fun showThrottle(driving: Boolean) {
        val player = seatedPlayer
        if (!driving || player == null) {
            throttleShown = Int.MIN_VALUE
            return
        }
        val percent = Math.round(throttle * 100).toInt()
        if (percent == throttleShown && ++throttleShownTicks < THROTTLE_SHOW_TICKS) return
        throttleShown = percent
        throttleShownTicks = 0
        player.sendOverlayMessage(Component.translatable("hud.vs_eureka.throttle", percent))
    }

    /**
     * VS core only says how much of a ship is in water while it works out drag, and on a big ship it says almost none,
     * so a big boat's engines hardly pushed. This looks under the ship itself: a grid of spots under the bottom of it.
     */
    @OptIn(GameTickOnly::class)
    private fun measureWaterContact() {
        if (++waterCheckTicks < WATER_CHECK_TICKS) return
        waterCheckTicks = 0
        val ship = ship ?: return
        val level = ValkyrienSkiesMod.currentServer?.getLevelFromDimensionId(ship.chunkClaimDimension) ?: return
        val box = ship.shipAABB ?: return
        var wet = 0
        val spot = Vector3d()
        val pos = net.minecraft.core.BlockPos.MutableBlockPos()
        for (i in 0 until WATER_CHECK_GRID) for (j in 0 until WATER_CHECK_GRID) {
            spot.set(
                box.minX() + (box.maxX() - box.minX()) * (i + 0.5) / WATER_CHECK_GRID,
                box.minY() - 0.5,
                box.minZ() + (box.maxZ() - box.minZ()) * (j + 0.5) / WATER_CHECK_GRID
            )
            ship.shipToWorld.transformPosition(spot)
            pos.set(floor(spot.x).toInt(), floor(spot.y).toInt(), floor(spot.z).toInt())
            if (!level.getFluidState(pos).isEmpty || !level.getFluidState(pos.move(0, 1, 0)).isEmpty) wet++
        }
        waterContact = wet.toDouble() / (WATER_CHECK_GRID * WATER_CHECK_GRID)
    }

    /**
     * VS core works out air drag and lift block by block every physics step, which on a ship of thousands of blocks
     * takes most of the physics thread and makes ships and the players on them stutter. A big ship with a helm doesn't
     * need it: steering already holds it to its speed and Eureka's levelling slows it down when nobody steers.
     */
    @OptIn(GameTickOnly::class)
    private fun useSimpleDrag() {
        val ship = ship ?: return
        val simple = AddedFeatures.noBlockDragOnBigShips && helms > 0 && ship.inertiaData.mass > EurekaConfig.SERVER.noBlockDragAboveMass
        if (simple == simpleDrag) return
        val drag = ship.dragController ?: return
        simpleDrag = simple
        if (simple) {
            drag.disableDrag()
        } else if (org.valkyrienskies.core.impl.config.VSCoreConfig.SERVER.ds.enableDrag) {
            drag.enableDrag()
        }
    }
}
