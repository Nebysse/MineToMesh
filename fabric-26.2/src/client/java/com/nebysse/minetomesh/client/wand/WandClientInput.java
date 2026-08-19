package com.nebysse.minetomesh.client.wand;

import com.nebysse.minetomesh.network.ClearWandSelectionPayload;
import com.nebysse.minetomesh.network.SetWandAirEndpointPayload;
import com.nebysse.minetomesh.wand.Endpoint;
import com.nebysse.minetomesh.wand.WandInteractionHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

public final class WandClientInput {
    private boolean airAttackHeld;

    public void register() {
        ClientPreAttackCallback.EVENT.register(this::onPreAttack);
    }

    private boolean onPreAttack(
            Minecraft minecraft, LocalPlayer player, int clickCount) {
        if (minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.MISS) {
            return false;
        }
        InteractionHand hand = WandInteractionHandler.resolveWandHand(player);
        if (hand == null) {
            return false;
        }
        if (!airAttackHeld && minecraft.getConnection() != null) {
            if (player.isShiftKeyDown()) {
                ClientPlayNetworking.send(new ClearWandSelectionPayload(hand));
            } else {
                ClientPlayNetworking.send(
                        new SetWandAirEndpointPayload(hand, Endpoint.POS1));
            }
            airAttackHeld = true;
        }
        return true;
    }

    public void tick(Minecraft minecraft) {
        if (minecraft.player == null
                || !minecraft.options.keyAttack.isDown()) {
            airAttackHeld = false;
        }
    }
}
