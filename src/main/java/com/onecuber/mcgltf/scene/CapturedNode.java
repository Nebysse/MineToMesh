package com.onecuber.mcgltf.scene;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CapturedNode(
        String name,
        Kind kind,
        List<PrimitiveData> primitives,
        Map<String, Object> extras) {
    public CapturedNode {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        primitives = List.copyOf(primitives);
        extras = Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }

    public enum Kind {
        CHUNK,
        BLOCK_ENTITY,
        ENTITY,
        PLACEHOLDER,
        OVERLAY
    }
}
