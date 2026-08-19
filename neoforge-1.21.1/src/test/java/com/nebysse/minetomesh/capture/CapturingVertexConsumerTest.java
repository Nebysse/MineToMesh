package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapturingVertexConsumerTest {
    @Test
    void commitsThePreviousVertexWhenTheNextPositionStarts() {
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();

        consumer.addVertex(1.0F, 2.0F, 3.0F)
                .setColor(10, 20, 30, 40)
                .setUv(0.25F, 0.75F)
                .setNormal(0.0F, 0.0F, 2.0F);
        consumer.addVertex(4.0F, 5.0F, 6.0F);
        List<Vertex> vertices = consumer.finish();

        assertEquals(2, vertices.size());
        assertEquals(new Vertex(
                new Vec3f(1.0F, 2.0F, 3.0F),
                new Vec3f(0.0F, 0.0F, 1.0F),
                new Vec2f(0.25F, 0.75F),
                new ColorRgba(10, 20, 30, 40)), vertices.get(0));
        assertEquals(new Vertex(
                new Vec3f(4.0F, 5.0F, 6.0F), Vec3f.UP,
                new Vec2f(0.0F, 0.0F), ColorRgba.WHITE), vertices.get(1));
    }

    @Test
    void settersRequireAPendingPositionAndIgnoredUvsRemainChainable() {
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();

        assertThrows(IllegalStateException.class, () -> consumer.setColor(1, 2, 3, 4));
        consumer.addVertex(0.0F, 0.0F, 0.0F)
                .setUv1(1, 2)
                .setUv2(3, 4);
        assertEquals(1, consumer.finish().size());
    }

    @Test
    void finishIsIdempotentAndClosesTheStream() {
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        consumer.addVertex(0.0F, 0.0F, 0.0F);

        List<Vertex> first = consumer.finish();
        List<Vertex> second = consumer.finish();

        assertEquals(first, second);
        assertThrows(IllegalStateException.class, () -> consumer.addVertex(1.0F, 1.0F, 1.0F));
    }
}
