package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.backend.RenderBackendAdapter;
import com.onecuber.mcgltf.backend.RenderBackendRegistry;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RendererReplayTest {
    @Test
    void invokesActionInsideBackendScopeAndRestoresIt() {
        FakeAdapter adapter = new FakeAdapter();
        RendererReplay replay = new RendererReplay(
                new RenderBackendRegistry(List.of(adapter)));
        AtomicBoolean invoked = new AtomicBoolean();

        RendererReplay.Outcome outcome = replay.run(() -> {
            assertTrue(adapter.inScope);
            invoked.set(true);
        });

        assertTrue(outcome.success());
        assertTrue(outcome.fallbackUsed());
        assertEquals("fake", outcome.adapterId());
        assertEquals(RendererReplay.FailureStage.NONE, outcome.failureStage());
        assertTrue(invoked.get());
        assertFalse(adapter.inScope);
    }

    @Test
    void distinguishesBackendEntryAndRendererFailures() {
        FakeAdapter entryFailure = new FakeAdapter();
        entryFailure.failEnter = true;
        RendererReplay.Outcome backend = new RendererReplay(
                new RenderBackendRegistry(List.of(entryFailure))).run(() -> {});

        assertFalse(backend.success());
        assertEquals(RendererReplay.FailureStage.BACKEND, backend.failureStage());
        assertTrue(backend.failure().isPresent());

        FakeAdapter rendererFailure = new FakeAdapter();
        RendererReplay.Outcome renderer = new RendererReplay(
                new RenderBackendRegistry(List.of(rendererFailure))).run(
                        () -> { throw new IOException("renderer failed"); });

        assertFalse(renderer.success());
        assertEquals(RendererReplay.FailureStage.RENDERER, renderer.failureStage());
        assertFalse(rendererFailure.inScope);
    }

    private static final class FakeAdapter implements RenderBackendAdapter {
        private boolean inScope;
        private boolean failEnter;

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Scope enter() throws Exception {
            if (failEnter) {
                throw new IOException("backend failed");
            }
            inScope = true;
            return new Scope() {
                private boolean closed;

                @Override
                public String adapterId() {
                    return "fake";
                }

                @Override
                public boolean active() {
                    return true;
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        inScope = false;
                    }
                }
            };
        }
    }
}
