package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoordinateEditorModelTest {
    private CoordinateEditorModel model;

    @BeforeEach
    void setUp() {
        model = new CoordinateEditorModel();
        model.serverValue(0);
    }

    @Test
    void parsesSignedInputAndCommits() {
        model.setText("-24");
        assertEquals(OptionalInt.of(-24), model.commit());
        assertFalse(model.isInvalid());
    }

    @Test
    void buttonStepAdjustsByOne() {
        model.serverValue(3);
        assertEquals(4, model.step(1, false));
        assertEquals(3, model.step(-1, false));
    }

    @Test
    void wheelStepAdjustsByOne() {
        model.serverValue(10);
        assertEquals(11, model.step(1, false));
    }

    @Test
    void shiftWheelStepsByTen() {
        model.setText("-24");
        assertEquals(OptionalInt.of(-24), model.commit());
        assertEquals(-14, model.step(1, true));
    }

    @Test
    void overflowLeavesModelInvalid() {
        model.serverValue(Integer.MAX_VALUE);
        model.step(1, false);
        assertTrue(model.isInvalid());
        assertEquals(OptionalInt.empty(), model.commit());
    }

    @Test
    void serverValueReplacesTextOnlyWhenNotEditing() {
        model.serverValue(7);
        assertEquals("7", model.rawText());
        model.beginEdit();
        model.serverValue(99);
        assertEquals("7", model.rawText());
        model.endEdit();
        model.serverValue(99);
        assertEquals("99", model.rawText());
    }

    @Test
    void emptyAndUnparsableTextAreInvalid() {
        assertTrue(model.setText("").isInvalid());
        assertTrue(model.setText("12a").isInvalid());
        assertTrue(model.setText("999999999999").isInvalid());
        assertEquals(OptionalInt.empty(), model.commit());
    }
}
