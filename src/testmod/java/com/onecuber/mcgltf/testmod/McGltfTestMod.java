package com.onecuber.mcgltf.testmod;

import com.onecuber.mcgltf.testmod.client.McGltfTestClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(McGltfTestMod.MOD_ID)
public final class McGltfTestMod {
    public static final String MOD_ID = "mcgltf_test";

    public McGltfTestMod(IEventBus modBus) {
        TestContent.register(modBus);
        if (System.getProperty("mcgltf.serverSmoke") != null) {
            NeoForge.EVENT_BUS.addListener(WandGameTests::onServerStarted);
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            McGltfTestClient.register(modBus);
        }
    }
}
