# MineToMesh Connected Texture, Grass Overlay, and Input Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. The project owner disabled subagent use. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export Create 6.x connected textures from their actual atlas UVs, place all vanilla grass side overlays in one selection-wide Blender object, and prevent wand GUI text input from leaking into Minecraft shortcuts.

**Architecture:** Resolve every block quad against a cached atlas-space sprite index before texture extraction, then route the resolved grass side overlay material into a stable `OVERLAY` logical node. glTF coalesces repeated overlay fragments into one mesh while streaming binary data; OBJ spools overlay fragments and appends them once at finish. A small keyboard policy keeps all non-Esc key traffic inside the wand screen and forwards text events directly to the focused `EditBox`.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.244, JUnit 5, Gson, glTF 2.0, Wavefront OBJ/MTL.

**Execution policy:** Execute inline in `D:\data\code\mcgltf\.worktrees\mcgltf-0.4.0-export-wand`. Preserve strict red-green-refactor TDD. The current worktree contains uncommitted wand-polish work; Task 0 creates a verified baseline before any files shared with this feature are changed.

---

## File Structure

### New production files

- `src/main/java/com/onecuber/mcgltf/texture/AtlasSpriteIndex.java` — pure, Minecraft-renderer-independent 256×256 UV bucket index and deterministic region selection.
- `src/main/java/com/onecuber/mcgltf/texture/AtlasSpriteResolver.java` — adapts live `TextureAtlasSprite` catalogs to `AtlasSpriteIndex` and caches one index per atlas.
- `src/main/java/com/onecuber/mcgltf/capture/BlockPrimitiveRouter.java` — classifies resolved texture IDs into section geometry or the global grass-side overlay route.
- `src/main/java/com/onecuber/mcgltf/client/wand/WandKeyboardPolicy.java` — pure key-action policy used by `ExportWandScreen`.

### New tests

- `src/test/java/com/onecuber/mcgltf/texture/AtlasSpriteIndexTest.java`
- `src/test/java/com/onecuber/mcgltf/texture/AtlasSpriteResolverPolicyTest.java`
- `src/test/java/com/onecuber/mcgltf/capture/BlockPrimitiveRouterTest.java`
- `src/test/java/com/onecuber/mcgltf/client/wand/WandKeyboardPolicyTest.java`

### Modified production files

- `src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java` — captures raw UVs first, resolves the actual sprite, registers the actual texture, normalizes against the resolved bounds, and routes overlay streams.
- `src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java` — constructs the resolver, owns separate section/overlay accumulators, and emits stable overlay fragments.
- `src/main/java/com/onecuber/mcgltf/scene/CapturedNode.java` — adds `OVERLAY` kind.
- `src/main/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilder.java` — adds an `Overlays` hierarchy root and coalesces matching overlay fragments.
- `src/main/java/com/onecuber/mcgltf/gltf/StreamingGltfSession.java` — counts coalesced overlay nodes once.
- `src/main/java/com/onecuber/mcgltf/obj/StreamingObjSession.java` — spools overlay geometry with negative relative indices and appends one object at finish.
- `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java` — removes unsafe `super.keyPressed` propagation for ordinary keys and forwards text events directly.

### Modified tests and documentation

- `src/test/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilderTest.java`
- `src/test/java/com/onecuber/mcgltf/gltf/StreamingGltfSessionTest.java`
- `src/test/java/com/onecuber/mcgltf/obj/StreamingObjSessionTest.java`
- `src/test/java/com/onecuber/mcgltf/output/StreamingSceneSessionTest.java`
- `src/test/java/com/onecuber/mcgltf/client/wand/ExportWandScreenBindingTest.java`
- `README.md`
- `docs/testing/manual-client-matrix.md`

---

## Task 0: Establish a Clean Wand-Polish Baseline

**Files already modified in the worktree:**

- `src/main/java/com/onecuber/mcgltf/capture/EntityCapture.java`
- `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandController.java`
- `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java`
- `src/main/java/com/onecuber/mcgltf/client/wand/HeldWandOverlaySource.java`
- `src/main/java/com/onecuber/mcgltf/client/wand/SelectionOverlayRenderer.java`
- `src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java`
- `src/main/java/com/onecuber/mcgltf/network/ExportWandGrantedPayload.java`
- `src/main/java/com/onecuber/mcgltf/network/WandPayloads.java`
- `src/main/java/com/onecuber/mcgltf/wand/ExportWandSelection.java`
- `src/main/java/com/onecuber/mcgltf/wand/ExportWandService.java`
- `src/main/java/com/onecuber/mcgltf/job/ExportOptions.java`
- `src/main/java/com/onecuber/mcgltf/network/ToggleWandIncludePlayersPayload.java`
- matching tests and `src/main/resources/assets/mcgltf/textures/item/export_wand.png`

- [ ] **Step 1: Review the existing polish diff without changing it**

Run:

```powershell
git status --short
git diff --check
git diff -- src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java
git diff -- src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java
```

Expected: only the already-known wand-polish files are dirty; `git diff --check` prints no errors. Confirm the diff matches `docs/superpowers/specs/2026-08-10-minetomesh-0.4.0-wand-polish-design.md`. Do not begin Task 1 if unrelated user edits are present.

- [ ] **Step 2: Run the current polish test set**

Run:

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.wand.*" --tests "com.onecuber.mcgltf.client.wand.*" --tests "com.onecuber.mcgltf.network.WandPayloadCodecTest" --tests "com.onecuber.mcgltf.capture.EntityFilterTest"
```

Expected: PASS. If this fails, finish the already-approved wand-polish work before continuing; do not fold unrelated repairs into the connected-texture commits.

- [ ] **Step 3: Run the complete unit suite**

Run:

```powershell
.\gradlew.bat test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit only the existing polish implementation**

Run the exact staged-file review:

