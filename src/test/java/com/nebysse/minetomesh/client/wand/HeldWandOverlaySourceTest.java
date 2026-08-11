package com.nebysse.minetomesh.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.wand.Endpoint;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import com.nebysse.minetomesh.world.Selection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class HeldWandOverlaySourceTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER =
            ResourceLocation.parse("minecraft:the_nether");
    private final HeldWandOverlaySource source = new HeldWandOverlaySource();

    @Test
    void mainHandHasPriorityAndOffhandIsTheFallback() {
        ExportWandSelection main = complete(OVERWORLD, 0);
        ExportWandSelection off = complete(OVERWORLD, 20);
        assertEquals(main.toSelection(), source.resolveSelections(
                Optional.of(main), Optional.of(off), OVERWORLD));
        assertEquals(off.toSelection(), source.resolveSelections(
                Optional.empty(), Optional.of(off), OVERWORLD));
    }

    @Test
    void hiddenIncompleteAndWrongDimensionSelectionsDoNotRender() {
        ExportWandSelection hidden = complete(OVERWORLD, 0)
                .withOverlayEnabled(false);
        ExportWandSelection incomplete = ExportWandSelection.empty()
                .ensureIdentity(UUID.randomUUID())
                .setEndpoint(OVERWORLD, Endpoint.POS1, BlockPos.ZERO);
        assertTrue(source.resolveSelections(
                Optional.of(hidden), Optional.empty(), OVERWORLD).isEmpty());
        assertTrue(source.resolveSelections(
                Optional.of(incomplete), Optional.empty(), OVERWORLD).isEmpty());
        assertTrue(source.resolveSelections(
                Optional.of(complete(OVERWORLD, 0)), Optional.empty(), NETHER).isEmpty());
    }

    @Test
    void switchingAwayHidesAndRestoringSameWandRestoresItsSelection() {
        ExportWandSelection wand = complete(OVERWORLD, 4);
        Optional<Selection> visible = source.resolveSelections(
                Optional.of(wand), Optional.empty(), OVERWORLD);
        assertTrue(visible.isPresent());
        assertTrue(source.resolveSelections(
                Optional.empty(), Optional.empty(), OVERWORLD).isEmpty());
        assertEquals(visible, source.resolveSelections(
                Optional.of(wand), Optional.empty(), OVERWORLD));
    }

    private static ExportWandSelection complete(
            ResourceLocation dimension, int offset) {
        return ExportWandSelection.empty()
                .ensureIdentity(UUID.randomUUID())
                .setEndpoint(dimension, Endpoint.POS1,
                        new BlockPos(offset, 64, offset))
                .setEndpoint(dimension, Endpoint.POS2,
                        new BlockPos(offset + 3, 70, offset + 5));
    }
}
