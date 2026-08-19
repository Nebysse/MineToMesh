package com.nebysse.minetomesh.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlywheelBackendAdapterTest {
    private Object original;

    @BeforeEach
    void resetBackend() {
        original = new Object();
        FakeBackendManager.backend = original;
    }

    @Test
    void nestedScopesRestoreOriginalBackendOnlyAtOuterClose() throws Exception {
        FlywheelBackendAdapter adapter =
                FlywheelBackendAdapter.forClass(FakeBackendManager.class);

        try (var outer = adapter.enter()) {
            assertSame(FakeBackendManager.OFF_BACKEND, FakeBackendManager.backend);
            try (var inner = adapter.enter()) {
                assertSame(FakeBackendManager.OFF_BACKEND, FakeBackendManager.backend);
            }
            assertSame(FakeBackendManager.OFF_BACKEND, FakeBackendManager.backend);
        }

        assertSame(original, FakeBackendManager.backend);
    }

    @Test
    void closeRestoresAfterRendererFailure() throws Exception {
        FlywheelBackendAdapter adapter =
                FlywheelBackendAdapter.forClass(FakeBackendManager.class);

        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = adapter.enter()) {
                throw new IllegalStateException("renderer failed");
            }
        });

        assertSame(original, FakeBackendManager.backend);
    }

    @Test
    void activeReflectsWhetherBackendIsOff() throws Exception {
        FlywheelBackendAdapter adapter =
                FlywheelBackendAdapter.forClass(FakeBackendManager.class);
        assertTrue(adapter.isActive());

        FakeBackendManager.backend = FakeBackendManager.OFF_BACKEND;

        assertFalse(adapter.isActive());
    }

    static final class FakeBackendManager {
        static final Object OFF_BACKEND = new Object();
        static Object backend = new Object();
    }
}