```powershell
git add -- src/main/java/com/onecuber/mcgltf/capture/EntityCapture.java src/main/java/com/onecuber/mcgltf/client/McGltfClient.java src/main/java/com/onecuber/mcgltf/client/wand/ExportWandController.java src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java src/main/java/com/onecuber/mcgltf/client/wand/HeldWandOverlaySource.java src/main/java/com/onecuber/mcgltf/client/wand/SelectionOverlayRenderer.java src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java src/main/java/com/onecuber/mcgltf/job/ExportOptions.java src/main/java/com/onecuber/mcgltf/network/ExportWandGrantedPayload.java src/main/java/com/onecuber/mcgltf/network/ToggleWandIncludePlayersPayload.java src/main/java/com/onecuber/mcgltf/network/WandPayloads.java src/main/java/com/onecuber/mcgltf/wand/ExportWandSelection.java src/main/java/com/onecuber/mcgltf/wand/ExportWandService.java src/main/resources/assets/mcgltf/textures/item/export_wand.png src/test/java/com/onecuber/mcgltf/capture/EntityFilterTest.java src/test/java/com/onecuber/mcgltf/client/wand/ExportWandControllerTest.java src/test/java/com/onecuber/mcgltf/client/wand/ExportWandVisualIntegrationTest.java src/test/java/com/onecuber/mcgltf/content/ExportWandResourceTest.java src/test/java/com/onecuber/mcgltf/network/WandPayloadCodecTest.java src/test/java/com/onecuber/mcgltf/wand/ExportWandSelectionTest.java
git diff --cached --check
git diff --cached --stat
git commit -m "feat: complete wand input and visual polish"
```

Expected: the commit contains only the pre-existing polish scope. The connected-texture design and this plan are already separate documentation commits.

---

## Task 1: Build the Pure Atlas UV Region Index

**Files:**

- Create: `src/main/java/com/onecuber/mcgltf/texture/AtlasSpriteIndex.java`
- Create: `src/test/java/com/onecuber/mcgltf/texture/AtlasSpriteIndexTest.java`

- [ ] **Step 1: Write failing selection tests**

Create `AtlasSpriteIndexTest` with these cases:

```java
package com.onecuber.mcgltf.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.scene.Vec2f;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AtlasSpriteIndexTest {
    private static final ResourceLocation ORIGINAL =
            ResourceLocation.parse("create:block/andesite_casing");
    private static final ResourceLocation CONNECTED =
            ResourceLocation.parse("create:block/andesite_casing_connected");

    @Test
    void keepsDeclaredSpriteWhenItContainsAllUvs() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(ORIGINAL, 0.10F, 0.10F, 0.20F, 0.20F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.11F, 0.11F, 0.19F, 0.19F));

        assertEquals(AtlasSpriteIndex.Kind.DECLARED, result.kind());
        assertEquals(ORIGINAL, result.region().orElseThrow().id());
    }

    @Test
    void redirectsCreateStyleUvsToConnectedSheet() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(ORIGINAL, 0.10F, 0.10F, 0.20F, 0.20F),
                region(CONNECTED, 0.40F, 0.30F, 0.80F, 0.70F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.45F, 0.35F, 0.50F, 0.40F));

        assertEquals(AtlasSpriteIndex.Kind.REDIRECTED, result.kind());
        assertEquals(CONNECTED, result.region().orElseThrow().id());
    }

    @Test
    void selectsSmallestCoveringRegionThenStableId() {
        ResourceLocation broad = ResourceLocation.parse("test:broad");
        ResourceLocation zed = ResourceLocation.parse("test:zed");
        ResourceLocation alpha = ResourceLocation.parse("test:alpha");
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(broad, 0.0F, 0.0F, 1.0F, 1.0F),
                region(zed, 0.4F, 0.4F, 0.6F, 0.6F),
                region(alpha, 0.4F, 0.4F, 0.6F, 0.6F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.45F, 0.45F, 0.55F, 0.55F));

        assertEquals(alpha, result.region().orElseThrow().id());
    }

    @Test
    void returnsDeclaredFallbackWhenNoRegionCoversUvs() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(ORIGINAL, 0.10F, 0.10F, 0.20F, 0.20F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.80F, 0.80F, 0.90F, 0.90F));

        assertEquals(AtlasSpriteIndex.Kind.FALLBACK, result.kind());
        assertEquals(ORIGINAL, result.region().orElseThrow().id());
    }

    @Test
    void halfPixelToleranceKeepsUvsOnDeclaredBoundary() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                new AtlasSpriteIndex.Region(ORIGINAL,
                        0.10F, 0.10F, 0.20F, 0.20F, 1024, 1024)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.10F - 0.25F / 1024F, 0.10F,
                        0.20F + 0.25F / 1024F, 0.20F));

        assertEquals(AtlasSpriteIndex.Kind.DECLARED, result.kind());
        assertTrue(result.region().isPresent());
    }

    private static AtlasSpriteIndex.Region region(
            ResourceLocation id, float u0, float v0, float u1, float v1) {
        return new AtlasSpriteIndex.Region(id, u0, v0, u1, v1, 1024, 1024);
    }

    private static List<Vec2f> quad(float u0, float v0, float u1, float v1) {
        return List.of(new Vec2f(u0, v0), new Vec2f(u0, v1),
                new Vec2f(u1, v1), new Vec2f(u1, v0));
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.texture.AtlasSpriteIndexTest
```

Expected: test compilation fails because `AtlasSpriteIndex` does not exist.

- [ ] **Step 3: Implement the immutable region and deterministic result contract**

Create `AtlasSpriteIndex` with this public shape:

