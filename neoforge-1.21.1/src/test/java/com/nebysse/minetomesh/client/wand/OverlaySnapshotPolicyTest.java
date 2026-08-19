package com.nebysse.minetomesh.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nebysse.minetomesh.client.wand.HeldWandOverlaySource.Snapshot;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class OverlaySnapshotPolicyTest {
    @Test
    void deduplicatesEqualSnapshotsAndPreservesDifferentOrder() {
        Snapshot held = snapshot(0);
        Snapshot locked = snapshot(20);

        assertEquals(List.of(held), OverlaySnapshotPolicy.merge(
                Optional.of(held), Optional.of(held)));
        assertEquals(List.of(held, locked), OverlaySnapshotPolicy.merge(
                Optional.of(held), Optional.of(locked)));
        assertEquals(List.of(locked), OverlaySnapshotPolicy.merge(
                Optional.empty(), Optional.of(locked)));
        assertEquals(List.of(), OverlaySnapshotPolicy.merge(
                Optional.empty(), Optional.empty()));
    }

    private static Snapshot snapshot(int offset) {
        ResourceLocation dimension = ResourceLocation.parse("minecraft:overworld");
        BlockPos first = new BlockPos(offset, 64, offset);
        BlockPos second = new BlockPos(offset + 4, 70, offset + 4);
        Selection selection = Selection.of(
                new BlockPoint(dimension.toString(), first.getX(), first.getY(), first.getZ()),
                new BlockPoint(dimension.toString(), second.getX(), second.getY(), second.getZ()));
        return new Snapshot(Optional.of(first), Optional.of(second),
                Optional.of(selection), dimension);
    }
}
