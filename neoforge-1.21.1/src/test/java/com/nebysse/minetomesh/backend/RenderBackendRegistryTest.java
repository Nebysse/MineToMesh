package com.nebysse.minetomesh.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RenderBackendRegistryTest {
    @Test
    void opensFirstActiveAdapterAndClosesItOnce() throws Exception {
        FakeAdapter inactive = new FakeAdapter("inactive", false);
        FakeAdapter active = new FakeAdapter("active", true);
        RenderBackendRegistry registry =
                new RenderBackendRegistry(List.of(inactive, active));

        try (RenderBackendAdapter.Scope scope = registry.enter()) {
            assertTrue(scope.active());
            assertEquals("active", scope.adapterId());
            assertEquals(1, active.enterCount);
        }

        assertEquals(1, active.closeCount);
        assertEquals(0, inactive.enterCount);
    }

    @Test
    void returnsNoopWhenNoBackendIsActive() throws Exception {
        try (RenderBackendAdapter.Scope scope =
                     new RenderBackendRegistry(List.of()).enter()) {
            assertFalse(scope.active());
            assertEquals("none", scope.adapterId());
        }
    }

    private static final class FakeAdapter implements RenderBackendAdapter {
        private final String id;
        private final boolean active;
        private int enterCount;
        private int closeCount;

        private FakeAdapter(String id, boolean active) {
            this.id = id;
            this.active = active;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public Scope enter() {
            enterCount++;
            return new Scope() {
                private boolean closed;

                @Override
                public String adapterId() {
                    return id;
                }

                @Override
                public boolean active() {
                    return true;
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        closeCount++;
                    }
                }
            };
        }
    }
}