```java
public final class AtlasSpriteIndex {
    static final int BUCKET_COUNT = 256;

    public enum Kind { DECLARED, REDIRECTED, FALLBACK }

    public record Region(
            ResourceLocation id,
            float u0, float v0, float u1, float v1,
            int atlasWidth, int atlasHeight) {
        public Region {
            Objects.requireNonNull(id, "id");
            if (!(u0 < u1) || !(v0 < v1)
                    || atlasWidth <= 0 || atlasHeight <= 0) {
                throw new IllegalArgumentException("Invalid atlas sprite region");
            }
        }

        float area() {
            return (u1 - u0) * (v1 - v0);
        }

        boolean contains(List<Vec2f> uvs) {
            float epsilonU = 0.5F / atlasWidth;
            float epsilonV = 0.5F / atlasHeight;
            return uvs.stream().allMatch(uv ->
                    uv.x() >= u0 - epsilonU && uv.x() <= u1 + epsilonU
                    && uv.y() >= v0 - epsilonV && uv.y() <= v1 + epsilonV);
        }
    }

    public record Resolution(Optional<Region> region, Kind kind) {
        public Resolution {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(kind, "kind");
        }
    }
}
```

Store a `Map<ResourceLocation, Region>` and a `Map<Integer, List<Region>>` keyed by `bucketY * BUCKET_COUNT + bucketX`. Insert each region into every intersected bucket. Clamp bucket coordinates to `0..255`; use `Math.nextDown(u1)` and `Math.nextDown(v1)` for exclusive upper bucket edges.

Resolve in this order:

```java
Region declared = byId.get(declaredId);
if (declared != null && declared.contains(uvs)) {
    return new Resolution(Optional.of(declared), Kind.DECLARED);
}
List<Region> candidates = candidatesAtCentroid(uvs).stream()
        .filter(region -> region.contains(uvs))
        .sorted(Comparator.comparingDouble(Region::area)
                .thenComparing(region -> region.id().toString()))
        .toList();
if (!candidates.isEmpty()) {
    return new Resolution(Optional.of(candidates.getFirst()), Kind.REDIRECTED);
}
return new Resolution(Optional.ofNullable(declared), Kind.FALLBACK);
```

Reject empty UV lists and non-finite values instead of silently indexing them.

- [ ] **Step 4: Run the test and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.texture.AtlasSpriteIndexTest
```

Expected: PASS.

- [ ] **Step 5: Commit the pure index**

```powershell
git add -- src/main/java/com/onecuber/mcgltf/texture/AtlasSpriteIndex.java src/test/java/com/onecuber/mcgltf/texture/AtlasSpriteIndexTest.java
git commit -m "feat: resolve actual sprites from atlas UVs"
```

---

## Task 2: Adapt Live Texture Atlases and Resolve Block Quad Textures

**Files:**

- Create: `src/main/java/com/onecuber/mcgltf/texture/AtlasSpriteResolver.java`
- Create: `src/test/java/com/onecuber/mcgltf/texture/AtlasSpriteResolverPolicyTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java`
- Modify: `src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java`
- Modify: `src/test/java/com/onecuber/mcgltf/texture/UvNormalizationTest.java`

- [ ] **Step 1: Write failing region-construction and connected-UV tests**

Create `AtlasSpriteResolverPolicyTest` around package-private pure helpers:

```java
@Test
void derivesAtlasDimensionsFromCanonicalSpriteBounds() {
    AtlasSpriteIndex.Region region = AtlasSpriteResolver.region(
            ResourceLocation.parse("create:block/casing_connected"),
            128, 128, 0.25F, 0.125F, 0.375F, 0.25F);

    assertEquals(1024, region.atlasWidth());
    assertEquals(1024, region.atlasHeight());
}

@Test
void normalizesCreateTileInsideResolvedConnectedSheet() {
    List<Vec2f> normalized = AtlasSpriteResolver.normalize(
            List.of(new Vec2f(0.50F, 0.25F), new Vec2f(0.50F, 0.30F),
                    new Vec2f(0.55F, 0.30F), new Vec2f(0.55F, 0.25F)),
            new AtlasSpriteIndex.Region(
                    ResourceLocation.parse("create:block/casing_connected"),
                    0.40F, 0.20F, 0.80F, 0.60F, 1024, 1024));

    assertEquals(0.25F, normalized.get(0).x(), 1.0E-6F);
    assertEquals(0.125F, normalized.get(0).y(), 1.0E-6F);
    assertEquals(0.375F, normalized.get(2).x(), 1.0E-6F);
    assertEquals(0.25F, normalized.get(2).y(), 1.0E-6F);
}
```

Also extend `UvNormalizationTest` with an out-of-range regression proving the old declared-sprite path would produce UVs outside `0..1`; this documents the Create failure before integration.

- [ ] **Step 2: Run targeted tests and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.texture.AtlasSpriteResolverPolicyTest --tests com.onecuber.mcgltf.texture.UvNormalizationTest
```

Expected: compilation fails because `AtlasSpriteResolver` is absent.

- [ ] **Step 3: Implement the live atlas adapter**

Use this contract:

```java
public final class AtlasSpriteResolver {
    @FunctionalInterface
    public interface AtlasLookup {
        TextureAtlas get(ResourceLocation atlasLocation);
    }

    public record Resolution(
            TextureAtlasSprite sprite,
            AtlasSpriteIndex.Region region,
            ResourceLocation declaredId,
            ResourceLocation resolvedId,
            AtlasSpriteIndex.Kind kind) {}

    public Resolution resolve(TextureAtlasSprite declared, List<Vec2f> atlasUvs);

    static AtlasSpriteIndex.Region region(
            ResourceLocation id, int pixelWidth, int pixelHeight,
            float u0, float v0, float u1, float v1);

    static List<Vec2f> normalize(
            List<Vec2f> atlasUvs, AtlasSpriteIndex.Region region);
}
```

Implementation requirements:

- Cache `AtlasData` in an `IdentityHashMap<TextureAtlas, AtlasData>`.
- Build `AtlasData` from `atlas.getTextures()`; each entry keeps the canonical Sprite and its `AtlasSpriteIndex.Region`.
- Derive dimensions exactly as:

```java
int atlasWidth = Math.round(pixelWidth / (u1 - u0));
int atlasHeight = Math.round(pixelHeight / (v1 - v0));
```

- Use `declared.contents().name()` as the stable declared resource ID.
- If the pure index returns no region, return the original declared Sprite with `FALLBACK` and derive a fallback Region from that Sprite's own bounds.
- If it returns a region, retrieve the canonical Sprite by region ID and include that same Region in `Resolution`.
- Never use reflection or Create classes.

