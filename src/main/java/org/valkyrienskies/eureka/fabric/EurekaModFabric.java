package org.valkyrienskies.eureka.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.config.ModConfig;
import org.valkyrienskies.eureka.EurekaBlockEntities;
import org.valkyrienskies.eureka.EurekaConfig;
import org.valkyrienskies.eureka.EurekaItems;
import org.valkyrienskies.eureka.EurekaEntities;
import org.valkyrienskies.eureka.EurekaMod;
import org.valkyrienskies.eureka.WelcomeMessage;
import org.valkyrienskies.eureka.block.IWoodType;
import org.valkyrienskies.eureka.block.WoodType;
import org.valkyrienskies.eureka.blockentity.renderer.ShipHelmBlockEntityRenderer;
import org.valkyrienskies.eureka.blockentity.renderer.WheelModels;
import org.valkyrienskies.eureka.fabric.registry.FuelRegistryImpl;
import org.valkyrienskies.eureka.registry.CreativeTabs;
import org.valkyrienskies.mod.fabric.common.ValkyrienSkiesModFabric;

public class EurekaModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // force VS2 to load before eureka
        new ValkyrienSkiesModFabric().onInitialize();

        new FuelRegistryImpl();

        ConfigRegistry.INSTANCE.register(EurekaMod.MOD_ID, ModConfig.Type.SERVER, EurekaConfig.INSTANCE.getEUREKA_SPEC(), "valkyrienskies/vs_eureka.toml");
        ModConfigEvents.loading(EurekaMod.MOD_ID).register(EurekaConfig.INSTANCE::update);
        ModConfigEvents.reloading(EurekaMod.MOD_ID).register(EurekaConfig.INSTANCE::update);

        EurekaMod.init();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> WelcomeMessage.onPlayerJoin(handler.getPlayer()));
    }

    @Environment(EnvType.CLIENT)
    public static class Client implements ClientModInitializer {

        @Override
        public void onInitializeClient() {
            EurekaMod.initClient();
            EntityRenderers.register(EurekaEntities.INSTANCE.getCANNONBALL().get(), ThrownItemRenderer::new);
            BlockEntityRenderers.register(
                    EurekaBlockEntities.INSTANCE.getSHIP_HELM().get(),
                    ShipHelmBlockEntityRenderer::new
            );

            ModelLoadingPlugin.register(context -> {
                for (final IWoodType woodType : WoodType.getEntries()) {
                    context.addModel(
                        WheelModels.INSTANCE.getKeys().get(woodType),
                        SimpleUnbakedExtraModel.blockStateModel(Identifier.fromNamespaceAndPath(
                            EurekaMod.MOD_ID,
                            "block/" + woodType.getSerializedName().toLowerCase() + "_ship_helm_wheel"
                        ))
                    );
                }
            });

            Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                EurekaItems.INSTANCE.getTAB(),
                CreativeTabs.INSTANCE.create()
            );

            ModContainer eureka = FabricLoader.getInstance().getModContainer(EurekaMod.MOD_ID)
                    .orElseThrow(() -> new IllegalStateException("Eureka's ModContainer couldn't be found!"));
            Identifier packId = Identifier.fromNamespaceAndPath(EurekaMod.MOD_ID, "retro_helms");
            ResourceLoader.registerBuiltinPack(packId, eureka, Component.literal("Eureka retro helms"), PackActivationType.NORMAL);
        }
    }
}
