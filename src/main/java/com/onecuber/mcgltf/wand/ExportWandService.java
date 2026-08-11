package com.onecuber.mcgltf.wand;

import com.onecuber.mcgltf.content.McGltfContent;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class ExportWandService {
    public static final ExportWandService INSTANCE = new ExportWandService();

    public enum Result {
        SUCCESS_POS1,
        SUCCESS_POS2,
        CLEARED,
        UPDATED,
        WRONG_DIMENSION,
        OUT_OF_BUILD_HEIGHT,
        INVALID_WAND,
        INVALID_BINDING
    }

    public record Feedback(SoundEvent sound, float volume, float pitch) {
        public Feedback {
            Objects.requireNonNull(sound, "sound");
        }
    }

    private final Supplier<UUID> uuidSupplier;

    public ExportWandService() {
        this(UUID::randomUUID);
    }

    ExportWandService(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
    }

    public ExportWandSelection selection(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!isWand(stack)) {
            return ExportWandSelection.empty();
        }
        return stack.getOrDefault(
                McGltfContent.EXPORT_WAND_SELECTION.get(),
                ExportWandSelection.empty());
    }

    public Result ensureIdentity(ItemStack stack) {
        if (!isWand(stack)) {
            return Result.INVALID_WAND;
        }
        ExportWandSelection current = selection(stack);
        if (current.wandId().isPresent()) {
            return Result.UPDATED;
        }
        stack.set(McGltfContent.EXPORT_WAND_SELECTION.get(),
                current.ensureIdentity(uuidSupplier.get()));
        return Result.UPDATED;
    }

    public Result setEndpoint(
            ItemStack stack, ResourceLocation dimension, Endpoint endpoint,
            BlockPos position, int minBuildHeight, int maxBuildHeight) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(position, "position");
        if (!isWand(stack)) {
            return Result.INVALID_WAND;
        }
        if (position.getY() < minBuildHeight || position.getY() >= maxBuildHeight) {
            return Result.OUT_OF_BUILD_HEIGHT;
        }
        ExportWandSelection current = selection(stack);
        if (current.selectionDimension().isPresent()
                && !current.selectionDimension().orElseThrow().equals(dimension)) {
            return Result.WRONG_DIMENSION;
        }
        ExportWandSelection candidate = ensureIdentity(current)
                .setEndpoint(dimension, endpoint, position);
        stack.set(McGltfContent.EXPORT_WAND_SELECTION.get(), candidate);
        return endpoint == Endpoint.POS1 ? Result.SUCCESS_POS1 : Result.SUCCESS_POS2;
    }

    public Result clearSelection(ItemStack stack) {
        if (!isWand(stack)) {
            return Result.INVALID_WAND;
        }
        ExportWandSelection candidate = ensureIdentity(selection(stack))
                .clearSelection();
        stack.set(McGltfContent.EXPORT_WAND_SELECTION.get(), candidate);
        return Result.CLEARED;
    }

    public Result setOverlayEnabled(ItemStack stack, boolean enabled) {
        if (!isWand(stack)) {
            return Result.INVALID_WAND;
        }
        ExportWandSelection candidate = ensureIdentity(selection(stack))
                .withOverlayEnabled(enabled);
        stack.set(McGltfContent.EXPORT_WAND_SELECTION.get(), candidate);
        return Result.UPDATED;
    }

    public Result setExportName(ItemStack stack, String exportName) {
        Objects.requireNonNull(exportName, "exportName");
        if (!isWand(stack)) {
            return Result.INVALID_WAND;
        }
        ExportWandSelection candidate = ensureIdentity(selection(stack))
                .withExportName(exportName);
        stack.set(McGltfContent.EXPORT_WAND_SELECTION.get(), candidate);
        return Result.UPDATED;
    }

    public Feedback feedbackFor(Result result) {
        Objects.requireNonNull(result, "result");
        return switch (result) {
            case SUCCESS_POS1 -> new Feedback(
                    SoundEvents.NOTE_BLOCK_HAT.value(), 0.6F, 0.75F);
            case SUCCESS_POS2 -> new Feedback(
                    SoundEvents.NOTE_BLOCK_HAT.value(), 0.6F, 1.25F);
            case CLEARED -> new Feedback(
                    SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.0F);
            case UPDATED -> new Feedback(
                    SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.1F);
            default -> new Feedback(
                    SoundEvents.VILLAGER_NO, 0.6F, 1.0F);
        };
    }

    public void playFeedback(ServerPlayer player, Result result) {
        Objects.requireNonNull(player, "player");
        Feedback feedback = feedbackFor(result);
        player.playNotifySound(feedback.sound(), SoundSource.PLAYERS,
                feedback.volume(), feedback.pitch());
    }

    private ExportWandSelection ensureIdentity(ExportWandSelection selection) {
        return selection.wandId().isPresent()
                ? selection : selection.ensureIdentity(uuidSupplier.get());
    }

    private static boolean isWand(ItemStack stack) {
        return stack != null && stack.is(McGltfContent.EXPORT_WAND_ITEM.get());
    }
}
