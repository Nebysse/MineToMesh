package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoplanarQuadLayeringTest {
    @Test
    void offsetsEveryLaterExactLayerAlongItsOwnNormal() {
        List<Vertex> up = quad(0.0F, Vec3f.UP);
        List<Vertex> down = quad(0.0F, new Vec3f(0.0F, -1.0F, 0.0F));
        List<Vertex> third = quad(0.0F, Vec3f.UP);

        CoplanarQuadLayering.Result result = CoplanarQuadLayering.apply(
                List.of(up, down, third));

        assertEquals(0.0F, result.quads().get(0).get(0).position().y());
        assertEquals(-1.0F / 1024.0F,
                result.quads().get(1).get(0).position().y());
        assertEquals(2.0F / 1024.0F,
                result.quads().get(2).get(0).position().y());
        assertEquals(1, result.statistics().coplanarGroups());
        assertEquals(2, result.statistics().offsetFaces());
        assertEquals(3, result.statistics().maxLayers());
    }

    @Test
    void ignoresNearButNonidenticalQuads() {
        CoplanarQuadLayering.Result result = CoplanarQuadLayering.apply(List.of(
                quad(0.0F, Vec3f.UP),
                quad(Math.nextUp(0.0F), Vec3f.UP)));
        assertEquals(CoplanarQuadLayering.Statistics.ZERO, result.statistics());
    }

    @Test
    void treatsNegativeZeroAndReorderedVerticesAsTheSameGeometry() {
        List<Vertex> first = quad(-0.0F, Vec3f.UP);
        List<Vertex> second = List.of(first.get(2), first.get(3), first.get(0), first.get(1));
        assertEquals(1, CoplanarQuadLayering.apply(List.of(first, second))
                .statistics().coplanarGroups());
    }

    @Test
    void fallsBackToGeometricNormalWhenVertexNormalsCancel() {
        List<Vertex> first = quad(0.0F, Vec3f.UP);
        List<Vertex> second = List.of(
                vertex(0, 0, new Vec3f(1, 0, 0)),
                vertex(1, 0, new Vec3f(-1, 0, 0)),
                vertex(1, 1, new Vec3f(1, 0, 0)),
                vertex(0, 1, new Vec3f(-1, 0, 0)));
        CoplanarQuadLayering.Result result = CoplanarQuadLayering.apply(List.of(first, second));
        assertEquals(-1.0F / 1024.0F, result.quads().get(1).get(0).position().y());
        assertEquals(0, result.statistics().invalidNormals());
    }

    @Test
    void reportsFullyDegenerateLayerWithoutMovingIt() {
        Vertex point = new Vertex(new Vec3f(0, 0, 0), Vec3f.UP,
                new Vec2f(0, 0), ColorRgba.WHITE);
        List<Vertex> first = List.of(point, point, point, point);
        List<Vertex> second = List.of(
                new Vertex(point.position(), new Vec3f(1, 0, 0), point.uv(), point.color()),
                new Vertex(point.position(), new Vec3f(-1, 0, 0), point.uv(), point.color()),
                new Vertex(point.position(), new Vec3f(1, 0, 0), point.uv(), point.color()),
                new Vertex(point.position(), new Vec3f(-1, 0, 0), point.uv(), point.color()));
        CoplanarQuadLayering.Result result = CoplanarQuadLayering.apply(List.of(first, second));
        assertEquals(second, result.quads().get(1));
        assertEquals(1, result.statistics().invalidNormals());
        assertEquals(0, result.statistics().offsetFaces());
    }

    private static List<Vertex> quad(float y, Vec3f normal) {
        return List.of(
                vertex(0, 0, y, normal), vertex(1, 0, y, normal),
                vertex(1, 1, y, normal), vertex(0, 1, y, normal));
    }

    private static Vertex vertex(float x, float z, Vec3f normal) {
        return vertex(x, z, 0.0F, normal);
    }

    private static Vertex vertex(float x, float z, float y, Vec3f normal) {
        return new Vertex(new Vec3f(x, y, z), normal,
                new Vec2f(x, z), ColorRgba.WHITE);
    }
}
