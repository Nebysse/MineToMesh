package com.nebysse.minetomesh.job;

import com.nebysse.minetomesh.scene.CapturedNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RawCapturedObject(
        String objectId,
        CapturedNode.Kind kind,
        List<RawPrimitiveStream> streams,
        Map<String, Object> extras) {
    public RawCapturedObject {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(kind, "kind");
        streams = List.copyOf(streams);
        extras = Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
