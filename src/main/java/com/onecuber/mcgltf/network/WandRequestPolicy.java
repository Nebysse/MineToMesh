package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.wand.ExportWandSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class WandRequestPolicy {
    public record Validation(boolean accepted, String reasonKey) {
        public static Validation accept() {
            return new Validation(true, "");
        }

        public static Validation reject(String key) {
            return new Validation(false, key);
        }
    }

    private WandRequestPolicy() {
    }

    public static Validation validateSelection(
            ExportWandSelection selection,
            int minBuildHeight,
            int maxBuildHeight) {
        if (selection == null || !selection.isComplete()) {
            return Validation.reject("mcgltf.error.wand.incomplete_selection");
        }
        for (BlockPos position : new BlockPos[]{
                selection.pos1().orElseThrow(), selection.pos2().orElseThrow()}) {
            if (position.getY() < minBuildHeight
                    || position.getY() >= maxBuildHeight) {
                return Validation.reject("mcgltf.error.wand.out_of_bounds");
            }
        }
        return Validation.accept();
    }

    public static Validation validateDimension(
            ExportWandSelection selection, ResourceLocation currentDimension) {
        return selection != null
                && selection.selectionDimension().filter(currentDimension::equals).isPresent()
                ? Validation.accept()
                : Validation.reject("mcgltf.error.wand.wrong_dimension");
    }

    public static Validation validateExportPermission(
            boolean localSingleplayer, boolean hasLevelTwoCommandPermission) {
        return localSingleplayer || hasLevelTwoCommandPermission
                ? Validation.accept()
                : Validation.reject("mcgltf.error.wand.no_export_permission");
    }

    public static Validation validateExportName(String exportName) {
        if (exportName == null
                || exportName.codePointCount(0, exportName.length()) > 64) {
            return Validation.reject("mcgltf.error.wand.invalid_name");
        }
        try {
            ExportName.parse(exportName);
            return Validation.accept();
        } catch (IllegalArgumentException exception) {
            return Validation.reject("mcgltf.error.wand.invalid_name");
        }
    }
}
