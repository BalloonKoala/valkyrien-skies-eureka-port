package org.valkyrienskies.eureka.assembly

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.RenderShape
import org.joml.Quaternionf
import org.joml.Vector3d
import org.valkyrienskies.eureka.gui.assembly.ShipAssemblyScreen
import kotlin.math.max
import kotlin.math.sqrt

object AssemblyPreviewClient {
    @JvmStatic
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(AssemblyPreviewPayload.TYPE) { payload, context ->
            context.client().execute {
                val minecraft = context.client()
                minecraft.player?.closeContainer()
                minecraft.gui.setScreen(ShipAssemblyScreen(payload, PreviewModel.build(minecraft, payload)))
            }
        }
        PictureInPictureRendererRegistry.register { AssemblyPreviewRenderer() }
    }
}

/** One block of the preview, with the faces that touch another solid block of the ship left out. */
class PreviewBlock(val x: Int, val y: Int, val z: Int, val parts: List<BlockStateModelPart>, val tints: IntArray)

/** The blocks of an assembly preview, ready to draw. Built once when the preview opens. */
class PreviewModel(val blocks: List<PreviewBlock>, val center: Vector3d, val radius: Double, val sizeX: Int, val sizeY: Int, val sizeZ: Int) {
    companion object {
        private const val LIGHT_FULL_BRIGHT = 15728880

        fun build(minecraft: Minecraft, payload: AssemblyPreviewPayload): PreviewModel {
            val level = minecraft.level
            val positions = HashSet<Long>(payload.blocks.size * 2)
            payload.blocks.forEach { positions.add(it) }
            if (level == null || payload.blocks.isEmpty()) return PreviewModel(emptyList(), Vector3d(), 1.0, 0, 0, 0)

            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
            for (packed in payload.blocks) {
                val x = BlockPos.getX(packed); val y = BlockPos.getY(packed); val z = BlockPos.getZ(packed)
                minX = minOf(minX, x); minY = minOf(minY, y); minZ = minOf(minZ, z)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y); maxZ = maxOf(maxZ, z)
            }
            val center = Vector3d((minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0)
            val radius = max(sqrt(
                (maxX - minX + 1.0) * (maxX - minX + 1.0) + (maxY - minY + 1.0) * (maxY - minY + 1.0) + (maxZ - minZ + 1.0) * (maxZ - minZ + 1.0)
            ) / 2.0, 1.0)

            val models = minecraft.modelManager.blockStateModelSet
            val random = RandomSource.create()
            val pos = BlockPos.MutableBlockPos()
            val blocks = ArrayList<PreviewBlock>()
            for (packed in payload.blocks) {
                try {
                    addBlock(minecraft, level, models, random, pos.set(packed), packed, positions, blocks)
                } catch (e: RuntimeException) {
                    // Some modded block that can't be drawn this way, the rest of the preview still shows
                }
            }
            return PreviewModel(blocks, center, radius, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1)
        }

        private fun addBlock(
            minecraft: Minecraft,
            level: net.minecraft.client.multiplayer.ClientLevel,
            models: net.minecraft.client.renderer.block.BlockStateModelSet,
            random: RandomSource,
            pos: BlockPos,
            packed: Long,
            positions: Set<Long>,
            blocks: MutableList<PreviewBlock>
        ) {
            run {
                val state = level.getBlockState(pos)
                if (state.isAir || state.renderShape != RenderShape.MODEL) return
                val hidden = HashSet<Direction>()
                for (direction in Direction.entries) {
                    val next = BlockPos.offset(packed, direction)
                    if (next in positions && level.getBlockState(BlockPos.of(next)).isSolidRender) hidden.add(direction)
                }
                if (hidden.size == Direction.entries.size) return
                val parts = ArrayList<BlockStateModelPart>()
                random.setSeed(state.getSeed(pos))
                models.get(state).collectParts(random, parts)
                // Grass, leaves and water get their colour from where they are
                val sources = minecraft.blockColors.getTintSources(state)
                val tints = IntArray(sources.size) { sources[it].colorInWorld(state, level, pos) }
                blocks.add(PreviewBlock(
                    pos.x, pos.y, pos.z, if (hidden.isEmpty()) parts else parts.map { CulledPart(it, hidden) }, tints
                ))
            }
        }

        fun lightCoords() = LIGHT_FULL_BRIGHT
    }
}

