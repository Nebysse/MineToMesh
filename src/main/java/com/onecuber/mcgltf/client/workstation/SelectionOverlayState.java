package com.onecuber.mcgltf.client.workstation;

import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SelectionOverlayState {
    private final Map<OverlayKey, WorkstationCoordinates> overlays = new LinkedHashMap<>();

    public void toggle(OverlayKey key, WorkstationCoordinates coordinates) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(coordinates, "coordinates");
        if (overlays.containsKey(key)) {
            overlays.remove(key);
        } else {
            overlays.put(key, coordinates);
        }
    }

    public void refresh(OverlayKey key, WorkstationCoordinates coordinates) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(coordinates, "coordinates");
        if (overlays.containsKey(key)) {
            overlays.put(key, coordinates);
        }
    }

    public void remove(OverlayKey key) {
        overlays.remove(Objects.requireNonNull(key, "key"));
    }

    public void screenClosed(OverlayKey key) {
        // Overlay visibility persists after the screen closes by design.
    }

    public void dimensionChanged(String dimension) {
        Objects.requireNonNull(dimension, "dimension");
        overlays.keySet().removeIf(key -> !key.dimension().equals(dimension));
    }

    public void clear() {
        overlays.clear();
    }

    public boolean visible(OverlayKey key) {
        return overlays.containsKey(Objects.requireNonNull(key, "key"));
    }

    public Optional<WorkstationCoordinates> coordinates(OverlayKey key) {
        return Optional.ofNullable(overlays.get(Objects.requireNonNull(key, "key")));
    }

    public List<Map.Entry<OverlayKey, WorkstationCoordinates>> activeInDimension(String dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return overlays.entrySet().stream()
                .filter(entry -> entry.getKey().dimension().equals(dimension))
                .toList();
    }
}
