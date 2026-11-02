package org.valkyrienskies.eureka

import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.FireBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction
import org.valkyrienskies.eureka.block.AnchorBlock
import org.valkyrienskies.eureka.block.BallastBlock
import org.valkyrienskies.eureka.block.BalloonBlock
import org.valkyrienskies.eureka.block.CannonBlock
import org.valkyrienskies.eureka.block.EngineBlock
import org.valkyrienskies.eureka.block.FloaterBlock
import org.valkyrienskies.eureka.block.ShipHelmBlock
import org.valkyrienskies.eureka.block.WirelessButtonBlock
import org.valkyrienskies.eureka.block.WoodType
import org.valkyrienskies.eureka.registry.DeferredRegister
import org.valkyrienskies.mod.common.hooks.VSGameEvents

@Suppress("unused")
object EurekaBlocks {
    internal val BLOCKS = DeferredRegister.create(EurekaMod.MOD_ID, Registries.BLOCK)

    val ANCHOR = BLOCKS.register("anchor", ::AnchorBlock)
    val CANNON = BLOCKS.register("cannon") {
        CannonBlock(
            // Not a conductor, or the pulse of one cannon's button would carry over to the cannon next to it
            eurekaBlockProperties("cannon").mapColor(MapColor.STONE).strength(3.5F).requiresCorrectToolForDrops()
                .sound(SoundType.STONE).isRedstoneConductor { _, _, _ -> false }
        )
    }
    val ENGINE = BLOCKS.register("engine", ::EngineBlock)
    val FLOATER = BLOCKS.register("floater", ::FloaterBlock)
    val BALLAST = BLOCKS.register("ballast", ::BallastBlock)

    val WIRELESS_BUTTONS = listOf(
        "oak" to BlockSetType.OAK,
        "spruce" to BlockSetType.SPRUCE,
        "birch" to BlockSetType.BIRCH,
        "jungle" to BlockSetType.JUNGLE,
        "acacia" to BlockSetType.ACACIA,
        "dark_oak" to BlockSetType.DARK_OAK,
        "mangrove" to BlockSetType.MANGROVE,
        "cherry" to BlockSetType.CHERRY,
        "pale_oak" to BlockSetType.PALE_OAK,
        "bamboo" to BlockSetType.BAMBOO,
        "crimson" to BlockSetType.CRIMSON,
        "warped" to BlockSetType.WARPED
    ).map { (wood, type) ->
        val name = "${wood}_wireless_button"
        BLOCKS.register(name) {
            WirelessButtonBlock(
                type,
                eurekaBlockProperties(name).noCollision().strength(0.5F).pushReaction(PushReaction.POPPED)
                    .lightLevel { state -> if (state.getValue(ButtonBlock.POWERED)) 5 else 0 }
            )
        }
    }

