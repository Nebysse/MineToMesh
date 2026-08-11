package com.onecuber.mcgltf.client.wand;

import com.onecuber.mcgltf.content.McGltfContent;
import com.onecuber.mcgltf.wand.ExportWandSelection;
import com.onecuber.mcgltf.world.Selection;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class HeldWandOverlaySource {
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

    private static Optional<Selection> resolveSelection(
            ExportWandSelection value, ResourceLocation currentDimension) {
        if (!value.overlayEnabled()
                || value.selectionDimension().filter(currentDimension::equals).isEmpty()) {
            return Optional.empty();
        }
        return value.toSelection();
    }
}
