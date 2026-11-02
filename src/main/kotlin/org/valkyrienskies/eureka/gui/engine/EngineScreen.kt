package org.valkyrienskies.eureka.gui.engine

import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import org.valkyrienskies.eureka.EurekaMod

class EngineScreen(handler: EngineScreenMenu, playerInventory: Inventory, text: Component) :
    AbstractContainerScreen<EngineScreenMenu>(handler, playerInventory, text) {

    private lateinit var pauseButton: Button

    override fun init() {
        super.init()

        pauseButton = addRenderableWidget(
            Button.builder(PAUSE_TEXT) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, EngineScreenMenu.PAUSE_BUTTON)
            }
                .bounds(leftPos + PAUSE_BUTTON_X, topPos + PAUSE_BUTTON_Y, PAUSE_BUTTON_WIDTH, PAUSE_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(PAUSE_TOOLTIP))
                .build()
        )
        updatePauseButton()
    }

    override fun containerTick() {
        super.containerTick()
        updatePauseButton()
    }

    private fun updatePauseButton() {
        // An engine that isn't on a ship has nothing to pause
        pauseButton.visible = menu.hasShipControl != 0
        pauseButton.message = if (menu.enginesPaused != 0) RESUME_TEXT else PAUSE_TEXT
    }

    // The texture is 512 so every coord is 2 pixels big
    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTicks)
        val xP = (width - imageWidth) / 2
        val yP = (height - imageHeight) / 2

        menu as EngineScreenMenu

        guiGraphics.pose().pushMatrix()
        // This matrix magic is bcs the texture is 512x512 and is 256x256 mc classic (mojank)
        guiGraphics.pose().translate(xP.toFloat(), yP.toFloat())
        guiGraphics.pose().scale(2f, 2f)

        // Draw the container background
        val (containerX, containerY) = if (menu.heatLevel > 1)
            Pair(HEATED_CONTAINER_X, HEATED_CONTAINER_Y)
        else
            Pair(CONTAINER_X, CONTAINER_Y)

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, FIRE_HOLE_X, FIRE_HOLE_Y, (containerX).toFloat(), (containerY).toFloat(), FIRE_HOLE_WIDTH, FIRE_HOLE_HEIGHT, 256, 256)

        // region COALS
        // Draw the coal
        if (menu.fuelLeft != 0) {
            val t = COAL_MULTI_MAX - ((menu.fuelLeft.toFloat() / menu.fuelTotal.toFloat()) * COAL_MULTI_MAX)
            fun coal(xC: Int, yC: Int, heightC: Int, mult: Float) {
                val drop = (t * mult).toInt()
                val calcY = FIRE_HOLE_HEIGHT - heightC + drop
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, FIRE_HOLE_X, FIRE_HOLE_Y + calcY, (xC).toFloat(), (yC).toFloat(), COAL_WIDTH, heightC, 256, 256)
            }

            coal(COAL_4_X, COAL_4_Y, COAL_4_HEIGHT, COAL_4_MULT)
            coal(COAL_3_X, COAL_3_Y, COAL_3_HEIGHT, COAL_3_MULT)
            coal(COAL_2_X, COAL_2_Y, COAL_2_HEIGHT, COAL_2_MULT)
            coal(COAL_1_X, COAL_1_Y, COAL_1_HEIGHT, COAL_1_MULT)
        }
        // endregion

        // Draw the glass background
        val (glassX, glassY) = if (menu.heatLevel > 3)
            Pair(HEATED_GLASS_X, HEATED_GLASS_Y)
        else
            Pair(GLASS_X, GLASS_Y)

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, FIRE_HOLE_X, FIRE_HOLE_Y, (glassX).toFloat(), (glassY).toFloat(), FIRE_HOLE_WIDTH, FIRE_HOLE_HEIGHT, 256, 256)

        // Draw the inventory
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, (0).toFloat(), (0).toFloat(), imageWidth / 2, imageHeight / 2, 256, 256)
        guiGraphics.pose().popMatrix()
    }

    override fun extractLabels(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        // super.renderLabels(poseStack, mouseX, mouseY)
    }

    companion object { // TEXTURE DATA
        internal val TEXTURE = Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "textures/gui/engine.png")

        private val PAUSE_TEXT = Component.translatable("gui.vs_eureka.engine.pause")
        private val RESUME_TEXT = Component.translatable("gui.vs_eureka.engine.resume")
        private val PAUSE_TOOLTIP = Component.translatable("gui.vs_eureka.engine.pause.tooltip")

        private const val PAUSE_BUTTON_X = 104
        private const val PAUSE_BUTTON_Y = 56
        private const val PAUSE_BUTTON_WIDTH = 62
        private const val PAUSE_BUTTON_HEIGHT = 16

        private const val FIRE_HOLE_X = 10 / 2
        private const val FIRE_HOLE_Y = 8 / 2

        private const val FIRE_HOLE_WIDTH = 156 / 2
        private const val FIRE_HOLE_HEIGHT = 68 / 2

        private const val HEATED_GLASS_X = 10 / 2
        private const val HEATED_GLASS_Y = 172 / 2
        private const val GLASS_X = 10 / 2
        private const val GLASS_Y = 244 / 2

        private const val HEATED_CONTAINER_X = 10 / 2
        private const val HEATED_CONTAINER_Y = 390 / 2
        private const val CONTAINER_X = 10 / 2
        private const val CONTAINER_Y = 318 / 2

        // TODO fill in actual pixel coords
        private const val COAL_4_X = 184 / 2
        private const val COAL_4_Y = 18 / 2
        private const val COAL_3_X = 184 / 2
        private const val COAL_3_Y = 80 / 2
        private const val COAL_2_X = 184 / 2
        private const val COAL_2_Y = 128 / 2
        private const val COAL_1_X = 184 / 2
        private const val COAL_1_Y = 166 / 2
        private const val COAL_WIDTH = 158 / 2
        private const val COAL_4_HEIGHT = 60 / 2
        private const val COAL_3_HEIGHT = 44 / 2
        private const val COAL_2_HEIGHT = 34 / 2
        private const val COAL_1_HEIGHT = 26 / 2

        private const val COAL_MULTI_MAX = COAL_1_HEIGHT.toFloat()
        private const val COAL_4_MULT = COAL_4_HEIGHT.toFloat() / COAL_MULTI_MAX
        private const val COAL_3_MULT = COAL_3_HEIGHT.toFloat() / COAL_MULTI_MAX
        private const val COAL_2_MULT = COAL_2_HEIGHT.toFloat() / COAL_MULTI_MAX
        private const val COAL_1_MULT = 1f
    }
}
