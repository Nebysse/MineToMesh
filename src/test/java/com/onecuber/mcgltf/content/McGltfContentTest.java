package com.onecuber.mcgltf.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class McGltfContentTest {
    @Test
    void registersWorkstationBlockEntries() {
        assertEquals("export_workstation",
                McGltfContent.EXPORT_WORKSTATION_BLOCK.getKey().location().getPath());
        assertEquals("export_workstation",
                McGltfContent.EXPORT_WORKSTATION_ITEM.getKey().location().getPath());
        assertEquals("export_workstation",
                McGltfContent.EXPORT_WORKSTATION_BLOCK_ENTITY.getKey().location().getPath());
    }

    @Test
    void registersMineToMeshCreativeTab() {
        assertEquals("mine_to_mesh",
                McGltfContent.CREATIVE_TAB.getKey().location().getPath());
    }
}
