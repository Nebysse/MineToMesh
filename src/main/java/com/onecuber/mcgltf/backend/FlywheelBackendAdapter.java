package com.onecuber.mcgltf.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;

public final class FlywheelBackendAdapter implements RenderBackendAdapter {
    private static final String IMPLEMENTATION =
            "dev.engine_room.flywheel.impl.BackendManagerImpl";
    private static final String BACKEND_FIELD = "backend";
    private static final String OFF_FIELD = "OFF_BACKEND";

    private final Field backendField;
    private final Object offBackend;
    private final boolean enforceRenderThread;
    private final Object lock = new Object();
    private int depth;
    private Object savedBackend;

    private FlywheelBackendAdapter(
            Field backendField,
            Object offBackend,
            boolean enforceRenderThread) {
        this.backendField = Objects.requireNonNull(backendField, "backendField");
        this.offBackend = Objects.requireNonNull(offBackend, "offBackend");
        this.enforceRenderThread = enforceRenderThread;
    }

    public static Optional<FlywheelBackendAdapter> discover(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        try {
            Class<?> implementation = Class.forName(
                    IMPLEMENTATION, false, classLoader);
            return Optional.of(create(implementation, true));
        } catch (ReflectiveOperationException | SecurityException exception) {
            return Optional.empty();
        }
    }

    static FlywheelBackendAdapter forClass(Class<?> implementation)
            throws ReflectiveOperationException {
        return create(implementation, false);
    }

    private static FlywheelBackendAdapter create(
            Class<?> implementation,
            boolean enforceRenderThread) throws ReflectiveOperationException {
        Field backend = implementation.getDeclaredField(BACKEND_FIELD);
        Field off = implementation.getDeclaredField(OFF_FIELD);
        if (!backend.trySetAccessible() || !off.trySetAccessible()) {
            throw new IllegalAccessException("Flywheel backend fields are not accessible");
        }
        return new FlywheelBackendAdapter(
                backend, off.get(null), enforceRenderThread);
    }

    @Override
    public String id() {
        return "flywheel";
    }

    @Override
    public boolean isActive() {
        synchronized (lock) {
            try {
                return backendField.get(null) != offBackend;
            } catch (IllegalAccessException exception) {
                return false;
            }
        }
    }

    @Override
    public Scope enter() throws IllegalAccessException {
        if (enforceRenderThread && !RenderSystem.isOnRenderThreadOrInit()) {
            throw new IllegalStateException(
                    "Flywheel fallback capture must run on the render thread");
        }
        synchronized (lock) {
            if (depth == 0) {
                Object current = backendField.get(null);
                backendField.set(null, offBackend);
                savedBackend = current;
            }
            depth = Math.addExact(depth, 1);
        }
        return new FlywheelScope();
    }

    private final class FlywheelScope implements Scope {
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
        public void close() throws IllegalAccessException {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                depth--;
                if (depth == 0) {
                    Object restore = savedBackend;
                    try {
                        backendField.set(null, restore);
                    } finally {
                        savedBackend = null;
                    }
                }
            }
        }
    }
}
