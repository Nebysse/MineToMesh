package com.onecuber.mcgltf.command;

import com.onecuber.mcgltf.job.ExportProgress;
import com.onecuber.mcgltf.world.BlockPoint;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

public final class ClientMessages {
    private ClientMessages() {
    }

    public static Component pointSet(int index, BlockPoint point) {
        return Component.translatable("commands.mcgltf.pos_set",
                index, point.x(), point.y(), point.z(), point.dimension());
    }

    public static Component incompleteSelection() {
        return Component.translatable("commands.mcgltf.selection_incomplete")
                .withStyle(ChatFormatting.RED);
    }

    public static Component crossDimensionSelection() {
        return Component.translatable("commands.mcgltf.selection_cross_dimension")
                .withStyle(ChatFormatting.RED);
    }

    public static Component unsafeName(String reason) {
        return Component.translatable("commands.mcgltf.name_unsafe", reason)
                .withStyle(ChatFormatting.RED);
    }

    public static Component confirmation(long volume, String command) {
        Component clickable = Component.literal(command).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        return Component.translatable("commands.mcgltf.confirm_required", volume, clickable)
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component alreadyRunning() {
        return Component.translatable("commands.mcgltf.already_running")
                .withStyle(ChatFormatting.RED);
    }

    public static Component started(String name) {
        return Component.translatable("commands.mcgltf.started", name)
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component status(ExportProgress progress) {
        return Component.translatable("commands.mcgltf.status", CommandPolicy.formatStatus(progress));
    }

    public static Component idle() {
        return Component.translatable("commands.mcgltf.idle");
    }

    public static Component cancelled() {
        return Component.translatable("commands.mcgltf.cancelled")
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component completed(Path path, long warningCount) {
        return Component.translatable("commands.mcgltf.completed", path.toString(), warningCount)
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component failed(String reason) {
        return Component.translatable("commands.mcgltf.failed", reason)
                .withStyle(ChatFormatting.RED);
    }

    public static void send(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(component, false);
        }
    }
}