Construct it in `ProductionCaptureSource`:

```java
AtlasSpriteResolver atlasSprites = new AtlasSpriteResolver(
        atlasId -> minecraft.getModelManager().getAtlas(atlasId));
this.blocks = new BlockModelExtractor(sprites, atlasSprites, textures);
```

- [ ] **Step 4: Reorder `BlockModelExtractor.captureQuad`**

Change its constructor to accept `AtlasSpriteResolver`. In `captureQuad`, move texture extraction after raw vertex capture:

```java
List<Vertex> rawVertices = consumer.finish();
List<Vec2f> atlasUvs = rawVertices.stream().map(Vertex::uv).toList();
AtlasSpriteResolver.Resolution resolution =
        atlasSpriteResolver.resolve(quad.getSprite(), atlasUvs);
TextureAtlasSprite sprite = resolution.sprite();
SpriteTextureExtractor.Extraction texture = extraction(sprite);
MaterialKey material = MaterialResolver.resolve(descriptor, texture.key());
List<Vec2f> normalizedUvs = AtlasSpriteResolver.normalize(
        atlasUvs, resolution.region());
List<Vertex> vertices = new ArrayList<>(rawVertices.size());
for (int index = 0; index < rawVertices.size(); index++) {
    Vertex vertex = rawVertices.get(index);
    vertices.add(new Vertex(
            LOCAL_TRANSFORM.position(vertex.position()),
            LOCAL_TRANSFORM.normal(vertex.normal()),
            normalizedUvs.get(index),
            vertex.color()));
}
```

Extract the existing cache/register block into this exact helper so `extraction(sprite)` in the snippet is defined:

```java
private SpriteTextureExtractor.Extraction extraction(
        TextureAtlasSprite sprite) throws IOException {
    SpriteTextureExtractor.Extraction texture = spriteCache.get(sprite);
    if (texture == null) {
        texture = spriteExtractor.extract(sprite);
        spriteCache.put(sprite, texture);
        textureRegistry.register(texture.key(), texture.image());
    }
    return texture;
}
```

Add per-export dedup sets:

```java
private final Set<String> reportedRedirects = new HashSet<>();
private final Set<String> reportedFailures = new HashSet<>();
```

For `REDIRECTED`, append INFO `ATLAS_SPRITE_REDIRECTED` once per `declaredId + "->" + resolvedId`. For `FALLBACK`, append WARNING `ATLAS_SPRITE_RESOLUTION_FAILED` once per declared ID and include the UV min/max in the message. Pass the current block position into the diagnostic.

Keep `spriteCache` keyed by the resolved canonical Sprite identity.

- [ ] **Step 5: Run texture and capture tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.texture.*" --tests "com.onecuber.mcgltf.capture.*"
```

Expected: PASS. Existing normal UV tests remain unchanged except for the added regression.

- [ ] **Step 6: Compile the client-facing source sets**

```powershell
.\gradlew.bat compileJava compileTestJava compileTestmodJava
```

Expected: BUILD SUCCESSFUL, proving the NeoForge `TextureAtlas.getTextures()` and model-manager calls match 1.21.1 mappings.

- [ ] **Step 7: Commit atlas integration**

```powershell
git add -- src/main/java/com/onecuber/mcgltf/texture/AtlasSpriteResolver.java src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java src/test/java/com/onecuber/mcgltf/texture/AtlasSpriteResolverPolicyTest.java src/test/java/com/onecuber/mcgltf/texture/UvNormalizationTest.java
git commit -m "fix: preserve connected texture atlas UVs"
```

---

## Task 3: Route Grass Side Overlay Into Stable Node Fragments

**Files:**

- Create: `src/main/java/com/onecuber/mcgltf/capture/BlockPrimitiveRouter.java`
- Create: `src/test/java/com/onecuber/mcgltf/capture/BlockPrimitiveRouterTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java`
- Modify: `src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java`
- Modify: `src/main/java/com/onecuber/mcgltf/scene/CapturedNode.java`
- Modify: `src/test/java/com/onecuber/mcgltf/scene/PrimitiveAccumulatorTest.java`

- [ ] **Step 1: Write failing routing tests**

Create:

```java
package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.onecuber.mcgltf.scene.TextureKey;
import org.junit.jupiter.api.Test;

class BlockPrimitiveRouterTest {
    @Test
    void routesOnlyVanillaGrassSideOverlayGlobally() {
        assertEquals(BlockPrimitiveRouter.Route.GLOBAL_GRASS_SIDE_OVERLAY,
                BlockPrimitiveRouter.route(key(
                        "minecraft:block/grass_block_side_overlay")));
        assertEquals(BlockPrimitiveRouter.Route.SECTION,
                BlockPrimitiveRouter.route(key(
                        "minecraft:block/grass_block_side")));
        assertEquals(BlockPrimitiveRouter.Route.SECTION,
                BlockPrimitiveRouter.route(key(
                        "other:block/grass_block_side_overlay")));
    }

    private static TextureKey key(String id) {
        return new TextureKey(TextureKey.Kind.ATLAS_SPRITE, id,
                "textures/" + id.replace(':', '/') + ".png");
    }
}
```

Add a `PrimitiveAccumulatorTest` assertion that `CapturedNode.Kind.OVERLAY` remains immutable like other kinds.

- [ ] **Step 2: Run tests and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.capture.BlockPrimitiveRouterTest --tests com.onecuber.mcgltf.scene.PrimitiveAccumulatorTest
```

Expected: compilation fails because `BlockPrimitiveRouter` and `OVERLAY` do not exist.

- [ ] **Step 3: Implement the exact texture-ID classifier**

