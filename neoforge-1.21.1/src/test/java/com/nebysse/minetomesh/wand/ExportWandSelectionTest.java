package com.nebysse.minetomesh.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ExportWandSelectionTest {
    private static final UUID WAND_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER =
            ResourceLocation.parse("minecraft:the_nether");

    @Test
    void emptySelectionUsesApprovedDefaults() {
        ExportWandSelection empty = ExportWandSelection.empty();
        assertTrue(empty.wandId().isEmpty());
        assertTrue(empty.selectionDimension().isEmpty());
        assertTrue(empty.pos1().isEmpty());
        assertTrue(empty.pos2().isEmpty());
        assertTrue(empty.overlayEnabled());
        assertFalse(empty.includePlayers());
        assertEquals(4, empty.batchChunkCount());
        assertEquals("export", empty.exportName());
        assertFalse(empty.isComplete());
        assertTrue(empty.toSelection().isEmpty());
    }

    @Test
    void firstEndpointBindsDimensionAndClearPreservesPreferences() {
        ExportWandSelection initial = ExportWandSelection.empty()
                .ensureIdentity(WAND_ID)
                .withOverlayEnabled(false)
                .withIncludePlayers(true)
                .withBatchChunkCount(8)
                .withExportName("flower_factory");
        ExportWandSelection selected = initial.setEndpoint(
                OVERWORLD, Endpoint.POS1, new BlockPos(1, 64, 2));
        assertEquals(Optional.of(OVERWORLD), selected.selectionDimension());
        assertEquals(Optional.of(new BlockPos(1, 64, 2)), selected.pos1());

        ExportWandSelection cleared = selected.clearSelection();
        assertEquals(Optional.of(WAND_ID), cleared.wandId());
        assertTrue(cleared.selectionDimension().isEmpty());
        assertTrue(cleared.pos1().isEmpty());
        assertTrue(cleared.pos2().isEmpty());
        assertFalse(cleared.overlayEnabled());
        assertTrue(cleared.includePlayers());
        assertEquals(8, cleared.batchChunkCount());
        assertEquals("flower_factory", cleared.exportName());
    }

    @Test
    void completeSelectionConvertsToNormalizedWorldSelection() {
        ExportWandSelection selected = ExportWandSelection.empty()
                .ensureIdentity(WAND_ID)
                .setEndpoint(OVERWORLD, Endpoint.POS1, new BlockPos(4, 70, 8))
                .setEndpoint(OVERWORLD, Endpoint.POS2, new BlockPos(-2, 64, 1));
        assertTrue(selected.isComplete());
        assertEquals(Optional.of(Selection.of(
                new BlockPoint("minecraft:overworld", 4, 70, 8),
                new BlockPoint("minecraft:overworld", -2, 64, 1))),
                selected.toSelection());
    }

    @Test
    void crossDimensionEndpointIsRejectedWithoutMutation() {
        ExportWandSelection selected = ExportWandSelection.empty()
                .ensureIdentity(WAND_ID)
                .setEndpoint(OVERWORLD, Endpoint.POS1, BlockPos.ZERO);
        assertThrows(IllegalArgumentException.class, () -> selected.setEndpoint(
                NETHER, Endpoint.POS2, new BlockPos(2, 70, 2)));
        assertEquals(Optional.of(OVERWORLD), selected.selectionDimension());
        assertTrue(selected.pos2().isEmpty());
    }

    @Test
    void codecRoundTripsAllPersistentFields() {
        ExportWandSelection original = ExportWandSelection.empty()
                .ensureIdentity(WAND_ID)
                .setEndpoint(OVERWORLD, Endpoint.POS1, new BlockPos(-10, 5, 20))
                .setEndpoint(OVERWORLD, Endpoint.POS2, new BlockPos(30, 90, -40))
                .withOverlayEnabled(false)
                .withIncludePlayers(true)
                .withBatchChunkCount(16)
                .withExportName("codec_test");
        JsonElement encoded = ExportWandSelection.CODEC
                .encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        ExportWandSelection decoded = ExportWandSelection.CODEC
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, decoded);
    }

    @Test
    void streamCodecRoundTripsAllSynchronizedFields() {
        ExportWandSelection original = ExportWandSelection.empty()
                .ensureIdentity(WAND_ID)
                .setEndpoint(OVERWORLD, Endpoint.POS1, new BlockPos(1, 2, 3))
                .setEndpoint(OVERWORLD, Endpoint.POS2, new BlockPos(4, 5, 6))
                .withOverlayEnabled(false)
                .withIncludePlayers(true)
                .withBatchChunkCount(1)
                .withExportName("network_test");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ExportWandSelection.STREAM_CODEC.encode(buffer, original);
        buffer.readerIndex(0);
        assertEquals(original, ExportWandSelection.STREAM_CODEC.decode(buffer));
    }

    @Test
    void codecDefaultsLegacyFields() {
        ExportWandSelection decoded = ExportWandSelection.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .getOrThrow();
        assertFalse(decoded.includePlayers());
        assertEquals(4, decoded.batchChunkCount());
    }

    @Test
    void constructorRejectsEndpointsWithoutDimension() {
        assertThrows(IllegalArgumentException.class, () -> new ExportWandSelection(
                Optional.of(WAND_ID), Optional.empty(), Optional.of(BlockPos.ZERO),
                Optional.empty(), true, false, 4, "export"));
        assertThrows(IllegalArgumentException.class,
                () -> ExportWandSelection.empty().withBatchChunkCount(17));
    }
}
