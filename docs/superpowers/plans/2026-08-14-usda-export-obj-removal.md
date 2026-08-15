# USDA Export and OBJ Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not dispatch subagents for this project. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a streaming USDA scene writer that preserves source quads, hierarchy, UVs, tint and PreviewSurface materials, then remove all current OBJ/MTL output code and contracts.

**Architecture:** Pure USD helpers convert source topology and material keys without Minecraft client access. `UsdaMeshSpool` streams large face arrays to bounded temporary fragments, while `StreamingUsdaSession` assembles category hierarchy and globally batches block entities and overlays. `StreamingSceneSession` owns glTF and USDA as one transactional pair.

**Tech Stack:** Java 21, JUnit 5, USDA 1.0 text syntax, UsdGeom/UsdShade/UsdPreviewSurface schemas, Minecraft 1.21.1/NeoForge, Gson, Gradle, Blender 5.2 manual validation.

---

## File map

- Create `src/main/java/com/nebysse/minetomesh/usd/UsdaNames.java`: legal prim identifiers and stable material hashes.
- Create `src/main/java/com/nebysse/minetomesh/usd/UsdaTopology.java`: source topology to USD face/curve topology.
- Create `src/main/java/com/nebysse/minetomesh/usd/UsdaText.java`: locale-stable scalar, string and asset serialization.
- Create `src/main/java/com/nebysse/minetomesh/usd/UsdaMaterialWriter.java`: PreviewSurface networks and material paths.
- Create `src/main/java/com/nebysse/minetomesh/usd/UsdaMeshSpool.java`: bounded-memory surface attribute fragments and count validation.
- Create `src/main/java/com/nebysse/minetomesh/usd/UsdaCurveSpool.java`: bounded-memory line and line-strip fragments.
- Create `src/main/java/com/nebysse/minetomesh/usd/StreamingUsdaSession.java`: stage hierarchy, category routing, global batching and cleanup.
- Create matching tests under `src/test/java/com/nebysse/minetomesh/usd/`.
- Modify `src/main/java/com/nebysse/minetomesh/output/StreamingSceneSession.java`: replace OBJ lifecycle with USDA.
- Modify `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java`: root extras and USDA output integration.
- Modify output/report/documentation tests to require `.usda` and reject `.obj`/`.mtl`.
- Delete `src/main/java/com/nebysse/minetomesh/obj/` and `src/test/java/com/nebysse/minetomesh/obj/`.
- Modify `README.md`, `docs/testing/manual-client-matrix.md`, and current documentation policy tests; preserve historical `docs/releases/` unchanged.

### Task 1: Implement legal names, text escaping and topology conversion

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/usd/UsdaNames.java`
- Create: `src/main/java/com/nebysse/minetomesh/usd/UsdaText.java`
- Create: `src/main/java/com/nebysse/minetomesh/usd/UsdaTopology.java`
- Test: `src/test/java/com/nebysse/minetomesh/usd/UsdaNamesTest.java`
- Test: `src/test/java/com/nebysse/minetomesh/usd/UsdaTopologyTest.java`

- [ ] **Step 1: Write failing naming and topology tests**

```java
@Test
void createsLegalStableUsdIdentifiers() {
    assertEquals("chunk_1", UsdaNames.identifier("区段/chunk 1"));
    assertEquals("unnamed", UsdaNames.identifier("空白"));
    assertEquals("_12rail", UsdaNames.identifier("12rail"));
    assertEquals(UsdaNames.material(material()), UsdaNames.material(material()));
    assertTrue(UsdaNames.material(material()).matches("m_[A-Za-z0-9_]+_[0-9a-f]{16}"));
}

