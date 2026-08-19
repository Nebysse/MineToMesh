package com.nebysse.minetomesh.testmod.client;

import com.nebysse.minetomesh.testmod.MineToMeshTestMod;
import com.nebysse.minetomesh.testmod.TestContent;
import com.nebysse.minetomesh.testmod.TestFluidRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class MineToMeshTestClient {

    private MineToMeshTestClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MineToMeshTestClient::clientSetup);
        modBus.addListener(MineToMeshTestClient::modifyBakingResult);
        modBus.addListener(MineToMeshTestClient::registerRenderers);
        modBus.addListener(MineToMeshTestClient::registerClientExtensions);
    }

    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> Minecraft.getInstance().getTextureManager().register(
                TestEntityRenderer.TEXTURE, new GpuResidentTexture()));
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) ->
                isModelDataBlock(location) ? new TestBakedModel(model) : model);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                TestContent.RENDERED_BLOCK_ENTITY.get(), TestBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                TestContent.GPU_ONLY_BLOCK_ENTITY.get(), GpuOnlyBlockEntityRenderer::new);
        event.registerEntityRenderer(TestContent.TEST_ENTITY.get(), TestEntityRenderer::new);
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        TestFluidRegistration.registerClientExtension(event);
    }

    private static boolean isModelDataBlock(ModelResourceLocation location) {
        return location.id().getNamespace().equals(MineToMeshTestMod.MOD_ID)
                && location.id().getPath().equals("model_data_block");
    }
}
