package com.nebysse.minetomesh.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ExportEnvironmentTest {
    @Test
    void copiesListsAndExposesStableMetadataKeys() {
        List<String> packs = new ArrayList<>(List.of("vanilla"));
        List<String> mods = new ArrayList<>(List.of("fabricloader@0.19.3"));
        ExportEnvironment environment = new ExportEnvironment(
                "26.2", "fabric", "0.19.3",
                "1.2.0-fabric-alpha.1", packs, mods);

        packs.add("late-pack");
        mods.add("late-mod");

        assertEquals(List.of("vanilla"), environment.activeResourcePacks());
        assertEquals(List.of("fabricloader@0.19.3"), environment.loadedMods());
        assertEquals("26.2", environment.asExtras().get("minecraftVersion"));
        assertEquals("fabric", environment.asExtras().get("loader"));
        assertEquals("0.19.3", environment.asExtras().get("loaderVersion"));
        assertEquals("1.2.0-fabric-alpha.1", environment.asExtras().get("exporterVersion"));
    }
}