@Test
void preservesQuadAndExpandsStripWinding() {
    UsdaTopology.Surface quad = UsdaTopology.surface(
            PrimitiveMode.QUADS, new int[] {4, 8}, "quad");
    assertArrayEquals(new int[] {4, 4, 4}, quad.faceVertexCounts());
    assertArrayEquals(IntStream.range(0, 12).toArray(), quad.faceVertexIndices());

    UsdaTopology.Surface strip = UsdaTopology.surface(
            PrimitiveMode.TRIANGLE_STRIP, new int[] {4}, "strip");
    assertArrayEquals(new int[] {3, 3}, strip.faceVertexCounts());
    assertArrayEquals(new int[] {0, 1, 2, 2, 1, 3}, strip.faceVertexIndices());
}

@Test
void convertsLineStreamsToIndependentCurveCounts() {
    assertArrayEquals(new int[] {2, 2}, UsdaTopology.curves(
            PrimitiveMode.LINES, new int[] {4}, "lines").curveVertexCounts());
    assertArrayEquals(new int[] {4, 3}, UsdaTopology.curves(
            PrimitiveMode.LINE_STRIP, new int[] {4, 3}, "strip").curveVertexCounts());
}
```

Also test `UsdaText.quoted("a\"b\\c") == "\"a\\\"b\\\\c\""`, finite float formatting with `Locale.ROOT`, and `UsdaText.asset("textures/a b.png")` escaping.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.nebysse.minetomesh.usd.UsdaNamesTest" --tests "com.nebysse.minetomesh.usd.UsdaTopologyTest"
```

Expected: compilation failure because the USD package is absent.

- [ ] **Step 3: Implement the pure helpers**

Implement `UsdaTopology` with explicit per-stream boundaries and cloned output arrays:

```java
public final class UsdaTopology {
    private UsdaTopology() {}

    public static Surface surface(PrimitiveMode mode, int[] counts, String objectId) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(objectId, "objectId");
        List<Integer> faceCounts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int offset = 0;
        for (int count : counts) {
            switch (mode) {
                case QUADS -> {
                    int complete = count - count % 4;
                    for (int base = 0; base < complete; base += 4) {
                        faceCounts.add(4);
                        for (int corner = 0; corner < 4; corner++) {
                            indices.add(offset + base + corner);
                        }
                    }
                }
                case TRIANGLES -> {
                    int complete = count - count % 3;
                    for (int base = 0; base < complete; base += 3) {
                        faceCounts.add(3);
                        indices.add(offset + base);
                        indices.add(offset + base + 1);
                        indices.add(offset + base + 2);
                    }
                }
                case TRIANGLE_FAN -> {
                    for (int corner = 1; corner < count - 1; corner++) {
                        faceCounts.add(3);
                        indices.add(offset);
                        indices.add(offset + corner);
                        indices.add(offset + corner + 1);
                    }
                }
                case TRIANGLE_STRIP -> {
                    for (int corner = 0; corner < count - 2; corner++) {
                        faceCounts.add(3);
                        if ((corner & 1) == 0) {
                            indices.add(offset + corner);
                            indices.add(offset + corner + 1);
                        } else {
                            indices.add(offset + corner + 1);
                            indices.add(offset + corner);
                        }
                        indices.add(offset + corner + 2);
                    }
                }
                case LINES, LINE_STRIP -> throw new IllegalArgumentException(
                        "Line topology cannot be written as a USD surface: " + objectId);
            }
            offset = Math.addExact(offset, count);
        }
        return new Surface(toIntArray(faceCounts), toIntArray(indices));
    }

    public static Curves curves(PrimitiveMode mode, int[] counts, String objectId) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(objectId, "objectId");
        List<Integer> curveCounts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int offset = 0;
        for (int count : counts) {
            switch (mode) {
                case LINES -> {
                    int complete = count - count % 2;
                    for (int base = 0; base < complete; base += 2) {
                        curveCounts.add(2);
                        indices.add(offset + base);
                        indices.add(offset + base + 1);
                    }
                }
                case LINE_STRIP -> {
                    if (count >= 2) {
                        curveCounts.add(count);
                        for (int vertex = 0; vertex < count; vertex++) {
                            indices.add(offset + vertex);
                        }
                    }
                }
                case QUADS, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP ->
                        throw new IllegalArgumentException(
                                "Surface topology cannot be written as USD curves: " + objectId);
            }
            offset = Math.addExact(offset, count);
        }
        return new Curves(toIntArray(curveCounts), toIntArray(indices));
    }

    private static int[] toIntArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    public record Surface(int[] faceVertexCounts, int[] faceVertexIndices) {
        public Surface {
            faceVertexCounts = faceVertexCounts.clone();
            faceVertexIndices = faceVertexIndices.clone();
        }
        @Override public int[] faceVertexCounts() { return faceVertexCounts.clone(); }
        @Override public int[] faceVertexIndices() { return faceVertexIndices.clone(); }
    }

    public record Curves(int[] curveVertexCounts, int[] vertexIndices) {
        public Curves {
            curveVertexCounts = curveVertexCounts.clone();
            vertexIndices = vertexIndices.clone();
        }
        @Override public int[] curveVertexCounts() { return curveVertexCounts.clone(); }
        @Override public int[] vertexIndices() { return vertexIndices.clone(); }
    }
}
```

