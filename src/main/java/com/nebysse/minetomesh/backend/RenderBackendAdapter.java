package com.nebysse.minetomesh.backend;

public interface RenderBackendAdapter {
    String id();

    boolean isActive();

    Scope enter() throws Exception;

    interface Scope extends AutoCloseable {
        String adapterId();

        boolean active();

        @Override
        void close() throws Exception;
    }
}
