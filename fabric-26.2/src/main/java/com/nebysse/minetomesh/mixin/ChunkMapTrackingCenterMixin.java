package com.nebysse.minetomesh.mixin;

import com.nebysse.minetomesh.server.ServerExportSessions;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkMap.class)
public abstract class ChunkMapTrackingCenterMixin {
    @Redirect(
            method = "updateChunkTracking",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos minetomesh$trackingCenter(ServerPlayer player) {
        return ServerExportSessions.trackingCenter(player.getUUID())
                .map(value -> new ChunkPos(value.x(), value.z()))
                .orElseGet(player::chunkPosition);
    }
}
