package com.onecuber.mcgltf.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

class WandPayloadCodecTest {
    @Test
    void clearSelectionRoundTripsTheActingHand() {
        ClearWandSelectionPayload payload =
                new ClearWandSelectionPayload(InteractionHand.OFF_HAND);
        assertEquals(payload, roundTrip(ClearWandSelectionPayload.STREAM_CODEC, payload));
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, value);
        buffer.readerIndex(0);
        return codec.decode(buffer);
    }
}