private class CulledPart(private val part: BlockStateModelPart, private val hidden: Set<Direction>) : BlockStateModelPart {
    override fun getQuads(direction: Direction?): List<BakedQuad> =
        if (direction != null && direction in hidden) emptyList() else part.getQuads(direction)

    override fun useAmbientOcclusion() = part.useAmbientOcclusion()
    override fun particleMaterial() = part.particleMaterial()
    override fun materialFlags() = part.materialFlags()
}

/** What the preview in the screen looks like this frame. [scale] is GUI pixels per block. */
class AssemblyPreviewRenderState(
    val model: PreviewModel,
    val yaw: Float,
    val pitch: Float,
    private val pixelsPerBlock: Float,
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int
) : PictureInPictureRenderState {
    override fun x0() = left
    override fun y0() = top
    override fun x1() = right
    override fun y1() = bottom
    override fun scale() = pixelsPerBlock
    override fun scissorArea(): ScreenRectangle? = null
    override fun bounds(): ScreenRectangle? = PictureInPictureRenderState.getBounds(left, top, right, bottom, null)
}

/** Draws only the blocks that would become the ship, so anything it would grab by mistake stands out. */
class AssemblyPreviewRenderer : PictureInPictureRenderer<AssemblyPreviewRenderState>() {
    // Drawing thousands of blocks every frame isn't needed, only when the view changes
    private var drawnModel: PreviewModel? = null
    private var drawnYaw = Float.NaN
    private var drawnPitch = Float.NaN
    private var drawnScale = Float.NaN

    override fun getRenderStateClass() = AssemblyPreviewRenderState::class.java

    override fun textureIsReadyToBlit(renderState: AssemblyPreviewRenderState): Boolean =
        renderState.model === drawnModel && renderState.yaw == drawnYaw && renderState.pitch == drawnPitch &&
            renderState.scale() == drawnScale

    override fun getTranslateY(height: Int, guiScale: Int): Float = height / 2.0f

    override fun renderToTexture(renderState: AssemblyPreviewRenderState, poseStack: PoseStack, collector: SubmitNodeCollector) {
        drawnModel = renderState.model
        drawnYaw = renderState.yaw
        drawnPitch = renderState.pitch
        drawnScale = renderState.scale()

        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D)
        val model = renderState.model
        // The picture's y axis points down, the same turn vanilla gives entities in screens
        poseStack.rotate(Quaternionf().rotationZ(Math.PI.toFloat()))
        poseStack.rotate(Quaternionf().rotationX(Math.toRadians(-renderState.pitch.toDouble()).toFloat()))
        poseStack.rotate(Quaternionf().rotationY(Math.toRadians(renderState.yaw.toDouble()).toFloat()))
        val light = PreviewModel.lightCoords()
        for (block in model.blocks) {
            poseStack.pushPose()
            poseStack.translate(block.x - model.center.x, block.y - model.center.y, block.z - model.center.z)
            val translucent = block.parts.any { it.materialFlags() and BakedQuad.FLAG_TRANSLUCENT != 0 }
            val type = if (translucent) RenderTypes.translucentMovingBlock() else RenderTypes.cutoutMovingBlock()
            collector.submitBlockModel(poseStack, type, block.parts, block.tints, light, OverlayTexture.NO_OVERLAY, 0)
            poseStack.popPose()
        }
    }

    override fun getTextureLabel() = "ship assembly preview"
}
