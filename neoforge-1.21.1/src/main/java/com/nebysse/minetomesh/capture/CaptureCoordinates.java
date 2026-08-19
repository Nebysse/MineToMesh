package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.world.Selection;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class CaptureCoordinates {
    private CaptureCoordinates() {
    }

    public static Vec3f localPosition(
            double x, double y, double z, Selection selection) {
        Objects.requireNonNull(selection, "selection");
        return new Vec3f(
                (float) (x - selection.min().x()),
                (float) (y - selection.min().y()),
                (float) (z - selection.min().z()));
    }

    public static PoseStack translatedPose(Vec3f translation) {
        Objects.requireNonNull(translation, "translation");
        PoseStack poseStack = new PoseStack();
        poseStack.translate(translation.x(), translation.y(), translation.z());
        return poseStack;
    }

    public static Bounds localBounds(AABB box, Selection selection) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(selection, "selection");
        return new Bounds(
                localPosition(box.minX, box.minY, box.minZ, selection),
                localPosition(box.maxX, box.maxY, box.maxZ, selection));
    }

    public static Bounds blockBounds(BlockPos position, Selection selection) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(selection, "selection");
        Vec3f min = localPosition(
                position.getX(), position.getY(), position.getZ(), selection);
        return new Bounds(
                min,
                new Vec3f(min.x() + 1.0F, min.y() + 1.0F, min.z() + 1.0F));
    }

    public record Bounds(Vec3f min, Vec3f max) {
        public Bounds {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
        }
    }
}