```java
public final class BlockPrimitiveRouter {
    public static final String GRASS_SIDE_OVERLAY_ID =
            "minecraft:block/grass_block_side_overlay";
    public static final String OVERLAY_OBJECT_NAME =
            "selection/grass_side_overlay";

    public enum Route { SECTION, GLOBAL_GRASS_SIDE_OVERLAY }

    public static Route route(TextureKey texture) {
        Objects.requireNonNull(texture, "texture");
        return texture.sourceId().equals(GRASS_SIDE_OVERLAY_ID)
                ? Route.GLOBAL_GRASS_SIDE_OVERLAY
                : Route.SECTION;
    }
}
```

Add `OVERLAY` to `CapturedNode.Kind`.

- [ ] **Step 4: Add separate accumulators to block capture**

Change `BlockModelExtractor.capture` to receive both destinations:

```java
public CaptureResult capture(
        ClientLevel level,
        BlockPos position,
        Selection selection,
        PrimitiveAccumulator sectionAccumulator,
        PrimitiveAccumulator overlayAccumulator)
```

Extend `PendingStream` with `BlockPrimitiveRouter.Route route`. After all quads succeed, append each stream to the selected accumulator:

```java
PrimitiveAccumulator target = stream.route()
        == BlockPrimitiveRouter.Route.GLOBAL_GRASS_SIDE_OVERLAY
        ? overlayAccumulator : sectionAccumulator;
target.append(stream.material(), stream.mode(), stream.vertices());
```

Determine the route from the resolved `texture.key()`, after atlas redirection.

- [ ] **Step 5: Emit one stable overlay fragment per section**

In `DefaultExportPipeline.SectionCursor`, add:

```java
private final PrimitiveAccumulator overlayAccumulator;
```

Initialize it with `BlockPrimitiveRouter.OVERLAY_OBJECT_NAME`. Pass it to `blocks.capture`. In `finish()`, seal both accumulators. Keep the existing CHUNK node behavior, then append an overlay fragment only when nonempty:

```java
nodes.add(new CapturedNode(
        BlockPrimitiveRouter.OVERLAY_OBJECT_NAME,
        CapturedNode.Kind.OVERLAY,
        overlaySealed.primitives(),
        Map.of(
                "layerRole", "grass_side_overlay",
                "scope", "selection",
                "sourceTexture", BlockPrimitiveRouter.GRASS_SIDE_OVERLAY_ID)));
```

Append diagnostics from both seals. Do not change block counters or placeholder policy.

- [ ] **Step 6: Run capture, scene, and pipeline policy tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.capture.*" --tests "com.onecuber.mcgltf.scene.*" --tests "com.onecuber.mcgltf.job.DefaultExportPipelinePolicyTest"
```

Expected: PASS.

- [ ] **Step 7: Commit overlay routing**

```powershell
git add -- src/main/java/com/onecuber/mcgltf/capture/BlockPrimitiveRouter.java src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java src/main/java/com/onecuber/mcgltf/scene/CapturedNode.java src/test/java/com/onecuber/mcgltf/capture/BlockPrimitiveRouterTest.java src/test/java/com/onecuber/mcgltf/scene/PrimitiveAccumulatorTest.java
git commit -m "feat: route grass side overlays separately"
```

---

## Task 4: Coalesce Overlay Fragments Into One glTF Node

**Files:**

- Modify: `src/main/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilder.java`
- Modify: `src/main/java/com/onecuber/mcgltf/gltf/StreamingGltfSession.java`
- Modify: `src/test/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilderTest.java`
- Modify: `src/test/java/com/onecuber/mcgltf/gltf/StreamingGltfSessionTest.java`

- [ ] **Step 1: Write failing glTF coalescing tests**

Add to `StreamingGltfSessionTest`:

```java
@Test
void coalescesSelectionOverlayFragmentsIntoOneNodeAndMesh() throws Exception {
    PrimitiveData primitive = GltfDocumentBuilderTest.triangle();
    Map<String, Object> extras = Map.of(
            "layerRole", "grass_side_overlay",
            "scope", "selection",
            "sourceTexture", "minecraft:block/grass_block_side_overlay");
    CapturedNode first = new CapturedNode(
            "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
            List.of(primitive), extras);
    CapturedNode second = new CapturedNode(
            "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
            List.of(primitive), extras);

    StreamingGltfSession.OutputStatistics statistics;
    try (StreamingGltfSession session = new StreamingGltfSession(
            tempDir, "overlay", Map.of())) {
        session.append(new ChunkBatch(List.of(first), List.of(), BatchCounters.ZERO));
        session.append(new ChunkBatch(List.of(second), List.of(), BatchCounters.ZERO));
        statistics = session.finish();
    }

    JsonObject document = JsonParser.parseString(
            Files.readString(tempDir.resolve("overlay.gltf"))).getAsJsonObject();
    assertEquals(1L, statistics.nodeCount());
    assertEquals(2L, statistics.primitiveCount());
    assertEquals(1, document.getAsJsonArray("meshes").size());
    assertEquals(2, document.getAsJsonArray("meshes").get(0)
            .getAsJsonObject().getAsJsonArray("primitives").size());
    JsonArray nodes = document.getAsJsonArray("nodes");
    long matchingNodes = StreamSupport.stream(nodes.spliterator(), false)
            .map(JsonElement::getAsJsonObject)
            .filter(node -> node.has("name")
                    && node.get("name").getAsString()
                    .equals("selection/grass_side_overlay"))
            .count();
    assertEquals(1L, matchingNodes);
}
```

Import `com.google.gson.JsonArray`, `com.google.gson.JsonElement`, and `java.util.stream.StreamSupport`. In the coalescing test, replace the first vertex color with `new ColorRgba(80, 160, 40, 255)` and use a `MaterialKey` with `AlphaMode.MASK` and cutoff `0.5F`; assert the written primitive has `COLOR_0` and the referenced material has `alphaMode: "MASK"`. This proves the global overlay path preserves biome Tint and transparency semantics.

Add a second test that submits the same overlay name with different Extras and expects `IllegalArgumentException`.

Update `GltfDocumentBuilderTest` expectations to include root node 4 named `Overlays` and the first ordinary child at index 5.

- [ ] **Step 2: Run glTF tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.gltf.*"
```

