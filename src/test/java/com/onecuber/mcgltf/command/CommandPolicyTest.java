package com.onecuber.mcgltf.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.onecuber.mcgltf.job.ExportJobManager;
import com.onecuber.mcgltf.job.ExportProgress;
import com.onecuber.mcgltf.job.JobState;
import com.onecuber.mcgltf.world.SelectionStore;
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
        McGltfCommands commands = new McGltfCommands(
                new SelectionStore(), new ExportJobManager(),
                (selection, name) -> { throw new AssertionError("parse must not create a job"); });
        commands.register(dispatcher);

        var parse = dispatcher.parse("mcgltf export \"城堡 夜景\" confirm", (CommandSourceStack) null);

        assertTrue(parse.getExceptions().isEmpty());
        assertTrue(parse.getContext().getNodes().stream()
                .anyMatch(node -> node.getNode().getName().equals("confirm")));
    }

    @Test
    void formatsRunningStatusWithStatePercentageQueueAndObject() {
        ExportProgress progress = new ExportProgress(
                JobState.CAPTURING, 25, 100, 2, Duration.ofSeconds(3), "section/0/4/0");

        String status = CommandPolicy.formatStatus(progress);

        assertTrue(status.contains("CAPTURING"));
        assertTrue(status.contains("25%"));
        assertTrue(status.contains("queue=2"));
        assertTrue(status.contains("section/0/4/0"));
    }
}
