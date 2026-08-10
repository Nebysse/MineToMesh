package com.onecuber.mcgltf.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.onecuber.mcgltf.workstation.Axis;
import com.onecuber.mcgltf.workstation.Endpoint;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

class WorkstationPayloadCodecTest {
    private final BlockPos station = new BlockPos(10, 64, 10);

    @Test
    void updateCoordinateRoundTrips() {
        UpdateCoordinatePayload payload = new UpdateCoordinatePayload(
                station, Endpoint.SECOND, Axis.Z, -12);
        assertEquals(payload, roundTrip(UpdateCoordinatePayload.STREAM_CODEC, payload));
    }

    @Test
    void captureFeetRoundTrips() {
        CaptureFeetPayload payload = new CaptureFeetPayload(station, Endpoint.FIRST);
        assertEquals(payload, roundTrip(CaptureFeetPayload.STREAM_CODEC, payload));
    }

    @Test
    void exportRequestRoundTrips() {
        ExportRequestPayload payload = new ExportRequestPayload(station, "flower_factory");
        assertEquals(payload, roundTrip(ExportRequestPayload.STREAM_CODEC, payload));
    }

    @Test
    void exportGrantedRoundTrips() {
        ExportGrantedPayload payload = new ExportGrantedPayload(
                station, "flower_factory",
                new BlockPos(-24, 64, 108), new BlockPos(12, 82, 146),
                "minecraft:overworld");
        assertEquals(payload, roundTrip(ExportGrantedPayload.STREAM_CODEC, payload));
    }

    @Test
    void exportRejectedRoundTrips() {
        ExportRejectedPayload payload = new ExportRejectedPayload(
                station, "mcgltf.error.workstation.out_of_bounds");
        assertEquals(payload, roundTrip(ExportRejectedPayload.STREAM_CODEC, payload));
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, value);
        buffer.readerIndex(0);
        return codec.decode(buffer);
    }
}
