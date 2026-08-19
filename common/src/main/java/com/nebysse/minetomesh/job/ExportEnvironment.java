package com.nebysse.minetomesh.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExportEnvironment(
        String minecraftVersion,
        String loaderName,
        String loaderVersion,
        String exporterVersion,
        List<String> activeResourcePacks,
        List<String> loadedMods) {
    public ExportEnvironment {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loaderName, "loaderName");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(exporterVersion, "exporterVersion");
        activeResourcePacks = List.copyOf(
                Objects.requireNonNull(activeResourcePacks, "activeResourcePacks"));
        loadedMods = List.copyOf(Objects.requireNonNull(loadedMods, "loadedMods"));
    }

    public Map<String, Object> asExtras() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("minecraftVersion", minecraftVersion);
        extras.put("loader", loaderName);
        extras.put("loaderVersion", loaderVersion);
        extras.put("exporterVersion", exporterVersion);
        extras.put("activeResourcePacks", activeResourcePacks);
        extras.put("loadedMods", loadedMods);
        return Map.copyOf(extras);
    }
}