Add imports for `ArrayList`, `List`, `Objects`, and `PrimitiveMode`. The conversion discards only incomplete trailing vertices inside each source stream, matching the existing topology policy.

`UsdaNames.identifier` must replace every non-ASCII alphanumeric/underscore run with `_`, trim repeated underscores, prefix `_` when the first character is a digit, and return `unnamed` when empty. `UsdaNames.material` must hash a canonical string containing every `MaterialKey` field and return a readable texture-derived prefix plus the first 16 lowercase SHA-256 hex digits.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all helper tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/usd/UsdaNames.java src/main/java/com/nebysse/minetomesh/usd/UsdaText.java src/main/java/com/nebysse/minetomesh/usd/UsdaTopology.java src/test/java/com/nebysse/minetomesh/usd/UsdaNamesTest.java src/test/java/com/nebysse/minetomesh/usd/UsdaTopologyTest.java
git commit -m "feat: add USDA topology and naming primitives"
```

### Task 2: Write standard PreviewSurface materials

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/usd/UsdaMaterialWriter.java`
- Test: `src/test/java/com/nebysse/minetomesh/usd/UsdaMaterialWriterTest.java`

- [ ] **Step 1: Write a failing material-network test**

Create one MASK, tinted, emissive, nearest material and assert the emitted fragment contains:

```java
String usd = UsdaMaterialWriter.fragment(material);
assertTrue(usd.contains("def Material \"" + UsdaNames.material(material) + "\""));
assertTrue(usd.contains("uniform token info:id = \"UsdPreviewSurface\""));
assertTrue(usd.contains("uniform token info:id = \"UsdUVTexture\""));
assertTrue(usd.contains("uniform token info:id = \"UsdPrimvarReader_float2\""));
assertTrue(usd.contains("uniform token info:id = \"UsdPrimvarReader_float4\""));
assertTrue(usd.contains("inputs:scale.connect"));
assertTrue(usd.contains("inputs:opacityThreshold = 0.5"));
assertTrue(usd.contains("inputs:emissiveColor.connect"));
assertTrue(usd.contains("asset inputs:file = @textures/minecraft/block/grass.png@"));
assertTrue(usd.contains("custom string minetomesh:samplerMode = \"NEAREST\""));
```

