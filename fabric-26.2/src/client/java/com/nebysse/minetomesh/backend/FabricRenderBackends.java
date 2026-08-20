package com.nebysse.minetomesh.backend;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricRenderBackends {
    private FabricRenderBackends() {
    }

    public static RenderBackendRegistry discover(ClassLoader classLoader) {
        // No compatible Flywheel API is available for Minecraft 26.2.
        // Presence is checked without linking its classes; renderer failures
        // continue through the normal diagnostic and placeholder path.
        FabricLoader.getInstance().isModLoaded("flywheel");
        return new RenderBackendDiscovery(List.of()).discover(classLoader);
    }
}
