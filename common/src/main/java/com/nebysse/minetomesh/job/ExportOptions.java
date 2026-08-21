package com.nebysse.minetomesh.job;

/** Immutable options frozen by the server when an export request is granted. */
public record ExportOptions(boolean includePlayers, boolean mergeChunks) {
    public static final ExportOptions DEFAULT = new ExportOptions(false, false);

    public ExportOptions(boolean includePlayers) {
        this(includePlayers, false);
    }
}
