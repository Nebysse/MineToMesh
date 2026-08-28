package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import com.nebysse.minetomesh.wand.Endpoint;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WandPayloadCodecTest {
    @Test
    void clearSelectionRoundTripsTheActingHand() {
        ClearWandSelectionPayload payload =
                new ClearWandSelectionPayload(InteractionHand.OFF_HAND);
        assertEquals(payload, roundTrip(ClearWandSelectionPayload.STREAM_CODEC, payload));
    }

    @Test
    void airEndpointRequestRoundTripsHandAndEndpoint() {
        SetWandAirEndpointPayload payload = new SetWandAirEndpointPayload(
                InteractionHand.MAIN_HAND, Endpoint.POS1);
        assertEquals(payload, roundTrip(
                SetWandAirEndpointPayload.STREAM_CODEC, payload));
    }

    @Test
    void endpointUpdateRoundTripsCompleteEndpoint() {
        UpdateWandEndpointPayload payload = new UpdateWandEndpointPayload(
                Endpoint.POS2, new BlockPos(-12, 80, 42));
        assertEquals(payload, roundTrip(UpdateWandEndpointPayload.STREAM_CODEC, payload));
    }

    @Test
    void overlayUpdateRoundTrips() {
        ToggleWandOverlayPayload payload = new ToggleWandOverlayPayload(false);
        assertEquals(payload, roundTrip(ToggleWandOverlayPayload.STREAM_CODEC, payload));
    }

    @Test
    void playerInclusionUpdateRoundTrips() {
        ToggleWandIncludePlayersPayload payload = new ToggleWandIncludePlayersPayload(true);
        assertEquals(payload, roundTrip(
                ToggleWandIncludePlayersPayload.STREAM_CODEC, payload));
    }

    @Test
    void batchSizeUpdateRoundTrips() {
        UpdateWandBatchSizePayload payload = new UpdateWandBatchSizePayload(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 16);
        assertEquals(payload, roundTrip(UpdateWandBatchSizePayload.STREAM_CODEC, payload));
    }

    @Test
    void exportNameUpdateRoundTrips() {
        UpdateWandExportNamePayload payload =
                new UpdateWandExportNamePayload("flower_factory");
        assertEquals(payload, roundTrip(UpdateWandExportNamePayload.STREAM_CODEC, payload));
    }

    @Test
    void exportRequestRoundTrips() {
        ExportWandRequestPayload payload =
                new ExportWandRequestPayload("flower_factory");
        assertEquals(payload, roundTrip(ExportWandRequestPayload.STREAM_CODEC, payload));
    }

    @Test
    void exportGrantRoundTripsImmutableSnapshot() {
        ExportWandGrantedPayload payload = new ExportWandGrantedPayload(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "flower_factory", new BlockPos(0, 64, 0),
                new BlockPos(10, 80, 10), "minecraft:overworld", true);
        assertEquals(payload, roundTrip(ExportWandGrantedPayload.STREAM_CODEC, payload));
    }

    @Test
    void exportRejectionRoundTrips() {
        ExportWandRejectedPayload payload = new ExportWandRejectedPayload(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "minetomesh.error.wand.no_export_permission");
        assertEquals(payload, roundTrip(ExportWandRejectedPayload.STREAM_CODEC, payload));
    }

    @Test
    void rollingSessionPayloadsRoundTripTheirAuthoritativeIdentity() {
        UUID session = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID wand = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        String dimension = "minecraft:overworld";
        List<ChunkCoordinate> chunks = List.of(
                new ChunkCoordinate(-2, 7), new ChunkCoordinate(-1, 7));
        assertRoundTrip(ExportSessionAcceptedPayload.STREAM_CODEC,
                new ExportSessionAcceptedPayload(session, wand, dimension,
                        new BlockPos(0, 1, 2), new BlockPos(3, 4, 5),
                        "castle", true, true, 4, 40, 10));
        assertRoundTrip(ExportSessionRejectedPayload.STREAM_CODEC,
                new ExportSessionRejectedPayload(session, wand, dimension, "busy"));
        assertRoundTrip(BatchLoadStartedPayload.STREAM_CODEC,
                new BatchLoadStartedPayload(session, wand, dimension, 3, chunks));
        assertRoundTrip(BatchReadyPayload.STREAM_CODEC,
                new BatchReadyPayload(session, wand, dimension, 3, chunks));
        assertRoundTrip(BatchClientReadablePayload.STREAM_CODEC,
                new BatchClientReadablePayload(session, wand, dimension, 3));
        assertRoundTrip(BatchCaptureCompletedPayload.STREAM_CODEC,
                new BatchCaptureCompletedPayload(session, wand, dimension, 3, 99, 2));
        assertRoundTrip(ExportProgressHeartbeatPayload.STREAM_CODEC,
                new ExportProgressHeartbeatPayload(
                        session, wand, dimension, 3, "capturing", 99));
        assertRoundTrip(CancelExportRequestPayload.STREAM_CODEC,
                new CancelExportRequestPayload(session, wand, dimension, "user"));
        assertRoundTrip(ExportCancelAcknowledgedPayload.STREAM_CODEC,
                new ExportCancelAcknowledgedPayload(session, wand, dimension, 1));
        assertRoundTrip(ExportClientCompletedPayload.STREAM_CODEC,
                new ExportClientCompletedPayload(session, wand, dimension, 10, "completed"));
        assertRoundTrip(ExportSessionFinishedPayload.STREAM_CODEC,
                new ExportSessionFinishedPayload(session, wand, dimension, "completed"));
        assertRoundTrip(ExportSessionFailedPayload.STREAM_CODEC,
                new ExportSessionFailedPayload(session, wand, dimension,
                        "chunk_timeout", 3, Optional.of(new ChunkCoordinate(-2, 7))));
    }

    private static <T> void assertRoundTrip(
            StreamCodec<FriendlyByteBuf, T> codec, T value) {
        assertEquals(value, roundTrip(codec, value));
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, value);
        buffer.readerIndex(0);
        return codec.decode(buffer);
    }
}
