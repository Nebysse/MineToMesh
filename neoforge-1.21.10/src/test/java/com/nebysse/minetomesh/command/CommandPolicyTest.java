package com.nebysse.minetomesh.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.nebysse.minetomesh.job.ExportJobManager;
import com.nebysse.minetomesh.job.ExportProgress;
import com.nebysse.minetomesh.job.ExportProgressSnapshot;
import com.nebysse.minetomesh.job.ExportStage;
import com.nebysse.minetomesh.job.JobState;
import com.nebysse.minetomesh.world.SelectionStore;
import java.time.Duration;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

class CommandPolicyTest {
    @Test
    void requiresConfirmationOnlyAboveTheSoftLimit() {
        assertFalse(CommandPolicy.requiresConfirmation(4_194_304L));
        assertTrue(CommandPolicy.requiresConfirmation(4_194_305L));
    }

    @Test
    void quotedUnicodeNameLeavesTrailingConfirmReachable() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        MineToMeshCommands commands = new MineToMeshCommands(
                new SelectionStore(), new ExportJobManager(),
                (selection, name) -> { throw new AssertionError("parse must not create a job"); });
        commands.register(dispatcher);

        var parse = dispatcher.parse("minetomesh export \"城堡 夜景\" confirm", (CommandSourceStack) null);

        assertTrue(parse.getExceptions().isEmpty());
        assertTrue(parse.getContext().getNodes().stream()
                .anyMatch(node -> node.getNode().getName().equals("confirm")));
    }

    @Test
    void formatsRunningStatusFromTheSharedTelemetrySnapshot() {
        ExportProgressSnapshot snapshot = new ExportProgressSnapshot(
                ExportStage.CAPTURING, 25, 1, 4, 2, 8,
                25, 100, 0, 0, 4, 2, 1, 2,
                "section/0/4/0", Duration.ofSeconds(3));
        ExportProgress progress = new ExportProgress(
                JobState.CAPTURING, 25, 100, 2,
                Duration.ofSeconds(3), "section/0/4/0", snapshot);

        String status = CommandPolicy.formatStatus(progress);

        assertTrue(status.contains("CAPTURING"));
        assertTrue(status.contains("25%"));
        assertTrue(status.contains("processingQueue=1"));
        assertTrue(status.contains("writingQueue=2"));
        assertTrue(status.contains("section/0/4/0"));
    }
}
