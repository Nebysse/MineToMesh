package com.onecuber.mcgltf.client;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.command.McGltfCommands;
import com.onecuber.mcgltf.job.ExportJobManager;
import com.onecuber.mcgltf.world.SelectionStore;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = McGltf.MOD_ID, dist = Dist.CLIENT)
public final class McGltfClient {
    private final SelectionStore selectionStore = new SelectionStore();
    private final ExportJobManager jobManager = new ExportJobManager();
    private final McGltfCommands commands;
    private String activeDimension;

    public McGltfClient(IEventBus modBus) {
        commands = new McGltfCommands(selectionStore, jobManager,
                (selection, name) -> {
                    throw new IllegalStateException("Export pipeline is not wired yet");
                });
        NeoForge.EVENT_BUS.addListener(commands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
        modBus.addListener(this::onRegisterReloadListeners);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            String dimension = minecraft.level.dimension().location().toString();
            if (activeDimension != null && !activeDimension.equals(dimension)) {
                selectionStore.clear();
                jobManager.cancel("dimension_change");
            }
            activeDimension = dimension;
        }
        jobManager.tick();
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        selectionStore.clear();
        activeDimension = null;
        jobManager.cancel("logout");
    }

    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                jobManager.cancel("resource_reload"));
    }
}
