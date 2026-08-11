package com.nebysse.minetomesh.capture;

import java.util.Objects;

public record ObjectCaptureDecision(boolean placeholder, boolean partial) {
    public static ObjectCaptureDecision decide(
            boolean staticGeometry,
            CaptureState auxiliary) {
        Objects.requireNonNull(auxiliary, "auxiliary");
        return new ObjectCaptureDecision(
                !staticGeometry && auxiliary != CaptureState.GEOMETRY,
                staticGeometry && auxiliary == CaptureState.FAILED);
    }
}