Expected: failure because overlays are emitted as two nodes and no overlay hierarchy exists.

- [ ] **Step 3: Add the hierarchy root and merge binding**

In `GltfDocumentBuilder` add:

```java
private static final int OVERLAYS_ROOT = 4;
private final Map<String, MergedNode> overlayNodes = new LinkedHashMap<>();

private record MergedNode(
        JsonArray primitives,
        Map<String, Object> extras,
        int nodeIndex,
        int meshIndex) {}
```

Constructor root order:

```java
addHierarchyRoot("Chunks");
addHierarchyRoot("BlockEntities");
addHierarchyRoot("Entities");
addHierarchyRoot("Placeholders");
addHierarchyRoot("Overlays");
```

Include all five roots in the scene. Map `CapturedNode.Kind.OVERLAY` to `OVERLAYS_ROOT`.

Change `addNode` to return `boolean`, meaning “created a new logical node”. For non-overlay nodes, preserve existing behavior and return `true`. For overlays:

1. Build each new primitive JSON immediately.
2. On first name, create one mesh and node, cache its primitive array and immutable Extras, return `true`.
3. On repeated name, require equal Extras, append primitive JSON to the cached array, return `false`.
4. Reject kind/name semantic conflicts rather than silently merging.

- [ ] **Step 4: Count logical nodes once in `StreamingGltfSession`**

Replace unconditional count increment with:

```java
if (!written.isEmpty()
        && documentBuilder.addNode(node, written)) {
    nodeCount = Math.addExact(nodeCount, 1L);
}
```

Primitive count remains per written primitive.

- [ ] **Step 5: Run glTF tests and internal validator tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.gltf.*"
```

Expected: PASS, including one overlay node with two primitives and valid hierarchy indices.

- [ ] **Step 6: Commit glTF coalescing**

```powershell
git add -- src/main/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilder.java src/main/java/com/onecuber/mcgltf/gltf/StreamingGltfSession.java src/test/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilderTest.java src/test/java/com/onecuber/mcgltf/gltf/StreamingGltfSessionTest.java
git commit -m "feat: coalesce selection overlays in glTF"
```

---

## Task 5: Spool One Selection-Wide Overlay Object in OBJ

**Files:**

- Modify: `src/main/java/com/onecuber/mcgltf/obj/StreamingObjSession.java`
- Modify: `src/test/java/com/onecuber/mcgltf/obj/StreamingObjSessionTest.java`
- Modify: `src/test/java/com/onecuber/mcgltf/output/StreamingSceneSessionTest.java`

- [ ] **Step 1: Write failing OBJ spool tests**

Add static imports for `assertFalse` and the existing `assertEquals`/`assertTrue`, then add to `StreamingObjSessionTest`:

```java
@Test
void appendsAllOverlayFragmentsUnderOneObjectAndDeletesSpool() throws Exception {
    PrimitiveData quad = new PrimitiveData(
            ObjTopologyConverterTest.vertices(4), PrimitiveMode.QUADS,
            new int[] {4}, ObjTopologyConverterTest.material());
    Map<String, Object> extras = Map.of(
            "layerRole", "grass_side_overlay",
            "scope", "selection",
            "sourceTexture", "minecraft:block/grass_block_side_overlay");
    CapturedNode overlay = new CapturedNode(
            "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
            List.of(quad), extras);

    StreamingObjSession.OutputStatistics statistics;
    try (StreamingObjSession session = new StreamingObjSession(tempDir, "overlay")) {
        session.append(new ChunkBatch(List.of(overlay), List.of(), BatchCounters.ZERO));
        session.append(new ChunkBatch(List.of(overlay), List.of(), BatchCounters.ZERO));
        statistics = session.finish();
    }

    String obj = Files.readString(tempDir.resolve("overlay.obj"));
    assertEquals(1, occurrences(obj, "o selection_grass_side_overlay"));
    assertEquals(2, occurrences(obj,
            "f -4/-4/-4 -1/-1/-1 -2/-2/-2 -3/-3/-3"));
    assertEquals(1L, statistics.nodeCount());
    assertEquals(2L, statistics.primitiveCount());
    assertFalse(Files.exists(tempDir.resolve(".overlay-grass-overlay.objpart")));
}
```

Add a cancellation/close test that appends one overlay fragment without calling `finish()`, closes the session, and asserts the spool file is deleted.

Add a vertex-color regression by replacing the first Quad vertex color with `new ColorRgba(255, 0, 0, 255)` and asserting the OBJ contains:

```text
v 0.0 0.0 0.0 1.0 0.0 0.0
```

This is the de facto OBJ RGB vertex extension Blender understands; texture alpha remains represented by MTL `map_d`.

Add helper:

```java
private static int occurrences(String text, String token) {
    return (text.length() - text.replace(token, "").length()) / token.length();
}
```

- [ ] **Step 2: Run OBJ tests and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.obj.StreamingObjSessionTest
```

Expected: two object declarations and absolute indices, or missing spool behavior.

- [ ] **Step 3: Add lazy overlay spooling**

Add fields:

```java
private final Path overlayFragmentPath;
private BufferedWriter overlayWriter;
private boolean overlayWritten;
```

Set path in the constructor:

```java
overlayFragmentPath = normalizedRoot.resolve(
        "." + fileName + "-grass-overlay.objpart");
```

In `append`, route `CapturedNode.Kind.OVERLAY` to `writeOverlay(node)`; other kinds keep the existing absolute-index path.

`writeOverlay` must:

- Require exact name `selection/grass_side_overlay`.
- Create the fragment lazily with `CREATE_NEW`.
- Write vertices, UVs, and normals to the fragment.
- Write every `v` line as `v x y z r g b`, with RGB divided by `255.0F`; use the same format for ordinary and overlay geometry so biome Tint survives OBJ import.
- Write each face with relative indices `index - primitive.vertices().size()`.
- Write line indices with the same relative rule.
- Reuse global material names and emit `g`/`usemtl` inside the fragment.
- Increment `nodeCount` only when `overlayWritten` changes from false to true.
- Increment primitive, face, and line counts normally.

