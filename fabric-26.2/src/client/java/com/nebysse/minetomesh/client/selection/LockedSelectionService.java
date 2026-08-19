package com.nebysse.minetomesh.client.selection;

import com.nebysse.minetomesh.client.wand.HeldWandOverlaySource;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

public final class LockedSelectionService {
    public enum ToggleResult {
        LOCKED,
        REPLACED,
        UNLOCKED,
        INCOMPLETE,
        NO_PROFILE,
        WRITE_FAILED
    }

    private final LockedSelectionStore store;
    private final Supplier<Optional<WorldProfileKey>> profileSupplier;
    private String lastError;

    public LockedSelectionService(
            LockedSelectionStore store,
            Supplier<Optional<WorldProfileKey>> profileSupplier) {
        this.store = Objects.requireNonNull(store, "store");
        this.profileSupplier = Objects.requireNonNull(profileSupplier, "profileSupplier");
    }

    public ToggleResult toggle(Optional<LockedSelection> candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.isEmpty()) {
            return ToggleResult.INCOMPLETE;
        }
        Optional<WorldProfileKey> profile = currentProfile();
        if (profile.isEmpty()) {
            return ToggleResult.NO_PROFILE;
        }
        WorldProfileKey key = profile.orElseThrow();
        LockedSelection selection = candidate.orElseThrow();
        try {
            if (store.matches(key, selection)) {
                store.remove(key);
                lastError = null;
                return ToggleResult.UNLOCKED;
            }
            boolean replacing = store.get(key).isPresent();
            store.put(key, selection);
            lastError = null;
            return replacing ? ToggleResult.REPLACED : ToggleResult.LOCKED;
        } catch (IOException exception) {
            lastError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return ToggleResult.WRITE_FAILED;
        }
    }

    public boolean isCurrent(LockedSelection candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return currentProfile().filter(profile -> store.matches(profile, candidate)).isPresent();
    }

    public Optional<HeldWandOverlaySource.Snapshot> resolve(Identifier dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return currentProfile()
                .flatMap(store::get)
                .flatMap(selection -> selection.snapshot(dimension));
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    private Optional<WorldProfileKey> currentProfile() {
        Optional<WorldProfileKey> profile = profileSupplier.get();
        return profile == null ? Optional.empty() : profile;
    }
}
