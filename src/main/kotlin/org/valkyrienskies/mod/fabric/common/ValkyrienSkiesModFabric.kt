package org.valkyrienskies.mod.fabric.common

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.PackType.SERVER_DATA
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.client.VSPhysicsEntityModel
import org.valkyrienskies.mod.client.VSPhysicsEntityRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_CREATOR_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_REMOVER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_ANTIGRAV
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_CHAIR
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_FLAP
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_HINGE
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_THRUSTER
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_WING
import org.valkyrienskies.mod.common.block.TestAntigravBlock
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.block.TestFlapBlock
import org.valkyrienskies.mod.common.block.TestHingeBlock
import org.valkyrienskies.mod.common.block.TestThrusterBlock
import org.valkyrienskies.mod.common.block.TestWingBlock
import org.valkyrienskies.mod.common.blockentity.TestAntigravBlockEntity
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.blockentity.TestThrusterBlockEntity
import org.valkyrienskies.mod.common.command.VSCommands
import org.valkyrienskies.mod.common.config.DimensionParametersResolver
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSConfigUpdater
import org.valkyrienskies.mod.common.config.VSEntityHandlerDataLoader
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.item.AreaAssemblerItem
import org.valkyrienskies.mod.common.item.ConnectionCheckerItem
import org.valkyrienskies.mod.common.item.PhysicsEntityCreatorItem
import org.valkyrienskies.mod.common.item.ShipAssemblerItem
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import org.valkyrienskies.mod.common.item.ShipRemoverItem
import org.valkyrienskies.mod.util.ClientConnectivityUpdateQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ValkyrienSkiesModFabric : ModInitializer {

    companion object {
        private val hasInitialized = AtomicBoolean(false)
    }

    override fun onInitialize() {
        if (hasInitialized.getAndSet(true)) return

        ConfigRegistry.INSTANCE.apply {
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.SERVER, VSConfigUpdater.CORE_SERVER_SPEC, "valkyrienskies/vs-core-server.toml")
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.SERVER, VSConfigUpdater.SERVER_SPEC, "valkyrienskies/valkyrienskies-server.toml")
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.COMMON, VSConfigUpdater.COMMON_SPEC, "valkyrienskies/valkyrienskies-common.toml")
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.CLIENT, VSConfigUpdater.CLIENT_SPEC, "valkyrienskies/valkyrienskies-client.toml")
        }

        val applyConfig = fun(config: ModConfig) {
            val spec = config.spec as? ModConfigSpec ?: return
            val loaded = config.loadedConfig?.config() ?: return
            VSConfigUpdater.applyFromConfigLoad(spec) { key -> loaded.get<Any?>(key) }
        }
        ModConfigEvents.loading(ValkyrienSkiesMod.MOD_ID).register { applyConfig(it) }
        ModConfigEvents.reloading(ValkyrienSkiesMod.MOD_ID).register { applyConfig(it) }

        ValkyrienSkiesMod.TEST_CHAIR = TestChairBlock()
        ValkyrienSkiesMod.TEST_HINGE = TestHingeBlock
        ValkyrienSkiesMod.TEST_FLAP = TestFlapBlock()
        ValkyrienSkiesMod.TEST_WING = TestWingBlock()
        ValkyrienSkiesMod.TEST_THRUSTER = TestThrusterBlock
        ValkyrienSkiesMod.TEST_ANTIGRAV = TestAntigravBlock
        ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM = ConnectionCheckerItem(
            itemProperties("connection_checker"),
            { 1.0 },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = ShipCreatorItem(
            itemProperties("ship_creator"),
            { 1.0 },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.SHIP_REMOVER_ITEM = ShipRemoverItem(
            itemProperties("ship_remover")
        )
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = ShipAssemblerItem(itemProperties("ship_assembler"))
        ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM = AreaAssemblerItem(
            itemProperties("area_assembler"),
            { 1.0 },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = ShipCreatorItem(
            itemProperties("ship_creator_smaller"),
            { VSGameConfig.SERVER.miniShipSize },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM = PhysicsEntityCreatorItem(itemProperties("physics_entity_creator"))

        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = EntityType.Builder.of(
            ::ShipMountingEntity,
            MobCategory.MISC
        ).sized(.3f, .3f)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id("ship_mounting_entity")))

        ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE = EntityType.Builder.of(
            ::VSPhysicsEntity,
            MobCategory.MISC
        ).sized(1f, 1f)
            .updateInterval(1)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id("vs_physics_entity")))

        ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE =
            FabricBlockEntityTypeBuilder.create(::TestHingeBlockEntity, ValkyrienSkiesMod.TEST_HINGE).build()

        ValkyrienSkiesMod.TEST_THRUSTER_BLOCK_ENTITY_TYPE =
            FabricBlockEntityTypeBuilder.create(::TestThrusterBlockEntity, ValkyrienSkiesMod.TEST_THRUSTER).build()

        ValkyrienSkiesMod.TEST_ANTIGRAV_BLOCK_ENTITY_TYPE =
            FabricBlockEntityTypeBuilder.create(::TestAntigravBlockEntity, ValkyrienSkiesMod.TEST_ANTIGRAV).build()

        val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

        ValkyrienSkiesMod.init()

        if (isClient) onInitializeClient()

        registerBlockAndItem("test_chair", ValkyrienSkiesMod.TEST_CHAIR)
        registerBlockAndItem("test_hinge", ValkyrienSkiesMod.TEST_HINGE)
        registerBlockAndItem("test_flap", ValkyrienSkiesMod.TEST_FLAP)
        registerBlockAndItem("test_wing", ValkyrienSkiesMod.TEST_WING)
        registerBlockAndItem("test_thruster", ValkyrienSkiesMod.TEST_THRUSTER)
        registerBlockAndItem("test_antigrav", ValkyrienSkiesMod.TEST_ANTIGRAV)
        Registry.register(
            BuiltInRegistries.ITEM, id("connection_checker"),
            ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, id("area_assembler"),
            ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, id("ship_assembler"),
            ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, id("ship_creator"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, id("ship_creator_smaller"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER
        )
        Registry.register(
            BuiltInRegistries.ITEM, id("ship_remover"),
            ValkyrienSkiesMod.SHIP_REMOVER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, id("physics_entity_creator"),
            ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM
        )
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("ship_mounting_entity"),
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("vs_physics_entity"),
            ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE
        )
        FabricDefaultAttributeRegistry.register(ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE, VSPhysicsEntity.Companion.createAttributes())



        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("test_hinge_block_entity"),
            ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("test_thruster_block_entity"),
            ValkyrienSkiesMod.TEST_THRUSTER_BLOCK_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("test_antigrav_block_entity"),
            ValkyrienSkiesMod.TEST_ANTIGRAV_BLOCK_ENTITY_TYPE
        )

        // Registry.register(
        //     BuiltInRegistries.CREATIVE_MODE_TAB,
        //     ValkyrienSkiesMod.VS_CREATIVE_TAB,
        //     ValkyrienSkiesMod.createCreativeTab()
        // )

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.OP_BLOCKS).register { event ->
            event.accept(TEST_CHAIR.asItem())
            event.accept(TEST_HINGE.asItem())
            event.accept(TEST_FLAP.asItem())
            event.accept(TEST_WING.asItem())
            event.accept(TEST_THRUSTER.asItem())
            event.accept(TEST_ANTIGRAV.asItem())
            event.accept(CONNECTION_CHECKER_ITEM)
            event.accept(SHIP_CREATOR_ITEM)
            event.accept(SHIP_REMOVER_ITEM)
            event.accept(SHIP_ASSEMBLER_ITEM)
            event.accept(SHIP_CREATOR_ITEM_SMALLER)
            event.accept(AREA_ASSEMBLER_ITEM)
            event.accept(PHYSICS_ENTITY_CREATOR_ITEM)
        }

        CommandRegistrationCallback.EVENT.register { dispatcher ,d, _ ->
            VSCommands.registerServerCommands(dispatcher)
        }

        // registering data loaders
        val loader1 = MassDatapackResolver.loader // the get makes a new instance so get it only once
        val loader2 = VSEntityHandlerDataLoader // the get makes a new instance so get it only once
        val loader3 = DimensionParametersResolver
        ResourceManagerHelper.get(SERVER_DATA)
            .registerReloadListener(object : IdentifiableResourceReloadListener {
                override fun getFabricId(): Identifier {
                    return id("vs_mass")
                }

                override fun reload(
                    sharedState: PreparableReloadListener.SharedState,
                    backgroundExecutor: Executor,
                    stage: PreparationBarrier,
                    gameExecutor: Executor
                ): CompletableFuture<Void> {
                    return loader1.reload(
                        sharedState, backgroundExecutor, stage, gameExecutor
                    ).thenAcceptBoth(
                        loader2.reload(
                            sharedState, backgroundExecutor, stage, gameExecutor
                        )
                    ) { _, _ -> }
                }
            })
        ResourceManagerHelper.get(SERVER_DATA)
            .registerReloadListener(object : IdentifiableResourceReloadListener {
                override fun getFabricId(): Identifier {
                    return id("vs_dimension_parameters")
                }

                override fun reload(
                    sharedState: PreparableReloadListener.SharedState,
                    backgroundExecutor: Executor,
                    stage: PreparationBarrier,
                    gameExecutor: Executor
                ): CompletableFuture<Void> {
                    return loader3.reload(
                        sharedState, backgroundExecutor, stage, gameExecutor
                    )
                }
            })
        CommonLifecycleEvents.TAGS_LOADED.register { _, _ ->
            VSGameEvents.tagsAreLoaded.emit(Unit)
        }

        if (System.getProperty("vs.selfTest") != null) {
            Class.forName("org.valkyrienskies.mod.dev.DevSelfTest").getMethod("install").invoke(null)
        }

    }

    /**
     * Only run on client
     */
    private fun onInitializeClient() {
        // Register the ship mounting entity renderer
        EntityRendererRegistry.register(
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        ) { context: Context ->
            EmptyRenderer(
                context
            )
        }

        EntityRendererRegistry.register(
            ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE
        ) { context: Context ->
            VSPhysicsEntityRenderer<VSPhysicsEntity>(
                context
            )
        }

        ModelLayerRegistry.registerModelLayer(
            VSPhysicsEntityModel.LAYER_LOCATION,
            VSPhysicsEntityModel.Companion::createBodyLayer
        )

        VSKeyBindings.clientSetup {
            KeyMappingHelper.registerKeyMapping(it)
        }

        VSGameEvents.registriesCompleted.on {
            ClientConnectivityUpdateQueue.onRegistriesCompleted()
        }

    }

    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, path)

    private fun itemProperties(name: String): Properties =
        Properties().setId(ResourceKey.create(Registries.ITEM, id(name)))

    private fun registerBlockAndItem(registryName: String, block: Block): Item {
        Registry.register(
            BuiltInRegistries.BLOCK, id(registryName),
            block
        )
        val item = BlockItem(block, itemProperties(registryName).useBlockDescriptionPrefix())
        Registry.register(BuiltInRegistries.ITEM, id(registryName), item)
        return item
    }
}
