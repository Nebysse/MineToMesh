package com.nebysse.minetomesh.client.wand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OverlaySnapshotPolicy {
    private OverlaySnapshotPolicy() {
    }

    public static List<HeldWandOverlaySource.Snapshot> merge(
            Optional<HeldWandOverlaySource.Snapshot> held,
            Optional<HeldWandOverlaySource.Snapshot> locked) {
        Objects.requireNonNull(held, "held");
        Objects.requireNonNull(locked, "locked");
        LinkedHashSet<HeldWandOverlaySource.Snapshot> snapshots = new LinkedHashSet<>();
        held.ifPresent(snapshots::add);
        locked.ifPresent(snapshots::add);
        return List.copyOf(snapshots);
    }
}
