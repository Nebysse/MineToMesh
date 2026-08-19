package com.nebysse.minetomesh.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class RenderBackendDiscoveryTest {
    @Test
    void preservesExplicitPlatformAdapters() {
        RenderBackendAdapter explicit = new RenderBackendAdapter() {
            @Override
            public String id() {
                return "explicit";
            }

            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public Scope enter() {
                throw new AssertionError("inactive adapter must not be entered");
            }
        };

        RenderBackendRegistry registry = new RenderBackendDiscovery(List.of(explicit))
                .discover(getClass().getClassLoader());

        assertEquals(List.of("explicit"), registry.adapterIds());
    }
}
