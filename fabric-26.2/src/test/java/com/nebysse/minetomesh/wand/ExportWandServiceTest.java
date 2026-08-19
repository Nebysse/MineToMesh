package com.nebysse.minetomesh.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.content.MineToMeshContent;
import net.minecraft.SharedConstants;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires Fabric game bootstrap; covered by dedicated-server smoke")
class ExportWandServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MineToMeshContent.register();
    }

    private static final UUID WAND_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Identifier OVERWORLD =
            Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER =
            Identifier.parse("minecraft:the_nether");

    private ExportWandService service;
    private ItemStack wand;

    @BeforeEach
    void setUp() {
        service = new ExportWandService(() -> WAND_ID);
        wand = new ItemStack(MineToMeshContent.EXPORT_WAND_ITEM);
    }

    @Test
    void firstSuccessfulMutationInitializesIdentity() {
        assertEquals(ExportWandService.Result.SUCCESS_POS1,
                service.setEndpoint(wand, OVERWORLD, Endpoint.POS1,
                        new BlockPos(1, 64, 2), -64, 320));
        ExportWandSelection selection = service.selection(wand);
        assertEquals(WAND_ID, selection.wandId().orElseThrow());
        assertEquals(new BlockPos(1, 64, 2), selection.pos1().orElseThrow());
    }

    @Test
    void crossDimensionRequestLeavesStackUnchanged() {
        service.setEndpoint(wand, OVERWORLD, Endpoint.POS1,
                BlockPos.ZERO, -64, 320);
        ExportWandSelection before = service.selection(wand);
        assertEquals(ExportWandService.Result.WRONG_DIMENSION,
                service.setEndpoint(wand, NETHER, Endpoint.POS2,
                        new BlockPos(1, 64, 1), -64, 320));
        assertEquals(before, service.selection(wand));
    }

    @Test
    void outOfBuildHeightRequestDoesNotInitializeOrMutate() {
        assertEquals(ExportWandService.Result.OUT_OF_BUILD_HEIGHT,
                service.setEndpoint(wand, OVERWORLD, Endpoint.POS1,
                        new BlockPos(0, 320, 0), -64, 320));
        assertEquals(ExportWandSelection.empty(), service.selection(wand));
    }

    @Test
    void clearPreservesIdentityOverlayAndExportName() {
        service.setEndpoint(wand, OVERWORLD, Endpoint.POS1,
                BlockPos.ZERO, -64, 320);
        service.setOverlayEnabled(wand, false);
        service.setExportName(wand, "flower_factory");
        assertEquals(ExportWandService.Result.CLEARED, service.clearSelection(wand));
        ExportWandSelection cleared = service.selection(wand);
        assertEquals(WAND_ID, cleared.wandId().orElseThrow());
        assertTrue(cleared.selectionDimension().isEmpty());
        assertTrue(cleared.pos1().isEmpty());
        assertTrue(cleared.pos2().isEmpty());
        assertFalse(cleared.overlayEnabled());
        assertEquals("flower_factory", cleared.exportName());
    }

    @Test
    void feedbackUsesApprovedSoundsAndDistinctEndpointPitches() {
        ExportWandService.Feedback pos1 = service.feedbackFor(
                ExportWandService.Result.SUCCESS_POS1);
        ExportWandService.Feedback pos2 = service.feedbackFor(
                ExportWandService.Result.SUCCESS_POS2);
        assertEquals(SoundEvents.NOTE_BLOCK_HAT.value(), pos1.sound());
        assertEquals(SoundEvents.NOTE_BLOCK_HAT.value(), pos2.sound());
        assertEquals(0.6F, pos1.volume());
        assertEquals(0.75F, pos1.pitch());
        assertEquals(1.25F, pos2.pitch());
        assertEquals(SoundEvents.BEACON_DEACTIVATE,
                service.feedbackFor(ExportWandService.Result.CLEARED).sound());
        assertEquals(SoundEvents.VILLAGER_NO,
                service.feedbackFor(ExportWandService.Result.WRONG_DIMENSION).sound());
        assertEquals(SoundEvents.BOOK_PAGE_TURN,
                service.feedbackFor(ExportWandService.Result.UPDATED).sound());
    }

    @Test
    void rejectsNonWandStacksWithoutMutation() {
        ItemStack stick = new ItemStack(Items.STICK);
        assertEquals(ExportWandService.Result.INVALID_WAND,
                service.clearSelection(stick));
        assertTrue(stick.getComponentsPatch().isEmpty());
    }
}
