package com.nebysse.minetomesh.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExportNameTest {
    @Test
    void acceptsUnicodeAndSafePunctuation() {
        assertEquals("城堡_01", ExportName.parse("城堡_01").value());
        assertEquals("castle.v2", ExportName.parse("castle.v2").value());
    }

    @Test
    void normalizesToUnicodeNfc() {
        assertEquals("é", ExportName.parse("e\u0301").value());
    }

    @Test
    void acceptsExactlySixtyFourUnicodeCodePoints() {
        String value = "界".repeat(64);
        assertEquals(value, ExportName.parse(value).value());
    }

    @ParameterizedTest
    @MethodSource("unsafeNames")
    void rejectsUnsafeNames(String value) {
        assertThrows(IllegalArgumentException.class, () -> ExportName.parse(value));
    }

    static Stream<String> unsafeNames() {
        return Stream.of(
                "",
                ".",
                "..",
                "CON",
                "con.txt",
                "a/b",
                "a\\b",
                "trailing.",
                "trailing ",
                "bad\u0001name",
                "a".repeat(65));
    }
}
