package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.scene.CapturedNode;
import com.onecuber.mcgltf.scene.ColorRgba;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.Vec3f;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceholderFactoryTest {
    @Test
    void unitAabbProducesMagentaTransparentIndexedBoxWithOutwardNormals() {
        CapturedNode node = PlaceholderFactory.create(
                "missing", new Vec3f(0.0F, 0.0F, 0.0F),
                new Vec3f(1.0F, 1.0F, 1.0F), Map.of("reason", "test"));

        assertEquals(CapturedNode.Kind.PLACEHOLDER, node.kind());
        assertEquals(1, node.primitives().size());
        var primitive = node.primitives().getFirst();
        assertEquals(8, primitive.vertices().size());
        assertEquals(36, primitive.indices().length);
        assertEquals(4, primitive.gltfMode());
        assertEquals(MaterialKey.AlphaMode.BLEND, primitive.material().alphaMode());
        assertTrue(primitive.material().doubleSided());
        assertEquals(new ColorRgba(255, 0, 255, 128),
                primitive.vertices().getFirst().color());

        Vec3f center = new Vec3f(0.5F, 0.5F, 0.5F);
        for (var vertex : primitive.vertices()) {
            Vec3f delta = new Vec3f(
                    vertex.position().x() - center.x(),
                    vertex.position().y() - center.y(),
                    vertex.position().z() - center.z());
            float dot = delta.x() * vertex.normal().x()
                    + delta.y() * vertex.normal().y()
                    + delta.z() * vertex.normal().z();
            assertTrue(dot > 0.0F, "normal must point out of the box");
        }
    }
}
