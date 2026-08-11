package com.onecuber.mcgltf.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import com.onecuber.mcgltf.wand.Endpoint;
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
                new BlockPos(10, 80, 10), "minecraft:overworld");
        assertEquals(payload, roundTrip(ExportWandGrantedPayload.STREAM_CODEC, payload));
    }

    @Test
    void exportRejectionRoundTrips() {
        ExportWandRejectedPayload payload = new ExportWandRejectedPayload(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "mcgltf.error.wand.no_export_permission");
        assertEquals(payload, roundTrip(ExportWandRejectedPayload.STREAM_CODEC, payload));
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, value);
        buffer.readerIndex(0);
        return codec.decode(buffer);
    }
}