For a four-vertex face `[0, 3, 2, 1]`, write:

```text
f -4/-4/-4 -1/-1/-1 -2/-2/-2 -3/-3/-3
```

- [ ] **Step 4: Append the fragment without loading it into memory**

In `finish()`:

1. Close the fragment writer if present.
2. If `overlayWritten`, write one object header to the main writer.
3. Copy the fragment with a `BufferedReader` and an 8192-character buffer.
4. Delete the fragment in `finally`.
5. Close the main writer and write MTL.

Use this copy loop:

```java
try (Reader reader = Files.newBufferedReader(
        overlayFragmentPath, StandardCharsets.UTF_8)) {
    char[] buffer = new char[8192];
    for (int read; (read = reader.read(buffer)) >= 0; ) {
        writer.write(buffer, 0, read);
    }
} finally {
    Files.deleteIfExists(overlayFragmentPath);
}
```

Refactor `writeVertices` to accept its destination writer so body and spool use the identical RGB-capable vertex encoding. `close()` must close both writers, preserve the first exception with suppressed exceptions, and always attempt `Files.deleteIfExists(overlayFragmentPath)`. Prefix spool-related IO messages with `GLOBAL_OVERLAY_SPOOL_FAILED`.

- [ ] **Step 5: Run OBJ and shared-session tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.obj.*" --tests "com.onecuber.mcgltf.output.*"
```

Expected: PASS. Existing ordinary object absolute indices remain unchanged.

- [ ] **Step 6: Commit OBJ spooling**

```powershell
git add -- src/main/java/com/onecuber/mcgltf/obj/StreamingObjSession.java src/test/java/com/onecuber/mcgltf/obj/StreamingObjSessionTest.java src/test/java/com/onecuber/mcgltf/output/StreamingSceneSessionTest.java
git commit -m "feat: spool one global grass overlay object"
```

---

## Task 6: Isolate Wand GUI Keyboard and IME Events

**Files:**

- Create: `src/main/java/com/onecuber/mcgltf/client/wand/WandKeyboardPolicy.java`
- Create: `src/test/java/com/onecuber/mcgltf/client/wand/WandKeyboardPolicyTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java`
- Modify: `src/test/java/com/onecuber/mcgltf/client/wand/ExportWandScreenBindingTest.java`

- [ ] **Step 1: Write failing key-action policy tests**

Create:

```java
package com.onecuber.mcgltf.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class WandKeyboardPolicyTest {
    @Test
    void mapsOnlyEscapeEnterAndTabToScreenActions() {
        assertEquals(WandKeyboardPolicy.Action.CLOSE,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_ESCAPE, true));
        assertEquals(WandKeyboardPolicy.Action.COMMIT,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_ENTER, true));
        assertEquals(WandKeyboardPolicy.Action.COMMIT,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_KP_ENTER, true));
        assertEquals(WandKeyboardPolicy.Action.MOVE_FOCUS,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_TAB, false));
    }

    @Test
    void forwardsEditingKeysOnlyWhenAnEditBoxIsFocused() {
        assertEquals(WandKeyboardPolicy.Action.FORWARD_TO_EDITOR,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_E, true));
        assertEquals(WandKeyboardPolicy.Action.FORWARD_TO_EDITOR,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_BACKSPACE, true));
        assertEquals(WandKeyboardPolicy.Action.CONSUME,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_E, false));
        assertEquals(WandKeyboardPolicy.Action.CONSUME,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_1, false));
        assertEquals(WandKeyboardPolicy.Action.CONSUME,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_W, false));
    }
}
```

Extend `ExportWandScreenBindingTest` to assert the source contains `this.passEvents = false`, `WandKeyboardPolicy.keyPressed`, and does not contain the unsafe sequence:

```java
assertFalse(source.contains(
        "super.keyPressed(keyCode, scanCode, modifiers);\n        return true;"));
```

- [ ] **Step 2: Run tests and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.client.wand.WandKeyboardPolicyTest --tests com.onecuber.mcgltf.client.wand.ExportWandScreenBindingTest
```

Expected: compilation fails because `WandKeyboardPolicy` does not exist.

- [ ] **Step 3: Implement the pure action policy**

```java
public final class WandKeyboardPolicy {
    public enum Action {
        CLOSE,
        COMMIT,
        MOVE_FOCUS,
        FORWARD_TO_EDITOR,
        CONSUME
    }

    public static Action keyPressed(int keyCode, boolean editorFocused) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> Action.CLOSE;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> Action.COMMIT;
            case GLFW.GLFW_KEY_TAB -> Action.MOVE_FOCUS;
            default -> editorFocused
                    ? Action.FORWARD_TO_EDITOR : Action.CONSUME;
        };
    }

    private WandKeyboardPolicy() {}
}
```

- [ ] **Step 4: Replace unsafe Screen propagation**

Set `this.passEvents = false` in the `ExportWandScreen` constructor.

Add:

```java
private EditBox focusedEditBox() {
    if (nameField != null && nameField.isFocused()) {
        return nameField;
    }
    for (EditBox field : coordinateFields) {
        if (field.isFocused()) {
            return field;
        }
    }
    return null;
}

private void commitFocusedEditBox() {
    for (int index = 0; index < coordinateFields.size(); index++) {
        if (coordinateFields.get(index).isFocused()) {
            commitEndpoint(endpoint(index));
            return;
        }
    }
    if (nameField != null && nameField.isFocused() && isNameValid()) {
        commitExportName();
    }
}
```

Implement key routing:

```java
@Override
public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    EditBox editor = focusedEditBox();
    return switch (WandKeyboardPolicy.keyPressed(keyCode, editor != null)) {
        case CLOSE -> {
            onClose();
            yield true;
        }
        case COMMIT -> {
            commitFocusedEditBox();
            yield true;
        }
        case MOVE_FOCUS -> {
            super.keyPressed(keyCode, scanCode, modifiers);
            yield true;
        }
        case FORWARD_TO_EDITOR -> {
            editor.keyPressed(keyCode, scanCode, modifiers);
            yield true;
        }
        case CONSUME -> true;
    };
}
```

