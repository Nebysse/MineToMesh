package com.nebysse.minetomesh.backend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

public final class RenderBackendDiscovery {
    private final List<RenderBackendAdapter> platformAdapters;

    public RenderBackendDiscovery(List<RenderBackendAdapter> platformAdapters) {
        this.platformAdapters = List.copyOf(
                Objects.requireNonNull(platformAdapters, "platformAdapters"));
    }

    public RenderBackendRegistry discover(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        Map<String, RenderBackendAdapter> discovered = new LinkedHashMap<>();
        for (RenderBackendAdapter adapter : platformAdapters) {
            discovered.put(adapter.id(), adapter);
        }
        for (RenderBackendAdapter adapter : ServiceLoader.load(
                RenderBackendAdapter.class, classLoader)) {
            discovered.putIfAbsent(adapter.id(), adapter);
        }
        return new RenderBackendRegistry(new ArrayList<>(discovered.values()));
    }
}
