package com.onecuber.mcgltf.client;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.client.workstation.ExportWorkstationScreen;
import com.onecuber.mcgltf.client.workstation.OverlayKey;
import com.onecuber.mcgltf.client.workstation.SelectionOverlayRenderer;
import com.onecuber.mcgltf.client.workstation.SelectionOverlayState;
import com.onecuber.mcgltf.client.workstation.WorkstationExportController;
import com.onecuber.mcgltf.client.wand.ExportWandController;
import com.onecuber.mcgltf.client.wand.ExportWandScreen;
import com.onecuber.mcgltf.client.wand.WandClientInput;
import com.onecuber.mcgltf.command.ClientMessages;
import com.onecuber.mcgltf.command.McGltfCommands;
import com.onecuber.mcgltf.content.McGltfContent;
import com.onecuber.mcgltf.job.DefaultExportPipeline;
import com.onecuber.mcgltf.job.ExportJob;
import com.onecuber.mcgltf.job.ExportJobManager;
import com.onecuber.mcgltf.job.ManagedJob;
import com.onecuber.mcgltf.network.WandClientReceiver;
import com.onecuber.mcgltf.network.WorkstationClientReceiver;
import com.onecuber.mcgltf.wand.ExportWandMenu;
import com.onecuber.mcgltf.workstation.ExportWorkstationMenu;
import com.onecuber.mcgltf.world.SelectionStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = McGltf.MOD_ID, dist = Dist.CLIENT)
public final class McGltfClient {
    private final SelectionStore selectionStore = new SelectionStore();
    private final ExportJobManager jobManager = new ExportJobManager();
    private final WandClientInput wandInput = new WandClientInput();
    private final WorkstationExportController workstationController;
    private final ExportWandController wandController;
    private final SelectionOverlayState overlayState = new SelectionOverlayState();
    private final SelectionOverlayRenderer overlayRenderer;
    private final McGltfCommands commands;
    private String activeDimension;
    private ManagedJob notifiedTerminalJob;

    public McGltfClient(IEventBus modBus) {
        workstationController = new WorkstationExportController(
                (coordinates, dimension, name, telemetry) -> DefaultExportPipeline.create(
                        Minecraft.getInstance(),
                        coordinates.toSelection(dimension),
                        name,
                        telemetry),
                WorkstationExportController.fromManager(jobManager));
        wandController = new ExportWandController(
                (selection, name, telemetry) -> DefaultExportPipeline.create(
                        Minecraft.getInstance(), selection, name, telemetry),
                ExportWandController.fromManager(jobManager));
        overlayRenderer = new SelectionOverlayRenderer(overlayState);
        WorkstationClientReceiver.install(
                workstationController::accept, workstationController::reject);
        WandClientReceiver.install(wandController::accept, wandController::reject);
        commands = new McGltfCommands(selectionStore, jobManager,
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

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        wandInput.tick(minecraft);
        if (minecraft.level != null) {
            String dimension = minecraft.level.dimension().location().toString();
            if (activeDimension != null && !activeDimension.equals(dimension)) {
                selectionStore.clear();
                overlayState.dimensionChanged(dimension);
                jobManager.cancel("dimension_change");
            }
            activeDimension = dimension;
        }
        jobManager.tick();
        workstationController.tick();
        notifyTerminalResult();
    }

    private void notifyTerminalResult() {
        ManagedJob job = jobManager.activeJob().orElse(null);
        if (job == null || !job.isTerminal() || job == notifiedTerminalJob) {
            return;
        }
        notifiedTerminalJob = job;
        if (job instanceof ExportJob exportJob) {
            if (exportJob.state() == com.onecuber.mcgltf.job.JobState.COMPLETED
                    && exportJob.finalDirectory().isPresent()) {
                ClientMessages.send(ClientMessages.completed(
                        exportJob.finalDirectory().orElseThrow(),
                        exportJob.warningCount()));
            } else if (exportJob.state() == com.onecuber.mcgltf.job.JobState.FAILED) {
                ClientMessages.send(ClientMessages.failed(
                        exportJob.failureReason().orElse("Unknown export failure")));
            }
        }
    }

    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(McGltfContent.EXPORT_WORKSTATION_MENU.get(),
                new MenuScreens.ScreenConstructor<ExportWorkstationMenu, ExportWorkstationScreen>() {
                    @Override
                    public ExportWorkstationScreen create(
                            ExportWorkstationMenu menu,
                            Inventory inventory,
                            Component title) {
                        return new ExportWorkstationScreen(
                                menu, inventory, title,
                                workstationController, overlayState);
                    }
                });
        event.register(McGltfContent.EXPORT_WAND_MENU.get(),
                new MenuScreens.ScreenConstructor<ExportWandMenu, ExportWandScreen>() {
                    @Override
                    public ExportWandScreen create(
                            ExportWandMenu menu,
                            Inventory inventory,
                            Component title) {
                        return new ExportWandScreen(
                                menu, inventory, title, wandController);
                    }
                });
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        selectionStore.clear();
        activeDimension = null;
        overlayState.clear();
        jobManager.cancel("logout");
        workstationController.unbind();
        wandController.unbind();
    }

    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                jobManager.cancel("resource_reload"));
    }
}
