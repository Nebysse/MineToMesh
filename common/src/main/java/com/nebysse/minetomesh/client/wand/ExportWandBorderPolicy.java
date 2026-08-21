package com.nebysse.minetomesh.client.wand;

public final class ExportWandBorderPolicy {
    public static int logicalBorder(
            int physicalPixels, double guiScale,
            int sourceWidth, int sourceHeight,
            int destinationWidth, int destinationHeight) {
        if (physicalPixels < 0
                || !Double.isFinite(guiScale)
                || guiScale <= 0.0
                || sourceWidth <= 0
                || sourceHeight <= 0
                || destinationWidth <= 0
                || destinationHeight <= 0) {
            throw new IllegalArgumentException("invalid border inputs");
        }
        if (physicalPixels == 0) {
            return 0;
        }
        // GuiGraphics works in logical pixels; the active GUI scale only
        // affects the final framebuffer upscale and must not shrink borders.
        int target = physicalPixels;
        int safeMaximum = Math.min(
                Math.min((sourceWidth - 1) / 2, (sourceHeight - 1) / 2),
                Math.min((destinationWidth - 1) / 2, (destinationHeight - 1) / 2));
        return Math.min(target, Math.max(0, safeMaximum));
    }

    private ExportWandBorderPolicy() {
    }
}
