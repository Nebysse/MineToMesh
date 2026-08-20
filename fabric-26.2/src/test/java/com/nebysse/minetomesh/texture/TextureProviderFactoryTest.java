package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class TextureProviderFactoryTest {
    @Test
    void exposesResourceAndDynamicProvidersInStableOrder() {
        assertEquals("resource", ResourceTextureExtractor.resourceProvider().id());
        assertEquals("dynamic", ResourceTextureExtractor.dynamicProvider().id());
    }

    @Test
    void missingTextureManagerFallsThroughToLaterProviders() throws Exception {
        TextureProvider.Request request = new TextureProvider.Request(
                Identifier.parse("test:runtime/disposed"), null, null);

        assertTrue(ResourceTextureExtractor.dynamicProvider()
                .acquire(request).isEmpty());
    }
}
