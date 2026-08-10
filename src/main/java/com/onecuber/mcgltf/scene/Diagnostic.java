package com.onecuber.mcgltf.scene;

import com.onecuber.mcgltf.world.BlockPoint;
import java.util.Objects;
import java.util.Optional;

public record Diagnostic(
        Severity severity,
        String code,
        String objectId,
        Optional<BlockPoint> position,
        String rendererClass,
        String exceptionType,
        String message) {
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rendererClass, "rendererClass");
        Objects.requireNonNull(exceptionType, "exceptionType");
        Objects.requireNonNull(message, "message");
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Diagnostic code must not be empty");
        }
    }

    public enum Severity {
        WARNING,
        FAILURE,
        FATAL
    }
}
