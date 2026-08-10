package com.onecuber.mcgltf.backend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

public final class RenderBackendRegistry {
    private static final RenderBackendAdapter.Scope NOOP_SCOPE =
            new RenderBackendAdapter.Scope() {
                @Override
                public String adapterId() {
                    return "none";
                }

                @Override
                public boolean active() {
                    return false;
                }

                @Override
                public void close() {
                }
            };

    private final List<RenderBackendAdapter> adapters;

    public RenderBackendRegistry(List<RenderBackendAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        this.adapters = List.copyOf(adapters);
    }

    public static RenderBackendRegistry discover(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        Map<String, RenderBackendAdapter> discovered = new LinkedHashMap<>();
        FlywheelBackendAdapter.discover(classLoader)
                .ifPresent(adapter -> discovered.put(adapter.id(), adapter));
        for (RenderBackendAdapter adapter : ServiceLoader.load(
                RenderBackendAdapter.class, classLoader)) {
            discovered.putIfAbsent(adapter.id(), adapter);
        }
        return new RenderBackendRegistry(new ArrayList<>(discovered.values()));
    }

    public RenderBackendAdapter.Scope enter() throws Exception {
        for (RenderBackendAdapter adapter : adapters) {
            if (adapter.isActive()) {
                return Objects.requireNonNull(adapter.enter(), "adapter scope");
            }
        }
        return NOOP_SCOPE;
    }

    public List<String> adapterIds() {
        return adapters.stream().map(RenderBackendAdapter::id).toList();
    }
}
