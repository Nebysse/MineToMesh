package com.nebysse.minetomesh.testmod;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class TestFluidRegistration {
    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace(
            "block/water_still");
    private static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace(
            "block/water_flow");

    private TestFluidRegistration() {
    }

    public static void registerClientExtension(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WATER_FLOW;
            }

            @Override
            public int getTintColor() {
                return 0xFF8A4FFF;
            }
        }, TestContent.TEST_FLUID_TYPE.get());
    }
}
