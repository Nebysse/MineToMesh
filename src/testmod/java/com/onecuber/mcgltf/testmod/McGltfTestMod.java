package com.onecuber.mcgltf.testmod;

import com.onecuber.mcgltf.testmod.client.McGltfTestClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(McGltfTestMod.MOD_ID)
public final class McGltfTestMod {
    public static final String MOD_ID = "mcgltf_test";

    public McGltfTestMod(IEventBus modBus) {
        TestContent.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            McGltfTestClient.register(modBus);
        }
    }
}
