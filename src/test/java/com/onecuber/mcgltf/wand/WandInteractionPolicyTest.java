package com.onecuber.mcgltf.wand;

import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Action.CLEAR;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Action.OPEN_GUI;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Action.PASS;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Action.SET_POS1;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Action.SET_POS2;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Button.LEFT;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Button.RIGHT;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Target.AIR;
import static com.onecuber.mcgltf.wand.WandInteractionPolicy.Target.BLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WandInteractionPolicyTest {
    @Test
    void appliesTheApprovedInputPriorityTable() {
        assertEquals(SET_POS1, WandInteractionPolicy.decide(false, BLOCK, LEFT));
        assertEquals(SET_POS1, WandInteractionPolicy.decide(true, BLOCK, LEFT));
        assertEquals(SET_POS1, WandInteractionPolicy.decide(false, AIR, LEFT));
        assertEquals(CLEAR, WandInteractionPolicy.decide(true, AIR, LEFT));
        assertEquals(SET_POS2, WandInteractionPolicy.decide(false, BLOCK, RIGHT));
        assertEquals(OPEN_GUI, WandInteractionPolicy.decide(true, BLOCK, RIGHT));
        assertEquals(SET_POS2, WandInteractionPolicy.decide(false, AIR, RIGHT));
        assertEquals(OPEN_GUI, WandInteractionPolicy.decide(true, AIR, RIGHT));
    }
}
