package org.valkyrienskies.eureka.gui.assembly

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.valkyrienskies.eureka.assembly.AssemblyConfirmPayload
import org.valkyrienskies.eureka.assembly.AssemblyPreviewPayload
import org.valkyrienskies.eureka.assembly.AssemblyPreviewRenderState
import org.valkyrienskies.eureka.assembly.PreviewModel
import org.valkyrienskies.eureka.mixin.client.GuiGraphicsExtractorAccessor
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

/**
 * The Info screen. Shows how many blocks would become the ship and a 3D view of only those blocks, so a ship that
 * touches the ground or a dock shows it before anything happens. The Assemble button under it really assembles.
 */
class ShipAssemblyScreen(private val payload: AssemblyPreviewPayload, private val model: PreviewModel) :
    Screen(Component.translatable("gui.vs_eureka.assembly.title")) {

    private var yaw = START_YAW
    private var pitch = START_PITCH
    private var zoom = 1f
    private var draggingView = false

    private var viewLeft = 0
    private var viewTop = 0
    private var viewRight = 0
    private var viewBottom = 0
    private var infoTop = 0

    private lateinit var assembleButton: Button

    private val count get() = payload.blocks.size
    private val tooBig get() = !payload.complete

    override fun init() {
        val viewWidth = min(width - 2 * MARGIN, MAX_VIEW_WIDTH)
        val viewHeight = max(min(height - TITLE_SPACE - INFO_SPACE - BUTTON_SPACE, MAX_VIEW_HEIGHT), MIN_VIEW_HEIGHT)
        viewLeft = (width - viewWidth) / 2
        viewRight = viewLeft + viewWidth
        viewTop = TITLE_SPACE
        viewBottom = viewTop + viewHeight
        infoTop = viewBottom + 6

        val buttonsTop = infoTop + INFO_SPACE - 6
        val buttonWidth = (viewWidth - BUTTON_GAP) / 2
        assembleButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.vs_eureka.assembly.confirm")) { confirm() }
                .bounds(viewLeft, buttonsTop, buttonWidth, BUTTON_HEIGHT).build()
        )
        assembleButton.active = !tooBig && count > 0
        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) { onClose() }
                .bounds(viewLeft + buttonWidth + BUTTON_GAP, buttonsTop, buttonWidth, BUTTON_HEIGHT).build()
        )
    }

    private fun confirm() {
        ClientPlayNetworking.send(AssemblyConfirmPayload(payload.helm))
        onClose()
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks)
        graphics.centeredText(font, title, width / 2, 8, TEXT_COLOR)

        // The view itself
        graphics.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, BORDER_COLOR)
        graphics.fill(viewLeft, viewTop, viewRight, viewBottom, VIEW_COLOR)
        val fit = min(viewRight - viewLeft, viewBottom - viewTop) * FIT_FRACTION / model.radius.toFloat()
        (graphics as GuiGraphicsExtractorAccessor).`vs_eureka$getGuiRenderState`().addPicturesInPictureState(
            AssemblyPreviewRenderState(model, yaw, pitch, fit * zoom, viewLeft, viewTop, viewRight, viewBottom)
        )
        graphics.text(font, HELP_TEXT, viewLeft + 4, viewBottom - 11, HELP_COLOR, true)

        // The info under it
        val number = NumberFormat.getIntegerInstance().format(count)
        val blocksLine = if (tooBig) {
            Component.translatable("gui.vs_eureka.assembly.too_many", NumberFormat.getIntegerInstance().format(payload.limit))
        } else {
            Component.translatable("gui.vs_eureka.assembly.blocks", number)
        }
        graphics.text(font, blocksLine, viewLeft, infoTop, if (tooBig) WARNING_COLOR else TEXT_COLOR, true)
        graphics.text(
            font, Component.translatable("gui.vs_eureka.assembly.size", model.sizeX, model.sizeY, model.sizeZ),
            viewLeft, infoTop + LINE, TEXT_COLOR, true
        )
        val hint = if (tooBig) TOO_BIG_TEXT else CHECK_TEXT
        graphics.text(font, hint, viewLeft, infoTop + 2 * LINE, if (tooBig) WARNING_COLOR else HELP_COLOR, true)
    }

    private fun overView(x: Double, y: Double) = x >= viewLeft && x < viewRight && y >= viewTop && y < viewBottom

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overView(event.x(), event.y())) {
            draggingView = true
            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (draggingView) {
            yaw += (dx * DRAG_DEGREES_PER_PIXEL).toFloat()
            pitch = (pitch + (dy * DRAG_DEGREES_PER_PIXEL).toFloat()).coerceIn(-MAX_PITCH, MAX_PITCH)
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        draggingView = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        if (overView(x, y)) {
            zoom = (zoom * if (scrollY > 0) ZOOM_STEP else 1f / ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)
            return true
        }
        return super.mouseScrolled(x, y, scrollX, scrollY)
    }

    /** For tests: turns the view the way dragging does. */
    fun turn(deltaYaw: Float, deltaPitch: Float) {
        yaw += deltaYaw
        pitch = (pitch + deltaPitch).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun blockCount() = count
    fun shownBlocks() = model.blocks.size
    fun canAssemble() = assembleButton.active

    private companion object {
        const val MARGIN = 20
        const val TITLE_SPACE = 24
        const val INFO_SPACE = 44
        const val BUTTON_SPACE = 30
        const val BUTTON_HEIGHT = 20
        const val BUTTON_GAP = 8
        const val LINE = 11
        const val MAX_VIEW_WIDTH = 360
        const val MAX_VIEW_HEIGHT = 240
        const val MIN_VIEW_HEIGHT = 60
        const val FIT_FRACTION = 0.48f
        const val START_YAW = 35f
        const val START_PITCH = 25f
        const val MAX_PITCH = 89f
        const val DRAG_DEGREES_PER_PIXEL = 0.6
        const val ZOOM_STEP = 1.15f
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 12f
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val HELP_COLOR = 0xFFAAAAAA.toInt()
        const val WARNING_COLOR = 0xFFFF5555.toInt()
        const val BORDER_COLOR = 0xFF8B8B8B.toInt()
        const val VIEW_COLOR = 0xFF1B1D22.toInt()

        val HELP_TEXT: Component = Component.translatable("gui.vs_eureka.assembly.help")
        val CHECK_TEXT: Component = Component.translatable("gui.vs_eureka.assembly.check")
        val TOO_BIG_TEXT: Component = Component.translatable("gui.vs_eureka.assembly.too_big")
    }
}
