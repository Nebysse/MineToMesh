package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.util.ArrayList;
import java.util.List;

public final class CapturingVertexConsumer implements VertexConsumer {
    private final List<Vertex> vertices = new ArrayList<>();
    private PendingVertex pending;
    private List<Vertex> finishedVertices;

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        requireNotFinished();
        commitPending();
        pending = new PendingVertex(new Vec3f(x, y, z));
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        pending().color = new ColorRgba(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return setColor(
                argb >> 16 & 0xFF,
                argb >> 8 & 0xFF,
                argb & 0xFF,
                argb >>> 24);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        pending().uv = new Vec2f(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        pending();
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        pending();
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        pending().normal = new Vec3f(x, y, z).normalizedOrUp();
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        pending();
        return this;
    }

    public List<Vertex> finish() {
        if (finishedVertices == null) {
            commitPending();
            finishedVertices = List.copyOf(vertices);
        }
        return finishedVertices;
    }

    private PendingVertex pending() {
        requireNotFinished();
        if (pending == null) {
            throw new IllegalStateException("A vertex position must be added before setting attributes");
        }
        return pending;
    }

    private void commitPending() {
        if (pending != null) {
            vertices.add(new Vertex(pending.position, pending.normal, pending.uv, pending.color));
            pending = null;
        }
    }

    private void requireNotFinished() {
        if (finishedVertices != null) {
            throw new IllegalStateException("Vertex stream is already finished");
        }
    }

    private static final class PendingVertex {
        private final Vec3f position;
        private Vec3f normal = Vec3f.UP;
        private Vec2f uv = new Vec2f(0.0F, 0.0F);
        private ColorRgba color = ColorRgba.WHITE;

        private PendingVertex(Vec3f position) {
            this.position = position;
        }
    }
}
