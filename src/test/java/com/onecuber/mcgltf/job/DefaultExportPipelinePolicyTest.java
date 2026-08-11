package com.onecuber.mcgltf.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.capture.CaptureState;
import com.onecuber.mcgltf.scene.Diagnostic;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultExportPipelinePolicyTest {
    @Test
    void staticGeometrySuppressesPlaceholderForEmptyAuxiliaryRenderer() {
        assertFalse(DefaultExportPipeline.shouldCreateBlockPlaceholder(
                CaptureState.GEOMETRY, CaptureState.EMPTY));
    }

    @Test
    void missingStaticAndAuxiliaryGeometryRequiresPlaceholder() {
        assertTrue(DefaultExportPipeline.shouldCreateBlockPlaceholder(
                CaptureState.EMPTY, CaptureState.EMPTY));
        assertTrue(DefaultExportPipeline.shouldCreateBlockPlaceholder(
                CaptureState.FAILED, CaptureState.FAILED));
    }

    @Test
    void resolvesExportsUnderMineToMeshDirectory() {
        Path gameDirectory = Path.of("game");

        assertEquals(
                gameDirectory.resolve("minetomesh-exports"),
                DefaultExportPipeline.exportRoot(gameDirectory));
    }

    @Test
    void informationalDiagnosticsDoNotCountAsWarnings() {
        assertEquals(2, DefaultExportPipeline.warningCount(List.of(
                diagnostic(Diagnostic.Severity.INFO),
                diagnostic(Diagnostic.Severity.WARNING),
                diagnostic(Diagnostic.Severity.FAILURE),
                diagnostic(Diagnostic.Severity.FATAL))));
    }

    private static Diagnostic diagnostic(Diagnostic.Severity severity) {
        return new Diagnostic(
                severity, "TEST", "object", Optional.empty(), "", "", "message");
    }
}
