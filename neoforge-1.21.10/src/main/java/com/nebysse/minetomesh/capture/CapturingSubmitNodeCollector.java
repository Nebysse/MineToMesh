package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.scene.MaterialKey;
import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/** Captures the geometry-bearing submit nodes emitted by 1.21.10 renderers. */
public final class CapturingSubmitNodeCollector extends SubmitNodeStorage {
    private final CapturingMultiBufferSource buffers;

    public CapturingSubmitNodeCollector(
            String objectId,
            Function<RenderTypeDescriptor, MaterialKey> materialResolver) {
        this.buffers = new CapturingMultiBufferSource(objectId, materialResolver);
    }

    @Override
    public <S> void submitModel(
            Model<? super S> model,
            S state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            @Nullable TextureAtlasSprite sprite,
            int outlineColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        model.setupAnim(state);
        model.renderToBuffer(
                poseStack, buffers.getBuffer(renderType),
                lightCoords, overlayCoords, tintedColor);
    }

    @Override
    public void submitModelPart(
            ModelPart modelPart,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            @Nullable TextureAtlasSprite sprite,
            boolean sheeted,
            boolean hasFoil,
            int tintedColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
            int outlineColor) {
        modelPart.render(
                poseStack, buffers.getBuffer(renderType),
                lightCoords, overlayCoords, tintedColor);
    }

    @Override
    public void submitCustomGeometry(
            PoseStack poseStack,
            RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer) {
        renderer.render(poseStack.last(), buffers.getBuffer(renderType));
    }

    public CapturingMultiBufferSource.CaptureResult finishAll() {
        return buffers.finishAll();
    }
}
