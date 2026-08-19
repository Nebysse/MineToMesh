package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.wand.Endpoint;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class WandRequestPolicyTest {
    private static final UUID WAND_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Identifier OVERWORLD =
            Identifier.parse("minecraft:overworld");

    @Test
    void rejectsIncompleteAndOutOfRangeSelections() {
        ExportWandSelection incomplete = ExportWandSelection.empty()
                .ensureIdentity(WAND_ID)
                .setEndpoint(OVERWORLD, Endpoint.POS1, BlockPos.ZERO);
        assertFalse(WandRequestPolicy
                .validateSelection(incomplete, -64, 320).accepted());
        ExportWandSelection outOfRange = incomplete.setEndpoint(
                OVERWORLD, Endpoint.POS2, new BlockPos(1, 320, 1));
        WandRequestPolicy.Validation validation = WandRequestPolicy
                .validateSelection(outOfRange, -64, 320);
        assertFalse(validation.accepted());
        assertEquals("minetomesh.error.wand.out_of_bounds", validation.reasonKey());
    }

    @Test
    void validatesExportNameAndPermissions() {
        assertFalse(WandRequestPolicy.validateExportName("..").accepted());
        assertFalse(WandRequestPolicy.validateExportName("x".repeat(65)).accepted());
        assertTrue(WandRequestPolicy.validateExportName("flower_factory").accepted());
        assertTrue(WandRequestPolicy.validateExportPermission(true, false).accepted());
        assertFalse(WandRequestPolicy.validateExportPermission(false, false).accepted());
        assertTrue(WandRequestPolicy.validateExportPermission(false, true).accepted());
    }

    @Test
    void acceptsCompleteSelectionAtInclusiveMinimumAndExclusiveMaximum() {
        ExportWandSelection selection = ExportWandSelection.empty()
                .ensureIdentity(WAND_ID)
                .setEndpoint(OVERWORLD, Endpoint.POS1, new BlockPos(0, -64, 0))
                .setEndpoint(OVERWORLD, Endpoint.POS2, new BlockPos(1, 319, 1));
        assertTrue(WandRequestPolicy
                .validateSelection(selection, -64, 320).accepted());
    }
}
