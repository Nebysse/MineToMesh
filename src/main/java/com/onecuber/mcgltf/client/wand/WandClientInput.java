package com.onecuber.mcgltf.client.wand;

import com.onecuber.mcgltf.network.ClearWandSelectionPayload;
import com.onecuber.mcgltf.network.SetWandAirEndpointPayload;
import com.onecuber.mcgltf.wand.Endpoint;
import com.onecuber.mcgltf.wand.WandInteractionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;

public final class WandClientInput {
    private boolean airAttackHeld;

    public void onInteractionKey(
            InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.MISS) {
            return;
        }
        InteractionHand hand = WandInteractionHandler.resolveWandHand(player);
        if (hand == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (airAttackHeld || minecraft.getConnection() == null) {
            return;
        }
        if (player.isShiftKeyDown()) {
            minecraft.getConnection().send(
                    new ClearWandSelectionPayload(hand));
        } else {
            minecraft.getConnection().send(
                    new SetWandAirEndpointPayload(hand, Endpoint.POS1));
        }
        airAttackHeld = true;
    }

    public void tick(Minecraft minecraft) {
        if (minecraft.player == null
                || !minecraft.options.keyAttack.isDown()) {
            airAttackHeld = false;
        }
    }
}
