package com.nebysse.minetomesh.client;

import com.nebysse.minetomesh.MineToMeshInfo;
import com.nebysse.minetomesh.client.config.ClientExportSettings;
import com.nebysse.minetomesh.client.config.ClientExportSettingsStore;
import com.nebysse.minetomesh.client.selection.LockedSelectionService;
import com.nebysse.minetomesh.client.selection.LockedSelectionStore;
import com.nebysse.minetomesh.client.selection.WorldProfileKey;
import com.nebysse.minetomesh.client.wand.ExportWandController;
import com.nebysse.minetomesh.client.wand.ExportWandScreen;
import com.nebysse.minetomesh.client.wand.HeldWandOverlaySource;
import com.nebysse.minetomesh.client.wand.SelectionOverlayRenderer;
import com.nebysse.minetomesh.client.wand.WandClientInput;
import com.nebysse.minetomesh.command.ClientMessages;
import com.nebysse.minetomesh.command.MineToMeshCommands;
import com.nebysse.minetomesh.content.MineToMeshContent;
import com.nebysse.minetomesh.job.ExportJob;
import com.nebysse.minetomesh.job.ExportJobManager;
import com.nebysse.minetomesh.job.ManagedJob;
import com.nebysse.minetomesh.job.RollingCaptureSource;
import com.nebysse.minetomesh.job.DefaultExportPipeline;
import com.nebysse.minetomesh.network.BatchCaptureCompletedPayload;
import com.nebysse.minetomesh.network.BatchClientReadablePayload;
import com.nebysse.minetomesh.network.BatchLoadStartedPayload;
import com.nebysse.minetomesh.network.BatchReadyPayload;
import com.nebysse.minetomesh.network.CancelExportRequestPayload;
import com.nebysse.minetomesh.network.ExportCancelAcknowledgedPayload;
import com.nebysse.minetomesh.network.ExportClientCompletedPayload;
import com.nebysse.minetomesh.network.ExportProgressHeartbeatPayload;
import com.nebysse.minetomesh.network.ExportSessionAcceptedPayload;
import com.nebysse.minetomesh.network.ExportSessionFailedPayload;
import com.nebysse.minetomesh.network.ExportSessionFinishedPayload;
import com.nebysse.minetomesh.network.ExportSessionRejectedPayload;
import com.nebysse.minetomesh.network.WandClientReceiver;
import com.nebysse.minetomesh.wand.ExportWandMenu;
import com.nebysse.minetomesh.world.SelectionStore;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MineToMeshClient {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(MineToMeshInfo.MOD_ID);

    private final SelectionStore selectionStore = new SelectionStore();
    private final ExportJobManager jobManager = new ExportJobManager();
    private final WandClientInput wandInput = new WandClientInput();
    private final ExportWandController wandController;
    private final LockedSelectionService lockedSelectionService;
    private final SelectionOverlayRenderer overlayRenderer;
    private final MineToMeshCommands commands;
    private String activeDimension;
    private ManagedJob notifiedTerminalJob;

    public MineToMeshClient(
            ExportWandController.JobStarter wandJobStarter,
            MineToMeshCommands.ExportJobFactory commandJobFactory) {
        Objects.requireNonNull(wandJobStarter, "wandJobStarter");
        Objects.requireNonNull(commandJobFactory, "commandJobFactory");
        Path lockFile = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("minetomesh")
                .resolve("locked-selections.json");
        LockedSelectionStore lockedSelectionStore;
        try {
            lockedSelectionStore = LockedSelectionStore.open(lockFile);
        } catch (IOException exception) {
            LOGGER.error("Could not open persistent locked selections", exception);
            lockedSelectionStore = LockedSelectionStore.empty(lockFile);
        }
        lockedSelectionService = new LockedSelectionService(
                lockedSelectionStore, MineToMeshClient::currentWorldProfile);
        wandController = new ExportWandController(
                wandJobStarter, ExportWandController.fromManager(jobManager),
                new ExportWandController.SessionPacketSender() {
                    @Override
                    public void send(BatchClientReadablePayload payload) {
                        ClientPlayNetworking.send(payload);
                    }

                    @Override
                    public void send(BatchCaptureCompletedPayload payload) {
                        ClientPlayNetworking.send(payload);
                    }

                    @Override
                    public void send(ExportProgressHeartbeatPayload payload) {
                        ClientPlayNetworking.send(payload);
                    }

                    @Override
                    public void send(CancelExportRequestPayload payload) {
                        ClientPlayNetworking.send(payload);
                    }

                    @Override
                    public void send(ExportClientCompletedPayload payload) {
                        ClientPlayNetworking.send(payload);
                    }
                },
                chunk -> Minecraft.getInstance().level != null
                        && Minecraft.getInstance().level.hasChunk(chunk.x(), chunk.z()),
                (selection, name, options, telemetry) -> {
                    DefaultExportPipeline.RollingExport rollingExport =
                            DefaultExportPipeline.createRolling(
                                    Minecraft.getInstance(), selection, name,
                                    options, telemetry);
                    RollingCaptureSource source = rollingExport.source();
                    return new ExportWandController.RollingCapture() {
                        @Override
                        public ManagedJob job() {
                            return rollingExport.job();
                        }

                        @Override
                        public int enqueueBatch(List<ChunkCoordinate> chunks) {
                            return source.enqueueBatch(chunks);
                        }

                        @Override
                        public int capturedUnits() {
                            return source.capturedUnits();
                        }

                        @Override
                        public void finishInput() {
                            source.finishInput();
                        }
                    };
                });
        ClientExportSettings settings = new ClientExportSettingsStore(
                Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("config").resolve("minetomesh"),
                Runtime.getRuntime().availableProcessors()).load();
        wandController.setWorkerThreads(settings.workerThreads());
        overlayRenderer = new SelectionOverlayRenderer(
                new HeldWandOverlaySource(), lockedSelectionService);
        WandClientReceiver.install(wandController::accept, wandController::reject);
        WandClientReceiver.installSessionHandler(this::receiveSessionPayload);
        commands = new MineToMeshCommands(
                selectionStore, jobManager, commandJobFactory);
    }

    public void register() {
        MenuScreens.register(
                MineToMeshContent.EXPORT_WAND_MENU,
                (ExportWandMenu menu, net.minecraft.world.entity.player.Inventory inventory,
                        net.minecraft.network.chat.Component title) ->
                        new ExportWandScreen(menu, inventory, title,
                                wandController, lockedSelectionService));
        wandInput.register();
        overlayRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, minecraft) -> onLoggingOut());
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, context) -> commands.register(dispatcher));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath(
                                MineToMeshInfo.MOD_ID, "cancel_export_on_reload");
                    }

                    @Override
                    public void onResourceManagerReload(
                            ResourceManager resourceManager) {
                        jobManager.cancel("resource_reload");
                    }
                });
    }

    private void receiveSessionPayload(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (payload instanceof ExportSessionAcceptedPayload value) {
            wandController.sessionAccepted(value);
        } else if (payload instanceof ExportSessionRejectedPayload value) {
            wandController.sessionRejected(value);
        } else if (payload instanceof BatchLoadStartedPayload value) {
            wandController.batchLoadStarted(value);
        } else if (payload instanceof BatchReadyPayload value) {
            wandController.batchReady(value);
        } else if (payload instanceof ExportCancelAcknowledgedPayload value) {
            wandController.cancelAcknowledged(value);
        } else if (payload instanceof ExportSessionFinishedPayload value) {
            wandController.sessionFinished(value);
        } else if (payload instanceof ExportSessionFailedPayload value) {
            wandController.sessionFailed(value);
        }
    }

    private static Optional<WorldProfileKey> currentWorldProfile() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null) {
            try {
                return Optional.of(WorldProfileKey.multiplayer(
                        minecraft.getCurrentServer().ip));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        if (minecraft.getSingleplayerServer() != null) {
            Path worldRoot = minecraft.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT);
            return Optional.of(WorldProfileKey.singleplayer(worldRoot));
        }
        return Optional.empty();
    }

    private void onClientTick(Minecraft minecraft) {
        wandInput.tick(minecraft);
        if (minecraft.level != null) {
            String dimension = minecraft.level.dimension().identifier().toString();
            if (activeDimension != null && !activeDimension.equals(dimension)) {
                selectionStore.clear();
                jobManager.cancel("dimension_change");
            }
            activeDimension = dimension;
        }
        jobManager.tick();
        wandController.tick();
        notifyTerminalResult();
    }

    private void notifyTerminalResult() {
        ManagedJob job = jobManager.activeJob().orElse(null);
        if (job == null || !job.isTerminal() || job == notifiedTerminalJob) {
            return;
        }
        notifiedTerminalJob = job;
        if (job instanceof ExportJob exportJob) {
            if (exportJob.state() == com.nebysse.minetomesh.job.JobState.COMPLETED
                    && exportJob.finalDirectory().isPresent()) {
                ClientMessages.send(ClientMessages.completed(
                        exportJob.finalDirectory().orElseThrow(),
                        exportJob.warningCount()));
            } else if (exportJob.state()
                    == com.nebysse.minetomesh.job.JobState.FAILED) {
                ClientMessages.send(ClientMessages.failed(
                        exportJob.failureReason()
                                .orElse("Unknown export failure")));
            }
        }
    }

    private void onLoggingOut() {
        selectionStore.clear();
        activeDimension = null;
        jobManager.cancel("logout");
        wandController.unbind();
    }
}