Add OPAQUE and BLEND cases proving cutoff appears only for MASK and Alpha still connects for BLEND.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.usd.UsdaMaterialWriterTest
```

Expected: compilation failure because `UsdaMaterialWriter` is absent.

- [ ] **Step 3: Implement a deterministic material fragment**

The writer must produce this graph shape with the actual stable material path substituted:

```usda
def Material "m_name_hash"
{
    token outputs:surface.connect = <Preview.outputs:surface>
    custom string minetomesh:samplerMode = "NEAREST"

    def Shader "Preview"
    {
        uniform token info:id = "UsdPreviewSurface"
        color3f inputs:diffuseColor.connect = <Texture.outputs:rgb>
        float inputs:opacity.connect = <Texture.outputs:a>
        float inputs:opacityThreshold = 0.5
        token outputs:surface
    }

    def Shader "Texture"
    {
        uniform token info:id = "UsdUVTexture"
        asset inputs:file = @textures/...png@
        token inputs:sourceColorSpace = "sRGB"
        float2 inputs:st.connect = <ReadSt.outputs:result>
        float4 inputs:scale.connect = <ReadTint.outputs:result>
        float3 outputs:rgb
        float outputs:a
    }

    def Shader "ReadSt"
    {
        uniform token info:id = "UsdPrimvarReader_float2"
        string inputs:varname = "st"
        float2 outputs:result
    }

    def Shader "ReadTint"
    {
        uniform token info:id = "UsdPrimvarReader_float4"
        string inputs:varname = "minetomeshTint"
        float4 inputs:fallback = (1, 1, 1, 1)
        float4 outputs:result
    }
}
```

Use absolute USD connections rooted at `/MineToMesh/Materials/<materialName>/...` so references remain valid regardless of lexical nesting. For OPAQUE, set opacity to `1` and do not connect texture Alpha. For MASK and BLEND, connect Alpha; only MASK writes its cutoff. When `emissive()` is true, connect texture RGB to `emissiveColor`. Add custom blend semantic and sampler attributes.

- [ ] **Step 4: Run focused tests and verify GREEN**

Expected: all material cases pass and output order is deterministic.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/usd/UsdaMaterialWriter.java src/test/java/com/nebysse/minetomesh/usd/UsdaMaterialWriterTest.java
git commit -m "feat: write USDA preview materials"
```

### Task 3: Build a bounded-memory mesh spool

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/usd/UsdaMeshSpool.java`
- Create: `src/main/java/com/nebysse/minetomesh/usd/UsdaCurveSpool.java`
- Test: `src/test/java/com/nebysse/minetomesh/usd/UsdaMeshSpoolTest.java`
- Test: `src/test/java/com/nebysse/minetomesh/usd/UsdaCurveSpoolTest.java`

- [ ] **Step 1: Write failing tests for Quad, UV flip, tint and cleanup**

```java
@Test
void appendsQuadWithFaceVaryingAttributesAndFlippedT() throws Exception {
    Path fragment = tempDir.resolve("quad.usdapart");
    try (UsdaMeshSpool spool = new UsdaMeshSpool(fragment, false)) {
        spool.append(quad());
        String mesh = spool.finishFragment("Mesh");
        assertTrue(mesh.contains("int[] faceVertexCounts = [4]"));
        assertTrue(mesh.contains("int[] faceVertexIndices = [0, 1, 2, 3]"));
        assertTrue(mesh.contains("texCoord2f[] primvars:st"));
        assertTrue(mesh.contains("(0, 1)"));
        assertTrue(mesh.contains("interpolation = \"faceVarying\""));
        assertTrue(mesh.contains("color4f[] primvars:minetomeshTint"));
        assertTrue(mesh.contains("uniform token subdivisionScheme = \"none\""));
    }
    assertFalse(Files.exists(fragment));
}
```

Add tests that two appends offset the second topology indices, different materials create deterministic GeomSubset face lists when the spool is multi-material, and closing without `finishFragment` removes all attribute fragments. In `UsdaCurveSpoolTest`, append one `LINES` primitive and one `LINE_STRIP` primitive, assert `BasisCurves`, `type="linear"`, `wrap="nonperiodic"`, correct `curveVertexCounts`, UV/tint preservation, and cleanup.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.usd.UsdaMeshSpoolTest
```

Expected: compilation failure because the spool is absent.

- [ ] **Step 3: Implement fragment-backed arrays and invariants**

`UsdaMeshSpool` owns separate hidden files for points, normals, counts, indices, UVs, tint and per-material face indices. It tracks `pointCount`, `faceCount`, `faceVertexCount`, `primitiveCount`, `doubleSided`, and insertion-ordered materials.

For each `PrimitiveData` surface:

1. Call `UsdaTopology.surface`.
2. Append every source point exactly once.
3. Add current `pointCount` to every topology index.
4. Append normals, UVs and colors in face-index order so each list has exactly `faceVertexCount` values.
5. Flip UV with `(u, 1-v)`.
6. Add generated face indices to that material's subset.
7. Validate all counts with `Math.addExact`.

`finishFragment(String meshName)` derives material paths from `UsdaNames.material` and must reject these conditions with `IOException` before returning text:

```text
sum(faceVertexCounts) != faceVertexIndices.length
normals.length != faceVertexIndices.length
st.length != faceVertexIndices.length
tint.length != faceVertexIndices.length
any faceVertexIndex < 0 or >= points.length
```

The returned Mesh must bind its sole material directly or create `GeomSubset` children with `familyName = "materialBind"` and `rel material:binding` for multiple materials. Always write `subdivisionScheme="none"` and the spool's `doubleSided` value.

`UsdaCurveSpool` follows the same lifecycle with point, curve-count, UV and tint fragments. It uses `UsdaTopology.curves`, emits one `BasisCurves`, binds one material per spool, and validates that `sum(curveVertexCounts)` equals the emitted curve vertex count.

- [ ] **Step 4: Run spool tests and verify GREEN**

Run the command from Step 2. Expected: all spool and cleanup tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/usd/UsdaMeshSpool.java src/main/java/com/nebysse/minetomesh/usd/UsdaCurveSpool.java src/test/java/com/nebysse/minetomesh/usd/UsdaMeshSpoolTest.java src/test/java/com/nebysse/minetomesh/usd/UsdaCurveSpoolTest.java
git commit -m "feat: stream USDA geometry attributes to spools"
```

### Task 4: Assemble the USDA stage and global batches

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/usd/StreamingUsdaSession.java`
- Test: `src/test/java/com/nebysse/minetomesh/usd/StreamingUsdaSessionTest.java`

- [ ] **Step 1: Write failing end-to-end session tests**

Port the behavioral contracts from `StreamingObjSessionTest` to USDA:

- one Quad writes `#usda 1.0`, Y-Up, metersPerUnit, `/MineToMesh/Chunks`, one four-sided face and a bound material;
- two block entities with the same complete `MaterialKey` produce one global Mesh under `BlockEntities`;
- different block-entity materials produce distinct meshes;
- ordinary entities remain separate Xforms;
- two Overlay batches produce one `selection_grass_side_overlay` Xform;
- Unicode names sanitize deterministically;
- `close()` without `finish()` removes every `.usdapart`.

Use assertions such as:

```java
assertEquals(1, occurrences(usd, "def Xform \"selection_grass_side_overlay\""));
assertEquals(1, occurrences(usd, "def Mesh \"BlockEntities_"));
assertFalse(hasPartFiles(tempDir));
assertEquals(2L, statistics.primitiveCount());
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.usd.StreamingUsdaSessionTest
```

Expected: compilation failure because the session is absent.

- [ ] **Step 3: Implement category routing and final Stage assembly**

The public lifecycle must match the existing writer pattern:

```java
public final class StreamingUsdaSession implements Closeable {
    public StreamingUsdaSession(Path root, String name,
            Map<String, Object> rootExtras) throws IOException;
    public void append(ChunkBatch batch) throws IOException;
    public OutputStatistics finish() throws IOException;
    public void close() throws IOException;

    public record OutputStatistics(
            long nodeCount,
            long primitiveCount,
            long faceCount,
            long lineCount,
            Path usdaPath) {}
}
```

Routing rules:

- `CHUNK`, `ENTITY`, and `PLACEHOLDER`: immediately write one sanitized Xform fragment under their fixed category; use one `UsdaMeshSpool` per double-sided bucket inside that node.
- `BLOCK_ENTITY`: append every surface primitive to a global mesh spool keyed by complete `MaterialKey` and double-sided value; append line primitives to global curve spools keyed by complete `MaterialKey`; emit one Mesh or BasisCurves per key at finish.
- `OVERLAY`: require `BlockPrimitiveRouter.OVERLAY_OBJECT_NAME`, append surfaces and lines to global overlay spools, and emit one selection-level Xform at finish.
- line primitives from ordinary nodes use short-lived `UsdaCurveSpool` instances and emit `BasisCurves` with `type="linear"` and `wrap="nonperiodic"`.

