package org.valkyrienskies.eureka.gui.wiring

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector4f
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.eureka.wiring.WiringBindPayload
import org.valkyrienskies.eureka.wiring.WiringButton
import org.valkyrienskies.eureka.wiring.WiringConnectPayload
import org.valkyrienskies.eureka.wiring.WiringContact
import org.valkyrienskies.eureka.wiring.WiringDataPayload
import org.valkyrienskies.mod.common.getShipManagingPos
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lets the player fly a camera around their ship and wire up its wireless buttons. The markers are drawn over the
 * picture, so they show through the ship.
 */
class ShipWiringScreen(private var data: WiringDataPayload) : Screen(Component.translatable("gui.vs_eureka.wiring")) {
    private var ship: ClientShip? = null
    private var hudWasHidden = false

    // The button waiting for a key, the button the mouse went down on and the one being dragged to a contact point
    private var binding: WiringButton? = null
    private var pressed: WiringButton? = null
    private var dragging: WiringButton? = null
    private var orbitAround: Vector3d? = null
    private var pressX = 0.0
    private var pressY = 0.0
    private var mouseX = 0.0
    private var mouseY = 0.0

    fun update(data: WiringDataPayload) {
        this.data = data
    }

    override fun init() {
        val client = minecraft
        if (ship == null) {
            val found = client.level?.getShipManagingPos(data.helm) as? ClientShip
            if (found == null) {
                onClose()
                return
            }
            ship = found
            hudWasHidden = client.gui.hud.isHidden
            if (!hudWasHidden) client.gui.hud.toggle()
            startCamera(found)
        }
    }

    // Starts behind and above the ship, looking at the middle of it
    private fun startCamera(ship: ClientShip) {
        val middle = shipMiddle(ship)
        val box = ship.shipAABB
        val size = if (box == null) MIN_START_DISTANCE else max(box.maxX() - box.minX(), box.maxZ() - box.minZ()).toDouble()
        val distance = max(size * START_DISTANCE_FACTOR, MIN_START_DISTANCE)
        val position = Vector3d(middle).add(0.0, distance * START_HEIGHT_FACTOR, distance)
        val look = Vector3d(middle).sub(position)
        val yaw = Math.toDegrees(atan2(-look.x, look.z)).toFloat()
        val pitch = Math.toDegrees(-atan2(look.y, hypot(look.x, look.z))).toFloat()
        WiringCamera.start(ship, position, yaw, pitch)
    }

    private fun shipMiddle(ship: ClientShip): Vector3d {
        val box = ship.shipAABB ?: return Vector3d(ship.transform.positionInShip)
        return Vector3d(
            (box.minX() + box.maxX()) / 2.0 + 0.5, (box.minY() + box.maxY()) / 2.0 + 0.5, (box.minZ() + box.maxZ()) / 2.0 + 0.5
        )
    }

    override fun removed() {
        WiringCamera.stop()
        if (!hudWasHidden) minecraft.gui.hud.toggle()
    }

    override fun isPauseScreen(): Boolean = false

