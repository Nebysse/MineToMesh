package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.scene.Vec2f;
import java.util.List;
import org.junit.jupiter.api.Test;

class FluidSpriteClassifierTest {
    @Test
    void classifiesUvsInsideStillFlowAndOverlayRectangles() {
        var still = new FluidGeometryCapture.SpriteBounds("still", 0.0F, 0.0F, 0.25F, 0.25F);
        var flow = new FluidGeometryCapture.SpriteBounds("flow", 0.25F, 0.0F, 0.5F, 0.25F);
        var overlay = new FluidGeometryCapture.SpriteBounds("overlay", 0.5F, 0.0F, 0.75F, 0.25F);
        List<FluidGeometryCapture.SpriteBounds> sprites = List.of(still, flow, overlay);

        assertEquals(still, FluidGeometryCapture.classifySprite(
                List.of(new Vec2f(0.01F, 0.01F), new Vec2f(0.24F, 0.24F)), sprites).orElseThrow());
        assertEquals(flow, FluidGeometryCapture.classifySprite(
                List.of(new Vec2f(0.26F, 0.01F), new Vec2f(0.49F, 0.24F)), sprites).orElseThrow());
        assertEquals(overlay, FluidGeometryCapture.classifySprite(
                List.of(new Vec2f(0.51F, 0.01F), new Vec2f(0.74F, 0.24F)), sprites).orElseThrow());
    }

    @Test
    void choosesSmallestContainingRectangleAndReturnsEmptyWhenUnmatched() {
        var broad = new FluidGeometryCapture.SpriteBounds("broad", 0.0F, 0.0F, 1.0F, 1.0F);
        var narrow = new FluidGeometryCapture.SpriteBounds("narrow", 0.25F, 0.25F, 0.75F, 0.75F);

        assertEquals(narrow, FluidGeometryCapture.classifySprite(
                List.of(new Vec2f(0.4F, 0.4F), new Vec2f(0.6F, 0.6F)),
                List.of(broad, narrow)).orElseThrow());
        assertTrue(FluidGeometryCapture.classifySprite(
                List.of(new Vec2f(2.0F, 2.0F)), List.of(broad, narrow)).isEmpty());
    }
}
