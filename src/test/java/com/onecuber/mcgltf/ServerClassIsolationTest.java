package com.onecuber.mcgltf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ServerClassIsolationTest {
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "net/minecraft/client",
            "com/mojang/blaze3d",
            "com/onecuber/mcgltf/client");

    @Test
    void serverReachableClassesDoNotReferenceClientCode() throws Exception {
        Path classesDir = Path.of(System.getProperty("user.dir"))
                .getParent()
                .getParent()
                .resolve("build/classes/java/main");
        System.out.println("SCANNING " + classesDir);
        assertTrue(Files.isDirectory(classesDir),
                "compiled classes not found at " + classesDir);
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(classesDir)) {
            stream.filter(path -> path.toString().endsWith(".class"))
                    .filter(ServerClassIsolationTest::isServerReachable)
                    .forEach(candidates::add);
        }
        assertTrue(candidates.size() >= 8, "expected common-side classes to scan");
        List<String> violations = new ArrayList<>();
        for (Path classFile : candidates) {
            for (String constant : constantPoolStrings(classFile)) {
                for (String forbidden : FORBIDDEN_PREFIXES) {
                    if (constant.startsWith(forbidden)) {
                        violations.add(classFile.getFileName() + " -> " + constant);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "server-reachable classes reference client code: " + violations);
    }

    private static boolean isServerReachable(Path classFile) {
        String relative = classFile.toString().replace('\\', '/');
        return relative.contains("/com/onecuber/mcgltf/content/")
                || relative.contains("/com/onecuber/mcgltf/workstation/")
                || relative.contains("/com/onecuber/mcgltf/network/")
                || relative.endsWith("/com/onecuber/mcgltf/McGltf.class");
    }

    private static Set<String> constantPoolStrings(Path classFile) {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(classFile))) {
            if (in.readInt() != 0xCAFEBABE) {
                throw new IOException("Not a class file: " + classFile);
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            Set<String> strings = new HashSet<>();
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> strings.add(in.readUTF());
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 15 -> in.skipBytes(3);
                    default -> throw new IOException(
                            "Unknown constant pool tag " + tag + " in " + classFile);
                }
            }
            return strings;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