`finish()` writes a new `<name>.usda` with:

1. Stage metadata and `defaultPrim`.
2. `/MineToMesh` root custom attributes converted from supported root extras (`String`, `Number`, `Boolean`, and flat lists).
3. Category Xforms in fixed order.
4. Material Scope fragments sorted by stable material name.
5. Closing braces.
6. Count and reference validation.
7. Part-file cleanup.

On any exception, close all open writers, delete the partial `.usda`, delete all fragments, and rethrow the primary exception with cleanup failures suppressed.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all stage, batching and cleanup tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/usd/StreamingUsdaSession.java src/test/java/com/nebysse/minetomesh/usd/StreamingUsdaSessionTest.java
git commit -m "feat: add streaming USDA scene export"
```

### Task 5: Replace OBJ with USDA in the transactional output pair

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/output/StreamingSceneSession.java`
- Modify: `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java`
- Modify: `src/test/java/com/nebysse/minetomesh/output/StreamingSceneSessionTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/output/OutputTransactionTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/job/DefaultExportPipelinePolicyTest.java`

- [ ] **Step 1: Change output tests first**

Update `StreamingSceneSessionTest` to expect:

```java
assertEquals(1, output.gltf().primitiveCount());
assertEquals(1, output.usda().primitiveCount());
assertTrue(Files.exists(tempDir.resolve("sample.gltf")));
assertTrue(Files.exists(tempDir.resolve("sample.bin")));
assertTrue(Files.exists(tempDir.resolve("sample.usda")));
assertFalse(Files.exists(tempDir.resolve("sample.obj")));
assertFalse(Files.exists(tempDir.resolve("sample.mtl")));
```

Update transaction/pipeline policy tests to require `formats=[gltf, usda]` and `sourceTopologyPreservedInUsda=true`.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.output.StreamingSceneSessionTest --tests com.nebysse.minetomesh.output.OutputTransactionTest --tests com.nebysse.minetomesh.job.DefaultExportPipelinePolicyTest
```

Expected: failures still reference OBJ output.

- [ ] **Step 3: Replace the writer lifecycle**

`StreamingSceneSession` fields become:

```java
private final StreamingGltfSession gltf;
private final StreamingUsdaSession usda;
```

Open glTF first, then USDA; if USDA construction fails, close glTF. Append every batch to both. `finish()` returns:

```java
public record OutputStatistics(
        StreamingGltfSession.OutputStatistics gltf,
        StreamingUsdaSession.OutputStatistics usda) {}
```

`close()` attempts both writers and suppresses secondary failures exactly as the current implementation does.

In `DefaultExportPipeline.rootExtras`, replace OBJ metadata with USDA metadata. Continue reporting glTF node and primitive counts in `ExportSummary` so public summary semantics do not change.

- [ ] **Step 4: Run focused and full tests**

Run the command from Step 2, then:

```powershell
.\gradlew.bat test
```

Expected: all tests pass before deleting OBJ classes.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/output/StreamingSceneSession.java src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java src/test/java/com/nebysse/minetomesh/output/StreamingSceneSessionTest.java src/test/java/com/nebysse/minetomesh/output/OutputTransactionTest.java src/test/java/com/nebysse/minetomesh/job/DefaultExportPipelinePolicyTest.java
git commit -m "feat: pair glTF and USDA outputs"
```

### Task 6: Delete OBJ implementation and enforce the removal boundary

**Files:**
- Delete: `src/main/java/com/nebysse/minetomesh/obj/ObjNames.java`
- Delete: `src/main/java/com/nebysse/minetomesh/obj/ObjTopologyConverter.java`
- Delete: `src/main/java/com/nebysse/minetomesh/obj/StreamingObjSession.java`
- Delete: `src/test/java/com/nebysse/minetomesh/obj/ObjTopologyConverterTest.java`
- Delete: `src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java`
- Create: `src/test/java/com/nebysse/minetomesh/output/ObjRemovalPolicyTest.java`

