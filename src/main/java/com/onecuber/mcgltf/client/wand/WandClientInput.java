package com.onecuber.mcgltf.client.wand;

import com.onecuber.mcgltf.network.ClearWandSelectionPayload;
import com.onecuber.mcgltf.wand.WandInteractionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;

public final class WandClientInput {
    private boolean clearChordHeld;

    public void onInteractionKey(
            InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isShiftKeyDown()
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
        if (!clearChordHeld && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ClearWandSelectionPayload(hand));
            clearChordHeld = true;
        }
    }

    public void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isShiftKeyDown()
                || !minecraft.options.keyAttack.isDown()) {
            clearChordHeld = false;
        }
    }
}