    // region Ship Helms
    val OAK_SHIP_HELM = BLOCKS.register("oak_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("oak_ship_helm").mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD),
            WoodType.OAK
        )
    }
    val SPRUCE_SHIP_HELM = BLOCKS.register("spruce_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("spruce_ship_helm").mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD),
            WoodType.SPRUCE
        )
    }
    val BIRCH_SHIP_HELM = BLOCKS.register("birch_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("birch_ship_helm").mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD),
            WoodType.BIRCH
        )
    }
    val JUNGLE_SHIP_HELM = BLOCKS.register("jungle_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("jungle_ship_helm").mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD),
            WoodType.JUNGLE
        )
    }
    val ACACIA_SHIP_HELM = BLOCKS.register("acacia_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("acacia_ship_helm").mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD),
            WoodType.ACACIA
        )
    }
    val DARK_OAK_SHIP_HELM = BLOCKS.register("dark_oak_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("dark_oak_ship_helm").mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD),
            WoodType.DARK_OAK
        )
    }
    val CRIMSON_SHIP_HELM = BLOCKS.register("crimson_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("crimson_ship_helm").mapColor(MapColor.NETHER).strength(2.5F).sound(SoundType.STEM),
            WoodType.CRIMSON
        )
    }
    val WARPED_SHIP_HELM = BLOCKS.register("warped_ship_helm") {
        ShipHelmBlock(
            eurekaBlockProperties("warped_ship_helm").mapColor(MapColor.NETHER).strength(2.5F).sound(SoundType.STEM),
            WoodType.WARPED
        )
    }
    // endregion

    // region Balloons
    val BALLOON = BLOCKS.register("balloon") {
        BalloonBlock(
            eurekaBlockProperties("balloon").mapColor(MapColor.WOOL).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val WHITE_BALLOON = BLOCKS.register("white_balloon") {
        BalloonBlock(
            eurekaBlockProperties("white_balloon").mapColor(MapColor.SNOW).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val LIGHT_GRAY_BALLOON = BLOCKS.register("light_gray_balloon") {
        BalloonBlock(
            eurekaBlockProperties("light_gray_balloon").mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val GRAY_BALLOON = BLOCKS.register("gray_balloon") {
        BalloonBlock(
            eurekaBlockProperties("gray_balloon").mapColor(MapColor.COLOR_GRAY).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val BLACK_BALLOON = BLOCKS.register("black_balloon") {
        BalloonBlock(
            eurekaBlockProperties("black_balloon").mapColor(MapColor.COLOR_BLACK).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val RED_BALLOON = BLOCKS.register("red_balloon") {
        BalloonBlock(
            eurekaBlockProperties("red_balloon").mapColor(MapColor.COLOR_RED).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val ORANGE_BALLOON = BLOCKS.register("orange_balloon") {
        BalloonBlock(
            eurekaBlockProperties("orange_balloon").mapColor(MapColor.COLOR_ORANGE).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val YELLOW_BALLOON = BLOCKS.register("yellow_balloon") {
        BalloonBlock(
            eurekaBlockProperties("yellow_balloon").mapColor(MapColor.COLOR_YELLOW).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val LIME_BALLOON = BLOCKS.register("lime_balloon") {
        BalloonBlock(
            eurekaBlockProperties("lime_balloon").mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val GREEN_BALLOON = BLOCKS.register("green_balloon") {
        BalloonBlock(
            eurekaBlockProperties("green_balloon").mapColor(MapColor.COLOR_GREEN).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val LIGHT_BLUE_BALLOON = BLOCKS.register("light_blue_balloon") {
        BalloonBlock(
            eurekaBlockProperties("light_blue_balloon").mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val CYAN_BALLOON = BLOCKS.register("cyan_balloon") {
        BalloonBlock(
            eurekaBlockProperties("cyan_balloon").mapColor(MapColor.COLOR_CYAN).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val BLUE_BALLOON = BLOCKS.register("blue_balloon") {
        BalloonBlock(
            eurekaBlockProperties("blue_balloon").mapColor(MapColor.COLOR_BLUE).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val PURPLE_BALLOON = BLOCKS.register("purple_balloon") {
        BalloonBlock(
            eurekaBlockProperties("purple_balloon").mapColor(MapColor.COLOR_PURPLE).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val MAGENTA_BALLOON = BLOCKS.register("magenta_balloon") {
        BalloonBlock(
            eurekaBlockProperties("magenta_balloon").mapColor(MapColor.COLOR_MAGENTA).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val PINK_BALLOON = BLOCKS.register("pink_balloon") {
        BalloonBlock(
            eurekaBlockProperties("pink_balloon").mapColor(MapColor.COLOR_PINK).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    val BROWN_BALLOON = BLOCKS.register("brown_balloon") {
        BalloonBlock(
            eurekaBlockProperties("brown_balloon").mapColor(MapColor.COLOR_BROWN).strength(0.8F).sound(SoundType.WOOL)
        )
    }
    // endregion

    fun register() {
        BLOCKS.applyAll()

        VSGameEvents.registriesCompleted.on { _, _ ->
            makeFlammables()
        }
    }

    // region Flammables
    // TODO make this part of the registration sequence
    fun flammableBlock(block: Block, flameOdds: Int, burnOdds: Int) {
        val fire = Blocks.FIRE as FireBlock
        fire.setFlammable(block, flameOdds, burnOdds)
    }

    fun makeFlammables() {
        flammableBlock(OAK_SHIP_HELM.get(), 5, 20)
        flammableBlock(SPRUCE_SHIP_HELM.get(), 5, 20)
        flammableBlock(BIRCH_SHIP_HELM.get(), 5, 20)
        flammableBlock(JUNGLE_SHIP_HELM.get(), 5, 20)
        flammableBlock(ACACIA_SHIP_HELM.get(), 5, 20)
        flammableBlock(DARK_OAK_SHIP_HELM.get(), 5, 20)
        flammableBlock(BALLOON.get(), 30, 60)
        flammableBlock(WHITE_BALLOON.get(), 30, 60)
        flammableBlock(LIGHT_GRAY_BALLOON.get(), 30, 60)
        flammableBlock(GRAY_BALLOON.get(), 30, 60)
        flammableBlock(BLACK_BALLOON.get(), 30, 60)
        flammableBlock(RED_BALLOON.get(), 30, 60)
        flammableBlock(ORANGE_BALLOON.get(), 30, 60)
        flammableBlock(YELLOW_BALLOON.get(), 30, 60)
        flammableBlock(LIME_BALLOON.get(), 30, 60)
        flammableBlock(GREEN_BALLOON.get(), 30, 60)
        flammableBlock(LIGHT_BLUE_BALLOON.get(), 30, 60)
        flammableBlock(CYAN_BALLOON.get(), 30, 60)
        flammableBlock(BLUE_BALLOON.get(), 30, 60)
        flammableBlock(PURPLE_BALLOON.get(), 30, 60)
        flammableBlock(MAGENTA_BALLOON.get(), 30, 60)
        flammableBlock(PINK_BALLOON.get(), 30, 60)
        flammableBlock(BROWN_BALLOON.get(), 30, 60)
        flammableBlock(FLOATER.get(), 5, 20)
    }
    // endregion

    // Blocks should also be registered as items, if you want them to be able to be held
    // aka all blocks
    fun registerItems(items: DeferredRegister<Item>) {
        BLOCKS.forEach {
            items.register(it.name) { BlockItem(it.get(), eurekaItemProperties(it.name).useBlockDescriptionPrefix()) }
        }
    }
}

internal fun eurekaBlockProperties(name: String): BlockBehaviour.Properties =
    BlockBehaviour.Properties.of()
        .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, name)))

internal fun eurekaItemProperties(name: String): Item.Properties =
    Item.Properties()
        .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, name)))
