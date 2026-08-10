package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import net.minecraft.core.BlockPos;

public final class WorkstationRequestPolicy {
    public record Validation(boolean accepted, String reasonKey) {
        public static Validation accept() {
            return new Validation(true, "");
        }

        public static Validation reject(String key) {
            return new Validation(false, key);
        }
    }

    private WorkstationRequestPolicy() {
    }

    public static Validation validateMenuIdentity(BlockPos payloadStation, BlockPos menuStation) {
        return payloadStation.equals(menuStation)
                ? Validation.accept()
                : Validation.reject("mcgltf.error.workstation.wrong_station");
    }

    public static Validation validateStationPresent(boolean present) {
        return present
                ? Validation.accept()
                : Validation.reject("mcgltf.error.workstation.missing_station");
    }

    public static Validation validateCoordinates(
            WorkstationCoordinates coordinates, int minBuildHeight, int maxBuildHeight) {
        for (BlockPos position : new BlockPos[]{coordinates.first(), coordinates.second()}) {
            if (position.getY() < minBuildHeight || position.getY() > maxBuildHeight) {
                return Validation.reject("mcgltf.error.workstation.out_of_bounds");
            }
        }
        return Validation.accept();
    }

    public static Validation validateExportPermission(
            boolean localSingleplayer, boolean hasLevelTwoCommandPermission) {
        return localSingleplayer || hasLevelTwoCommandPermission
                ? Validation.accept()
                : Validation.reject("mcgltf.error.workstation.no_export_permission");
    }

    public static Validation validateExportName(String exportName) {
        if (exportName == null
                || exportName.codePointCount(0, exportName.length()) > 64) {
            return Validation.reject("mcgltf.error.workstation.invalid_name");
        }
        try {
            ExportName.parse(exportName);
            return Validation.accept();
        } catch (IllegalArgumentException exception) {
            return Validation.reject("mcgltf.error.workstation.invalid_name");
        }
    }
}
