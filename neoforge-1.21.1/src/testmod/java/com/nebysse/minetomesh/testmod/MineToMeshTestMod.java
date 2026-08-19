package com.nebysse.minetomesh.testmod;

import com.nebysse.minetomesh.testmod.client.MineToMeshTestClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MineToMeshTestMod.MOD_ID)
public final class MineToMeshTestMod {
    public static final String MOD_ID = "minetomesh_test";

    public MineToMeshTestMod(IEventBus modBus) {
        TestContent.register(modBus);
        if (System.getProperty("minetomesh.serverSmoke") != null) {
            NeoForge.EVENT_BUS.addListener(WandGameTests::onServerStarted);
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MineToMeshTestClient.register(modBus);
        }
    }
}
