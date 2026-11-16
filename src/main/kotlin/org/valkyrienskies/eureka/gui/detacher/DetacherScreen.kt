package org.valkyrienskies.eureka.gui.detacher

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.valkyrienskies.eureka.blockentity.DetacherBlockEntity.Companion.MAX_PUSH_HALF_BLOCKS
import org.valkyrienskies.eureka.blockentity.DetacherBlockEntity.Companion.MIN_PUSH_HALF_BLOCKS
import kotlin.math.roundToInt

class DetacherScreen(handler: DetacherMenu, playerInventory: Inventory, text: Component) :
    AbstractContainerScreen<DetacherMenu>(handler, playerInventory, text, WIDTH, HEIGHT) {

    private lateinit var slider: PushSlider

    override fun init() {
        super.init()
        slider = addRenderableWidget(PushSlider(leftPos + SLIDER_X, topPos + SLIDER_Y, imageWidth - 2 * SLIDER_X, SLIDER_HEIGHT))
    }

    // The setting comes from the server just after the screen opens, and again when someone else changes it
    override fun containerTick() {
        super.containerTick()
        if (!isDragging) slider.show(menu.pushHalfBlocks)
    }

    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTicks)
        guiGraphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, BORDER_COLOR)
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR)
        // The red face, like on the block
        val cx = leftPos + imageWidth / 2
        val cy = topPos + RED_FACE_Y
        guiGraphics.fill(cx - 5, cy - 3, cx + 5, cy + 3, RED_COLOR)
        guiGraphics.fill(cx - 3, cy - 5, cx + 3, cy + 5, RED_COLOR)
        guiGraphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, RED_COLOR)
    }

    override fun extractLabels(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        guiGraphics.centeredText(font, title, imageWidth / 2, TITLE_Y, TITLE_COLOR)
        guiGraphics.centeredText(font, HELP_TEXT, imageWidth / 2, HELP_Y, HELP_COLOR)
        guiGraphics.centeredText(font, HELP_TEXT_2, imageWidth / 2, HELP_Y + 10, HELP_COLOR)
    }

    private inner class PushSlider(x: Int, y: Int, width: Int, height: Int) :
        AbstractSliderButton(x, y, width, height, Component.empty(), toValue(menu.pushHalfBlocks)) {

        private var sent = menu.pushHalfBlocks

        init {
            updateMessage()
        }

        fun halfBlocks(): Int = MIN_PUSH_HALF_BLOCKS + (value * (MAX_PUSH_HALF_BLOCKS - MIN_PUSH_HALF_BLOCKS)).roundToInt()

        fun show(halfBlocks: Int) {
            if (halfBlocks == sent || halfBlocks < MIN_PUSH_HALF_BLOCKS) return
            sent = halfBlocks
            value = toValue(halfBlocks)
            updateMessage()
        }

        override fun updateMessage() {
            message = Component.translatable("gui.vs_eureka.detacher.push", "%.1f".format(halfBlocks() / 2.0))
        }

        override fun applyValue() {
            val halfBlocks = halfBlocks()
            if (halfBlocks == sent) return
            sent = halfBlocks
            minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, halfBlocks)
        }
    }

    companion object {
        private const val WIDTH = 200
        private const val HEIGHT = 100
        private const val TITLE_Y = 8
        private const val RED_FACE_Y = 30
        private const val HELP_Y = 42
        private const val SLIDER_X = 12
        private const val SLIDER_Y = 70
        private const val SLIDER_HEIGHT = 20
        private const val PANEL_COLOR = 0xF0262A30.toInt()
        private const val BORDER_COLOR = 0xFF0E1013.toInt()
        private const val RED_COLOR = 0xFFD42020.toInt()
        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val HELP_COLOR = 0xFFB8BCC4.toInt()
        private val HELP_TEXT: Component = Component.translatable("gui.vs_eureka.detacher.help")
        private val HELP_TEXT_2: Component = Component.translatable("gui.vs_eureka.detacher.help2")

        private fun toValue(halfBlocks: Int): Double =
            ((halfBlocks.coerceIn(MIN_PUSH_HALF_BLOCKS, MAX_PUSH_HALF_BLOCKS) - MIN_PUSH_HALF_BLOCKS).toDouble() /
                (MAX_PUSH_HALF_BLOCKS - MIN_PUSH_HALF_BLOCKS))
    }
}
