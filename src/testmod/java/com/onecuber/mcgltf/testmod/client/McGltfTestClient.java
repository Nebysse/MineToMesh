package com.onecuber.mcgltf.testmod.client;

import com.onecuber.mcgltf.testmod.McGltfTestMod;
import com.onecuber.mcgltf.testmod.TestContent;
import com.onecuber.mcgltf.testmod.TestFluidRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class McGltfTestClient {

    private McGltfTestClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(McGltfTestClient::clientSetup);
        modBus.addListener(McGltfTestClient::modifyBakingResult);
        modBus.addListener(McGltfTestClient::registerRenderers);
        modBus.addListener(McGltfTestClient::registerClientExtensions);
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
        return location.id().getNamespace().equals(McGltfTestMod.MOD_ID)
                && location.id().getPath().equals("model_data_block");
    }
}
