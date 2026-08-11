package com.nebysse.minetomesh.command;

import com.nebysse.minetomesh.job.ExportProgress;
import com.nebysse.minetomesh.world.BlockPoint;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

public final class ClientMessages {
    private ClientMessages() {
    }

    public static Component pointSet(int index, BlockPoint point) {
        return Component.translatable("commands.minetomesh.pos_set",
                index, point.x(), point.y(), point.z(), point.dimension());
    }

    public static Component incompleteSelection() {
        return Component.translatable("commands.minetomesh.selection_incomplete")
                .withStyle(ChatFormatting.RED);
    }

    public static Component crossDimensionSelection() {
        return Component.translatable("commands.minetomesh.selection_cross_dimension")
                .withStyle(ChatFormatting.RED);
    }

    public static Component unsafeName(String reason) {
        return Component.translatable("commands.minetomesh.name_unsafe", reason)
                .withStyle(ChatFormatting.RED);
    }

    public static Component confirmation(long volume, String command) {
        Component clickable = Component.literal(command).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        return Component.translatable("commands.minetomesh.confirm_required", volume, clickable)
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component alreadyRunning() {
        return Component.translatable("commands.minetomesh.already_running")
                .withStyle(ChatFormatting.RED);
    }

    public static Component started(String name) {
        return Component.translatable("commands.minetomesh.started", name)
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component status(ExportProgress progress) {
        return Component.translatable("commands.minetomesh.status", CommandPolicy.formatStatus(progress));
    }

    public static Component idle() {
        return Component.translatable("commands.minetomesh.idle");
    }

    public static Component cancelled() {
        return Component.translatable("commands.minetomesh.cancelled")
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component completed(Path path, long warningCount) {
        return Component.translatable("commands.minetomesh.completed", path.toString(), warningCount)
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component failed(String reason) {
        return Component.translatable("commands.minetomesh.failed", reason)
                .withStyle(ChatFormatting.RED);
    }

    public static void send(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(component, false);
        }
    }
}
