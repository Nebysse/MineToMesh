package com.nebysse.minetomesh.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nebysse.minetomesh.job.ExportExecutionPolicy;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record ExportWandSelection(
        Optional<UUID> wandId,
        Optional<Identifier> selectionDimension,
        Optional<BlockPos> pos1,
        Optional<BlockPos> pos2,
        boolean overlayEnabled,
        boolean includePlayers,
        int batchChunkCount,
        String exportName) {
    public static final String DEFAULT_EXPORT_NAME = "export";
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
            UUID::fromString, UUID::toString);

    public static final Codec<ExportWandSelection> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.optionalFieldOf("wand_id")
                            .forGetter(ExportWandSelection::wandId),
                    Identifier.CODEC.optionalFieldOf("dimension")
                            .forGetter(ExportWandSelection::selectionDimension),
                    BlockPos.CODEC.optionalFieldOf("pos1")
                            .forGetter(ExportWandSelection::pos1),
                    BlockPos.CODEC.optionalFieldOf("pos2")
                            .forGetter(ExportWandSelection::pos2),
                    Codec.BOOL.optionalFieldOf("overlay_enabled", true)
                            .forGetter(ExportWandSelection::overlayEnabled),
                    Codec.BOOL.optionalFieldOf("include_players", false)
                            .forGetter(ExportWandSelection::includePlayers),
                    Codec.INT.optionalFieldOf("batch_chunk_count",
                                    ExportExecutionPolicy.DEFAULT_BATCH_CHUNKS)
                            .forGetter(ExportWandSelection::batchChunkCount),
                    Codec.STRING.optionalFieldOf("export_name", DEFAULT_EXPORT_NAME)
                            .forGetter(ExportWandSelection::exportName))
                    .apply(instance, ExportWandSelection::new));

    public static final StreamCodec<FriendlyByteBuf, ExportWandSelection> STREAM_CODEC =
            StreamCodec.of(ExportWandSelection::encode, ExportWandSelection::decode);

    public ExportWandSelection {
        wandId = Objects.requireNonNull(wandId, "wandId");
        selectionDimension = Objects.requireNonNull(selectionDimension, "selectionDimension");
        pos1 = Objects.requireNonNull(pos1, "pos1");
        pos2 = Objects.requireNonNull(pos2, "pos2");
        exportName = Objects.requireNonNull(exportName, "exportName");
        ExportExecutionPolicy.validateBatchChunks(batchChunkCount);
        boolean hasEndpoint = pos1.isPresent() || pos2.isPresent();
        if (selectionDimension.isPresent() != hasEndpoint) {
            throw new IllegalArgumentException(
                    "Selection dimension must exist exactly when an endpoint exists");
        }
    }

    public static ExportWandSelection empty() {
        return new ExportWandSelection(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                true, false, ExportExecutionPolicy.DEFAULT_BATCH_CHUNKS,
                DEFAULT_EXPORT_NAME);
    }

    public ExportWandSelection ensureIdentity(UUID value) {
        Objects.requireNonNull(value, "value");
        if (wandId.isPresent()) {
            return this;
        }
        return new ExportWandSelection(Optional.of(value), selectionDimension,
                pos1, pos2, overlayEnabled, includePlayers, batchChunkCount, exportName);
    }

    public ExportWandSelection setEndpoint(
            Identifier dimension, Endpoint endpoint, BlockPos position) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(position, "position");
        if (selectionDimension.isPresent()
                && !selectionDimension.orElseThrow().equals(dimension)) {
            throw new IllegalArgumentException("Selection endpoints must share a dimension");
        }
        Optional<BlockPos> first = endpoint == Endpoint.POS1 ? Optional.of(position) : pos1;
        Optional<BlockPos> second = endpoint == Endpoint.POS2 ? Optional.of(position) : pos2;
        return new ExportWandSelection(wandId, Optional.of(dimension),
                first, second, overlayEnabled, includePlayers, batchChunkCount, exportName);
    }

    public ExportWandSelection clearSelection() {
        return new ExportWandSelection(wandId, Optional.empty(),
                Optional.empty(), Optional.empty(), overlayEnabled, includePlayers,
                batchChunkCount, exportName);
    }

    public ExportWandSelection withOverlayEnabled(boolean value) {
        return new ExportWandSelection(wandId, selectionDimension,
                pos1, pos2, value, includePlayers, batchChunkCount, exportName);
    }

    public ExportWandSelection withIncludePlayers(boolean value) {
        return new ExportWandSelection(wandId, selectionDimension,
                pos1, pos2, overlayEnabled, value, batchChunkCount, exportName);
    }

    public ExportWandSelection withBatchChunkCount(int value) {
        return new ExportWandSelection(wandId, selectionDimension,
                pos1, pos2, overlayEnabled, includePlayers,
                ExportExecutionPolicy.validateBatchChunks(value), exportName);
    }

    public ExportWandSelection withExportName(String value) {
        return new ExportWandSelection(wandId, selectionDimension,
                pos1, pos2, overlayEnabled, includePlayers, batchChunkCount,
                Objects.requireNonNull(value, "value"));
    }

    public boolean isComplete() {
        return selectionDimension.isPresent() && pos1.isPresent() && pos2.isPresent();
    }

    public Optional<Selection> toSelection() {
        if (!isComplete()) {
            return Optional.empty();
        }
        String dimension = selectionDimension.orElseThrow().toString();
        BlockPos first = pos1.orElseThrow();
        BlockPos second = pos2.orElseThrow();
        return Optional.of(Selection.of(
                new BlockPoint(dimension, first.getX(), first.getY(), first.getZ()),
                new BlockPoint(dimension, second.getX(), second.getY(), second.getZ())));
    }

    private static void encode(
            FriendlyByteBuf buffer, ExportWandSelection selection) {
        writeOptional(buffer, selection.wandId, buffer::writeUUID);
        writeOptional(buffer, selection.selectionDimension,
                value -> buffer.writeUtf(value.toString()));
        writeOptional(buffer, selection.pos1, buffer::writeBlockPos);
        writeOptional(buffer, selection.pos2, buffer::writeBlockPos);
        buffer.writeBoolean(selection.overlayEnabled);
        buffer.writeBoolean(selection.includePlayers);
        buffer.writeVarInt(selection.batchChunkCount);
        buffer.writeUtf(selection.exportName, 64);
    }

    private static ExportWandSelection decode(FriendlyByteBuf buffer) {
        Optional<UUID> wandId = readOptional(buffer, buffer::readUUID);
        Optional<Identifier> dimension = readOptional(
                buffer, () -> Identifier.parse(buffer.readUtf()));
        Optional<BlockPos> pos1 = readOptional(buffer, buffer::readBlockPos);
        Optional<BlockPos> pos2 = readOptional(buffer, buffer::readBlockPos);
        boolean overlayEnabled = buffer.readBoolean();
        boolean includePlayers = buffer.readBoolean();
        int batchChunkCount = buffer.readVarInt();
        String exportName = buffer.readUtf(64);
        return new ExportWandSelection(
                wandId, dimension, pos1, pos2, overlayEnabled, includePlayers,
                batchChunkCount, exportName);
    }

    private static <T> void writeOptional(
            FriendlyByteBuf buffer, Optional<T> value,
            java.util.function.Consumer<T> writer) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(writer);
    }

    private static <T> Optional<T> readOptional(
            FriendlyByteBuf buffer, java.util.function.Supplier<T> reader) {
        return buffer.readBoolean() ? Optional.of(reader.get()) : Optional.empty();
    }
}
