package com.onecuber.mcgltf.client.wand;

import com.onecuber.mcgltf.content.McGltfContent;
import com.onecuber.mcgltf.wand.ExportWandSelection;
import com.onecuber.mcgltf.world.Selection;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class HeldWandOverlaySource {
    public record Snapshot(
            Optional<BlockPos> pos1,
            Optional<BlockPos> pos2,
            Optional<Selection> selection,
            ResourceLocation dimension) {
        public Snapshot {
            pos1 = Objects.requireNonNull(pos1, "pos1");
            pos2 = Objects.requireNonNull(pos2, "pos2");
            selection = Objects.requireNonNull(selection, "selection");
            dimension = Objects.requireNonNull(dimension, "dimension");
        }
    }

    public Optional<Snapshot> resolveSnapshot(
            ItemStack mainHand,
            ItemStack offHand,
            ResourceLocation currentDimension) {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(currentDimension, "currentDimension");
        Optional<Snapshot> main = resolveStackSnapshot(mainHand, currentDimension);
        return main.isPresent() ? main : resolveStackSnapshot(offHand, currentDimension);
    }

    public Optional<Selection> resolve(
            ItemStack mainHand,
            ItemStack offHand,
            ResourceLocation currentDimension) {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(currentDimension, "currentDimension");
        Optional<Selection> main = resolveStack(mainHand, currentDimension);
        return main.isPresent() ? main : resolveStack(offHand, currentDimension);
    }

    Optional<Selection> resolveSelections(
            Optional<ExportWandSelection> mainHand,
            Optional<ExportWandSelection> offHand,
            ResourceLocation currentDimension) {
        Optional<Selection> main = mainHand.flatMap(
                value -> resolveSelection(value, currentDimension));
        return main.isPresent() ? main : offHand.flatMap(
                value -> resolveSelection(value, currentDimension));
    }

    private static Optional<Snapshot> resolveStackSnapshot(
            ItemStack stack, ResourceLocation currentDimension) {
        if (!stack.is(McGltfContent.EXPORT_WAND_ITEM.get())) {
            return Optional.empty();
        }
        ExportWandSelection value = stack.get(
                McGltfContent.EXPORT_WAND_SELECTION.get());
        return value == null ? Optional.empty() : snapshot(value, currentDimension);
    }

    private static Optional<Selection> resolveStack(
            ItemStack stack, ResourceLocation currentDimension) {
        if (!stack.is(McGltfContent.EXPORT_WAND_ITEM.get())) {
            return Optional.empty();
        }
        ExportWandSelection value = stack.get(
                McGltfContent.EXPORT_WAND_SELECTION.get());
        return value == null
                ? Optional.empty()
                : resolveSelection(value, currentDimension);
    }

    private static Optional<Snapshot> snapshot(
            ExportWandSelection value, ResourceLocation currentDimension) {
        if (!value.overlayEnabled()
                || value.selectionDimension().filter(currentDimension::equals).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(value.pos1(), value.pos2(),
                value.toSelection(), currentDimension));
    }

    private static Optional<Selection> resolveSelection(
            ExportWandSelection value, ResourceLocation currentDimension) {
        if (!value.overlayEnabled()
                || value.selectionDimension().filter(currentDimension::equals).isEmpty()) {
            return Optional.empty();
        }
        return value.toSelection();
    }
}