    // The whole point is to see the ship
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
    }

    /** Where a spot on the ship is on the screen, or null if it's behind the camera. */
    private fun project(pos: BlockPos): IntArray? {
        val ship = ship ?: return null
        val camera = minecraft.gameRenderer.mainCamera()
        val world = ship.renderTransform.shipToWorld.transformPosition(Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5))
        val eye = camera.position()
        val clip = Vector4f((world.x - eye.x).toFloat(), (world.y - eye.y).toFloat(), (world.z - eye.z).toFloat(), 1f)
        camera.getViewRotationProjectionMatrix(Matrix4f()).transform(clip)
        if (clip.w <= MIN_CLIP_W) return null
        return intArrayOf(
            ((clip.x / clip.w + 1f) / 2f * width).toInt(),
            ((1f - clip.y / clip.w) / 2f * height).toInt()
        )
    }

    /** For tests: the buttons this screen shows. */
    fun buttons(): List<WiringButton> = data.buttons

    /** For tests: the contact points this screen shows. */
    fun contacts(): List<WiringContact> = data.contacts

    /** For tests: where the marker of something on the ship is drawn. */
    fun markerPosition(pos: BlockPos): IntArray? = project(pos)

    private fun buttonAt(x: Double, y: Double): WiringButton? =
        data.buttons.mapNotNull { button -> project(button.pos)?.let { button to hypot(it[0] - x, it[1] - y) } }
            .filter { it.second <= PICK_RADIUS }.minByOrNull { it.second }?.first

    private fun contactAt(x: Double, y: Double): WiringContact? =
        data.contacts.mapNotNull { contact -> project(contact.pos)?.let { contact to hypot(it[0] - x, it[1] - y) } }
            .filter { it.second <= PICK_RADIUS }.minByOrNull { it.second }?.first

    private fun contactOf(button: WiringButton): BlockPos? =
        button.contact ?: data.contacts.filter { it.isHelm }.minByOrNull { it.pos.distSqr(button.pos) }?.pos

    private fun keyName(key: String): Component =
        if (key.isEmpty()) NO_KEY_TEXT else InputConstants.getKey(key).displayName

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        for (button in data.buttons) {
            if (button === dragging) continue
            val from = project(button.pos) ?: continue
            val to = contactOf(button)?.let { project(it) } ?: continue
            line(graphics, from[0], from[1], to[0], to[1], WIRE_COLOR)
        }
        dragging?.let { button ->
            project(button.pos)?.let { line(graphics, it[0], it[1], mouseX, mouseY, DRAG_COLOR) }
        }

        for (contact in data.contacts) {
            val at = project(contact.pos) ?: continue
            dot(graphics, at[0], at[1], CONTACT_RADIUS + 1, OUTLINE_COLOR)
            dot(graphics, at[0], at[1], CONTACT_RADIUS, CONTACT_COLOR)
            val label = if (contact.isHelm) HELM_TEXT else CUSHION_TEXT
            graphics.centeredText(font, label, at[0], at[1] + CONTACT_RADIUS + 3, CONTACT_TEXT_COLOR)
        }
        for (button in data.buttons) {
            val at = project(button.pos) ?: continue
            val color = if (button.pos == binding?.pos) BINDING_COLOR else BUTTON_COLOR
            dot(graphics, at[0], at[1], BUTTON_RADIUS + 1, OUTLINE_COLOR)
            dot(graphics, at[0], at[1], BUTTON_RADIUS, color)
            graphics.centeredText(font, keyName(button.key), at[0], at[1] - BUTTON_RADIUS - 11, BUTTON_TEXT_COLOR)
        }

        graphics.centeredText(font, title, width / 2, 6, TITLE_COLOR)
        graphics.centeredText(font, MOVE_HELP_TEXT, width / 2, 18, HELP_COLOR)
        graphics.centeredText(font, LOOK_HELP_TEXT, width / 2, 28, HELP_COLOR)
        val status = when {
            binding != null -> BINDING_TEXT
            data.buttons.isEmpty() -> NONE_TEXT
            else -> TIP_TEXT
        }
        graphics.centeredText(font, status, width / 2, height - 14, TITLE_COLOR)
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks)
    }

    private fun dot(graphics: GuiGraphicsExtractor, x: Int, y: Int, radius: Int, color: Int) {
        for (row in -radius..radius) {
            val half = sqrt((radius * radius - row * row).toDouble()).toInt()
            graphics.fill(x - half, y + row, x + half + 1, y + row + 1, color)
        }
    }

    private fun line(graphics: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val steps = max(abs(x1 - x0), abs(y1 - y0))
        if (steps == 0 || steps > MAX_LINE_STEPS) return
        var step = 0
        while (step <= steps) {
            val x = x0 + (x1 - x0) * step / steps
            val y = y0 + (y1 - y0) * step / steps
            graphics.fill(x, y, x + 1, y + 1, color)
            step += LINE_DOT_SPACING
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick)
        stopBinding()
        pressX = event.x()
        pressY = event.y()
        pressed = buttonAt(event.x(), event.y())
        val ship = ship
        if (pressed == null && ship != null) orbitAround = shipMiddle(ship)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        when {
            event.button() == InputConstants.MOUSE_BUTTON_RIGHT -> WiringCamera.turn(dx.toFloat() * LOOK_SPEED, dy.toFloat() * LOOK_SPEED)
            pressed != null -> {
                if (hypot(event.x() - pressX, event.y() - pressY) > DRAG_START_DISTANCE) dragging = pressed
            }
            else -> orbitAround?.let { orbit(it, dx.toFloat() * ORBIT_SPEED, dy.toFloat() * ORBIT_SPEED) }
        }
        return true
    }

    // Turns the view and swings the camera around [middle] so that it keeps looking at the same spot
    private fun orbit(middle: Vector3d, deltaYaw: Float, deltaPitch: Float) {
        val distance = WiringCamera.position.distance(middle)
        val focus = Vector3d(WiringCamera.position).fma(distance, WiringCamera.forward())
        WiringCamera.turn(deltaYaw, deltaPitch)
        WiringCamera.position.set(focus).fma(-distance, WiringCamera.forward())
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val wire = dragging
        val press = pressed
        dragging = null
        pressed = null
        orbitAround = null
        if (wire != null) {
            contactAt(event.x(), event.y())?.let { ClientPlayNetworking.send(WiringConnectPayload(data.helm, wire.pos, it.pos)) }
        } else if (press != null) {
            binding = press
            WiringCamera.setFlying(false)
        }
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        WiringCamera.speed *= if (scrollY > 0) SPEED_STEP else 1.0 / SPEED_STEP
        return true
    }

    private fun stopBinding() {
        binding = null
        WiringCamera.setFlying(true)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val button = binding ?: return super.keyPressed(event)
        stopBinding()
        if (event.key() != InputConstants.KEY_ESCAPE) {
            ClientPlayNetworking.send(WiringBindPayload(data.helm, button.pos, InputConstants.getKey(event).name))
        }
        return true
    }

    private companion object {
        const val MIN_START_DISTANCE = 8.0
        const val START_DISTANCE_FACTOR = 1.2
        const val START_HEIGHT_FACTOR = 0.6
        const val MIN_CLIP_W = 0.05f
        const val PICK_RADIUS = 7.0
        const val DRAG_START_DISTANCE = 4.0
        const val LOOK_SPEED = 0.4f
        const val ORBIT_SPEED = 0.4f
        const val SPEED_STEP = 1.25
        const val MAX_LINE_STEPS = 4000
        const val LINE_DOT_SPACING = 3
        const val BUTTON_RADIUS = 3
        const val CONTACT_RADIUS = 4

        const val BUTTON_COLOR = 0xFFFF2020.toInt()
        const val BINDING_COLOR = 0xFFFFD020.toInt()
        const val CONTACT_COLOR = 0xFF2F7BFF.toInt()
        const val OUTLINE_COLOR = 0xFF101018.toInt()
        const val WIRE_COLOR = 0xC0FFFFFF.toInt()
        const val DRAG_COLOR = 0xFFFF6060.toInt()
        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val HELP_COLOR = 0xFFD0D0D0.toInt()
        const val BUTTON_TEXT_COLOR = 0xFFFFC0C0.toInt()
        const val CONTACT_TEXT_COLOR = 0xFFB0D0FF.toInt()

        val MOVE_HELP_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.help_move")
        val LOOK_HELP_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.help_look")
        val TIP_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.tip")
        val BINDING_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.binding")
        val NONE_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.none")
        val NO_KEY_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.no_key")
        val HELM_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.helm")
        val CUSHION_TEXT: Component = Component.translatable("gui.vs_eureka.wiring.cushion")
    }
}
