package com.nebysse.minetomesh.backend;

import java.util.List;
import java.util.Objects;

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
