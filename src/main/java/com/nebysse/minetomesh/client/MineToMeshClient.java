package com.nebysse.minetomesh.client;

import com.nebysse.minetomesh.MineToMesh;
import com.nebysse.minetomesh.client.selection.LockedSelectionService;
import com.nebysse.minetomesh.client.selection.LockedSelectionStore;
import com.nebysse.minetomesh.client.selection.WorldProfileKey;
import com.nebysse.minetomesh.client.wand.ExportWandController;
import com.nebysse.minetomesh.client.wand.HeldWandOverlaySource;
import com.nebysse.minetomesh.client.wand.SelectionOverlayRenderer;
import com.nebysse.minetomesh.client.wand.ExportWandScreen;
import com.nebysse.minetomesh.client.wand.WandClientInput;
import com.nebysse.minetomesh.command.ClientMessages;
import com.nebysse.minetomesh.command.MineToMeshCommands;
import com.nebysse.minetomesh.content.MineToMeshContent;
import com.nebysse.minetomesh.job.DefaultExportPipeline;
import com.nebysse.minetomesh.job.ExportJob;
import com.nebysse.minetomesh.job.ExportJobManager;
import com.nebysse.minetomesh.job.ManagedJob;
import com.nebysse.minetomesh.network.WandClientReceiver;
import com.nebysse.minetomesh.wand.ExportWandMenu;
import com.nebysse.minetomesh.world.SelectionStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = MineToMesh.MOD_ID, dist = Dist.CLIENT)
public final class MineToMeshClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MineToMesh.MOD_ID);

    private final SelectionStore selectionStore = new SelectionStore();
    private final ExportJobManager jobManager = new ExportJobManager();
    private final WandClientInput wandInput = new WandClientInput();
    private final ExportWandController wandController;
    private final LockedSelectionService lockedSelectionService;
    private final SelectionOverlayRenderer overlayRenderer;
    private final MineToMeshCommands commands;
    private String activeDimension;
    private ManagedJob notifiedTerminalJob;

    public MineToMeshClient(IEventBus modBus) {
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
                (selection, name, options, telemetry) -> DefaultExportPipeline.create(
                        Minecraft.getInstance(), selection, name, options, telemetry),
                ExportWandController.fromManager(jobManager));
        overlayRenderer = new SelectionOverlayRenderer(
                new HeldWandOverlaySource(), lockedSelectionService);
        WandClientReceiver.install(wandController::accept, wandController::reject);
        commands = new MineToMeshCommands(selectionStore, jobManager,
                (selection, name) -> DefaultExportPipeline.create(
                        Minecraft.getInstance(), selection, name));
        NeoForge.EVENT_BUS.addListener(commands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(wandInput::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(overlayRenderer::onRenderLevel);
        modBus.addListener(this::onRegisterReloadListeners);
        modBus.addListener(this::onRegisterMenuScreens);
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

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        wandInput.tick(minecraft);
        if (minecraft.level != null) {
            String dimension = minecraft.level.dimension().location().toString();
            if (activeDimension != null && !activeDimension.equals(dimension)) {
                selectionStore.clear();
                jobManager.cancel("dimension_change");
            }
            activeDimension = dimension;
        }
        jobManager.tick();
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
            } else if (exportJob.state() == com.nebysse.minetomesh.job.JobState.FAILED) {
                ClientMessages.send(ClientMessages.failed(
                        exportJob.failureReason().orElse("Unknown export failure")));
            }
        }
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MineToMeshContent.EXPORT_WAND_MENU.get(),
                new MenuScreens.ScreenConstructor<ExportWandMenu, ExportWandScreen>() {
                    @Override
                    public ExportWandScreen create(
                            ExportWandMenu menu,
                            Inventory inventory,
                            Component title) {
                        return new ExportWandScreen(
                                menu, inventory, title, wandController,
                                lockedSelectionService);
                    }
                });
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        selectionStore.clear();
        activeDimension = null;
        jobManager.cancel("logout");
        wandController.unbind();
    }

    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                jobManager.cancel("resource_reload"));
    }
}