Implement release and character routing without calling container super methods:

```java
@Override
public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
    EditBox editor = focusedEditBox();
    if (editor != null) {
        editor.keyReleased(keyCode, scanCode, modifiers);
    }
    return true;
}

@Override
public boolean charTyped(char character, int modifiers) {
    EditBox editor = focusedEditBox();
    if (editor != null) {
        editor.charTyped(character, modifiers);
    }
    return true;
}
```

Do not call `super.keyPressed` for E, WASD, hotbar keys, Q, F, clipboard keys, or ordinary printable characters. Ctrl+A/C/V/X reach the focused `EditBox` through `FORWARD_TO_EDITOR`; committed English and Chinese text reach it through `charTyped`.

- [ ] **Step 5: Add Unicode and leakage regressions**

Keep the existing `ExportNameTest.acceptsUnicodeAndSafePunctuation`. Add source-contract assertions that `charTyped` calls `editor.charTyped` and `keyReleased` calls `editor.keyReleased`, while neither method calls `super`.

Run:

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.client.wand.*" --tests com.onecuber.mcgltf.output.ExportNameTest
```

Expected: PASS.

- [ ] **Step 6: Commit keyboard isolation**

```powershell
git add -- src/main/java/com/onecuber/mcgltf/client/wand/WandKeyboardPolicy.java src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java src/test/java/com/onecuber/mcgltf/client/wand/WandKeyboardPolicyTest.java src/test/java/com/onecuber/mcgltf/client/wand/ExportWandScreenBindingTest.java
git commit -m "fix: isolate wand text input from game shortcuts"
```

---

## Task 7: Document the New Hierarchy and Manual Visual Gate

**Files:**

- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`

- [ ] **Step 1: Update README contracts**

Make these exact documentation changes:

- Change output hierarchy to `Chunks`, `BlockEntities`, `Entities`, `Placeholders`, `Overlays`.
- State that Sprite-shifted quads are resolved from actual atlas UVs, enabling Create `*_connected` texture tables without a Create dependency.
- State that vanilla grass side overlays remain geometrically separate and are emitted as `selection/grass_side_overlay`.
- State that the wand GUI consumes game shortcuts while open and supports Unicode names through `charTyped`.
- Keep the explicit statement that final visual correctness is manually judged.

- [ ] **Step 2: Extend the manual matrix**

Add rows:

```markdown
| Create 连纹 | 拼接安山机壳、黄铜机壳、储液罐和保险库 | 游戏与 Blender 中边、角、中心纹理一致，不退回单块重复 |
| 草侧 Overlay 对象 | 导出跨多个 Section 的草地区域 | Outliner 只有一个 `selection/grass_side_overlay`；隐藏后只剩底层侧面 |
| 英文 E 输入 | 名称框输入 `create_export` | 字符正常进入名称，GUI 不关闭，物品栏不弹出 |
| 中文输入法 | 名称框输入中文并提交 | 中文名称保存并用于输出目录，快捷键不泄漏 |
| 关闭后恢复 | Esc 关闭 GUI 后按 E、WASD、1 至 9 | 游戏快捷键立即恢复，无粘滞状态 |
```

Clearly label these rows “项目所有者人工视觉/交互验收”，not automated pass criteria.

- [ ] **Step 3: Run documentation policy tests**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.DocumentationPolicyTest --tests com.onecuber.mcgltf.McGltfMetadataTest
```

Expected: PASS.

- [ ] **Step 4: Commit documentation**

```powershell
git add -- README.md docs/testing/manual-client-matrix.md
git commit -m "docs: add connected texture and input validation matrix"
```

---

## Task 8: Full Mechanical Verification and Candidate Audit

**Files:**

- No production edits expected.
- Update only a failing test or implementation file that directly violates this plan; do not perform unrelated refactors.

- [ ] **Step 1: Run focused regression groups**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.texture.*" --tests "com.onecuber.mcgltf.capture.*" --tests "com.onecuber.mcgltf.gltf.*" --tests "com.onecuber.mcgltf.obj.*" --tests "com.onecuber.mcgltf.output.*" --tests "com.onecuber.mcgltf.client.wand.*"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run clean full build**

```powershell
.\gradlew.bat clean test build
```

Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 3: Run dedicated-server isolation smoke test**

```powershell
.\gradlew.bat runServerSmoke
```

Expected: process exits successfully and logs `MINETOMESH_SERVER_READY`; no client class linkage error.

- [ ] **Step 4: Audit the production JAR**

```powershell
$jar = Get-ChildItem build\libs\mcgltf-*.jar | Sort-Object LastWriteTime -Descending | Select-Object -First 1
jar tf $jar.FullName | Select-String "mcgltf_test|TestBakedModel|create/|catnip/|\.objpart$"
jdeps --multi-release 21 $jar.FullName | Select-String "com\.simibubi\.create|net\.createmod\.catnip"
```

Expected: both searches print no output. The JAR may contain MineToMesh classes named `AtlasSpriteResolver`, but no Create/Catnip classes or dependencies.

- [ ] **Step 5: Verify repository cleanliness and commit history**

```powershell
git status --short
git log -8 --oneline
```

Expected: clean worktree. Recent commits show separate units for atlas index, atlas integration, overlay routing, glTF merge, OBJ spool, keyboard isolation, and docs.

- [ ] **Step 6: Hand off the candidate for owner visual validation**

Do not claim Create textures or Blender appearance visually pass. Report only:

```text
Mechanical verification: passed
Create connected-texture visual validation: pending owner review
Grass overlay Blender validation: pending owner review
IME and shortcut real-client validation: pending owner review
```

Provide the candidate JAR and the updated `docs/testing/manual-client-matrix.md` to the project owner. Final release approval waits for their game/Blender comparison.
