package com.nebysse.minetomesh.backend;

import java.util.List;

public final class NeoForgeRenderBackends {
    private NeoForgeRenderBackends() {
    }

    public static RenderBackendRegistry discover(ClassLoader classLoader) {
        List<RenderBackendAdapter> platformAdapters = FlywheelBackendAdapter
                .discover(classLoader)
                .map(adapter -> List.<RenderBackendAdapter>of(adapter))
                .orElseGet(List::of);
        return new RenderBackendDiscovery(platformAdapters).discover(classLoader);
    }
}
