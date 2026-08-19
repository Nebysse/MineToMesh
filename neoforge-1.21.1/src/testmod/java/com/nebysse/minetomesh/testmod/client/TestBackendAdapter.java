package com.nebysse.minetomesh.testmod.client;

import com.nebysse.minetomesh.backend.RenderBackendAdapter;

public final class TestBackendAdapter implements RenderBackendAdapter {
    public static boolean accelerated = true;
    public static boolean captureFallback;

    @Override
    public String id() {
        return "minetomesh_test";
    }

    @Override
    public boolean isActive() {
        return accelerated;
    }

    @Override
    public Scope enter() {
        boolean previous = captureFallback;
        captureFallback = true;
        return new Scope() {
            private boolean closed;

            @Override
            public String adapterId() {
                return id();
            }

            @Override
            public boolean active() {
                return true;
            }

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    captureFallback = previous;
                }
            }
        };
    }
}
