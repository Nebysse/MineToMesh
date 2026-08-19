package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectCaptureDecisionTest {
    @ParameterizedTest
    @CsvSource({
            "true,  GEOMETRY, false, false",
            "true,  EMPTY,    false, false",
            "true,  FAILED,   false, true",
            "false, GEOMETRY, false, false",
            "false, EMPTY,    true,  false",
            "false, FAILED,   true,  false"
    })
    void decidesPlaceholderAndPartialCapture(
            boolean staticGeometry,
            CaptureState auxiliary,
            boolean placeholder,
            boolean partial) {
        ObjectCaptureDecision decision =
                ObjectCaptureDecision.decide(staticGeometry, auxiliary);
        assertEquals(placeholder, decision.placeholder());
        assertEquals(partial, decision.partial());
    }
}
