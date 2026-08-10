package com.onecuber.mcgltf.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextureProviderFactoryTest {
    @Test
    void exposesResourceAndDynamicProvidersInStableOrder() {
        assertEquals("resource", ResourceTextureExtractor.resourceProvider().id());
        assertEquals("dynamic", ResourceTextureExtractor.dynamicProvider().id());
    }
}