- [ ] **Step 1: Write the removal policy test before deletion**

```java
@Test
void currentSourceAndOutputContractsContainNoObjWriter() throws Exception {
    Path root = projectRoot();
    assertFalse(Files.exists(root.resolve(
            "src/main/java/com/nebysse/minetomesh/obj")));
    String scene = Files.readString(root.resolve(
            "src/main/java/com/nebysse/minetomesh/output/StreamingSceneSession.java"));
    assertFalse(scene.contains("StreamingObjSession"));
    assertFalse(scene.contains(".obj"));
    assertFalse(scene.contains(".mtl"));
}
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.output.ObjRemovalPolicyTest
```

Expected: failure because the OBJ package still exists.

- [ ] **Step 3: Delete production and test packages**

Use structured file deletion or `git rm` for exactly the six listed files. Search current source and tests for `StreamingObjSession`, `ObjTopologyConverter`, `.obj`, and `.mtl`; remove only present-tense output contracts, leaving historical release documentation untouched.

- [ ] **Step 4: Compile and run the removal test**

```powershell
.\gradlew.bat clean compileJava compileTestJava
.\gradlew.bat test --tests com.nebysse.minetomesh.output.ObjRemovalPolicyTest
```

Expected: successful compilation and passing removal test.

- [ ] **Step 5: Commit**

```powershell
git add -A src/main/java/com/nebysse/minetomesh/obj src/test/java/com/nebysse/minetomesh/obj src/test/java/com/nebysse/minetomesh/output/ObjRemovalPolicyTest.java
git commit -m "refactor: remove OBJ export path"
```

### Task 7: Update current documentation and add USDA validation gates

**Files:**
- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`
- Modify: `src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/output/OutputTransactionTest.java`

- [ ] **Step 1: Change documentation policy assertions first**

Require current docs to contain `USDA`, `OpenUSD`, `Quad`, `subdivisionScheme`, `Closest`, and Blender 5.2 import instructions. Reject present-tense statements that every export generates OBJ/MTL. Do not reject `OBJ` globally because historical release notes legitimately contain it.

- [ ] **Step 2: Run documentation tests and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.DocumentationPolicyTest
```

Expected: missing USDA documentation assertions fail.

- [ ] **Step 3: Update README and manual matrix**

Document the exact output tree, USDA import path, Y-Up conversion, preserved Quad topology, PreviewSurface limitations, nearest-neighbor `Closest` fallback, and removal of current OBJ output. Add manual cases for powered rails, grass Overlay, crops, static blocks, Create block entities, hierarchy, material tint, Cutout, emissive materials and absence of `.usdapart`.

Keep `docs/releases/*.md` unchanged.

- [ ] **Step 4: Run final automated verification**

```powershell
.\gradlew.bat test
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat runServerSmoke
```

Expected: every command succeeds; server smoke exits within its configured 90-second timeout without loading client-only USD or locked-selection classes.

- [ ] **Step 5: Commit**

```powershell
git add README.md docs/testing/manual-client-matrix.md src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java src/test/java/com/nebysse/minetomesh/output/OutputTransactionTest.java
git commit -m "docs: document USDA export workflow"
```

## Manual Blender checkpoint

Import the same export in Blender 5.2 through glTF and USDA and record evidence for:

1. USDA Quad count and `subdivisionScheme=none`.
2. glTF triangle count.
3. `+X/+Y/+Z` handedness and scale.
4. grass tint, Cutout, Blend and emissive material behavior.
5. powered rail layers separated without black rendering.
6. Create block-entity hierarchy and global material batches.
7. no `.obj`, `.mtl`, `.usdapart` or failed transaction directories.

Do not mark this plan complete from automated tests alone; Blender import is an explicit acceptance gate.
