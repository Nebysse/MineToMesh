package com.onecuber.mcgltf.client.wand;

public class ExportWandBorderPolicy {
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
        int target = Math.max(1, (int) Math.round(physicalPixels / guiScale));
        int safeMaximum = Math.min(
                Math.min((sourceWidth - 1) / 2, (sourceHeight - 1) / 2),
                Math.min((destinationWidth - 1) / 2, (destinationHeight - 1) / 2));
        return Math.min(target, Math.max(0, safeMaximum));
    }

    protected ExportWandBorderPolicy() {
    }
}

final class WorkstationBorderPolicy extends ExportWandBorderPolicy {
    private WorkstationBorderPolicy() {
    }
}
