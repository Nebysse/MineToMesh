package com.onecuber.mcgltf.capture;

import com.onecuber.mcgltf.backend.RenderBackendAdapter;
import com.onecuber.mcgltf.backend.RenderBackendRegistry;
import java.util.Objects;
import java.util.Optional;

public final class RendererReplay {
    private final RenderBackendRegistry registry;

    public RendererReplay(RenderBackendRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public Outcome run(Action action) {
        Objects.requireNonNull(action, "action");
        RenderBackendAdapter.Scope scope;
        try {
            scope = registry.enter();
        } catch (Exception exception) {
            return Outcome.failure(
                    false, "none", FailureStage.BACKEND, exception);
        }

        boolean fallbackUsed = scope.active();
        String adapterId = scope.adapterId();
        Exception actionFailure = null;
        Exception closeFailure = null;
        try {
            action.run();
        } catch (Throwable throwable) {
            actionFailure = asException(throwable);
        } finally {
            try {
                scope.close();
            } catch (Exception exception) {
                closeFailure = exception;
            }
        }

        if (actionFailure != null) {
            if (closeFailure != null) {
                actionFailure.addSuppressed(closeFailure);
            }
            return Outcome.failure(
                    fallbackUsed, adapterId, FailureStage.RENDERER, actionFailure);
        }
        if (closeFailure != null) {
            return Outcome.failure(
                    fallbackUsed, adapterId, FailureStage.RESTORE, closeFailure);
        }
        return new Outcome(
                true,
                fallbackUsed,
                adapterId,
                FailureStage.NONE,
                Optional.empty());
    }

    private static Exception asException(Throwable throwable) {
        return throwable instanceof Exception exception
                ? exception : new RuntimeException(throwable);
    }

    public enum FailureStage {
        NONE,
        BACKEND,
        RENDERER,
        RESTORE
    }

    public record Outcome(
            boolean success,
            boolean fallbackUsed,
            String adapterId,
            FailureStage failureStage,
            Optional<Exception> failure) {
        public Outcome {
            Objects.requireNonNull(adapterId, "adapterId");
            Objects.requireNonNull(failureStage, "failureStage");
            Objects.requireNonNull(failure, "failure");
        }

        private static Outcome failure(
                boolean fallbackUsed,
                String adapterId,
                FailureStage stage,
                Exception exception) {
            return new Outcome(
                    false,
                    fallbackUsed,
                    adapterId,
                    stage,
                    Optional.of(exception));
        }
    }

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }
}
