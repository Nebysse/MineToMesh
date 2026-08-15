# Coplanar Quad Layering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not dispatch subagents for this project. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect exactly coincident ordinary block quads before routing, preserve every layer, offset later layers along their own normals, and report aggregate adjustment statistics.

**Architecture:** A pure `CoplanarQuadLayering` utility transforms same-order lists of four-vertex streams and returns immutable statistics. `BlockModelExtractor` invokes it once per block before splitting grass overlays, while `GeometryAdjustmentStats` travels through `ChunkBatch` to report schema version 3 without producing per-face diagnostics.

**Tech Stack:** Java 21, JUnit 5, Minecraft 1.21.1/NeoForge mapped types, Gson report writer, Gradle.

---

## File map

- Create `src/main/java/com/nebysse/minetomesh/capture/CoplanarQuadLayering.java`: exact position-key grouping and per-layer displacement.
- Create `src/test/java/com/nebysse/minetomesh/capture/CoplanarQuadLayeringTest.java`: geometry policy tests independent of Minecraft client state.
- Create `src/main/java/com/nebysse/minetomesh/scene/GeometryAdjustmentStats.java`: immutable aggregate counts and per-block map.
- Create `src/test/java/com/nebysse/minetomesh/scene/GeometryAdjustmentStatsTest.java`: merge and immutability tests.
- Modify `src/main/java/com/nebysse/minetomesh/capture/BlockModelExtractor.java`: invoke layering before accumulator routing and return stats.
- Modify `src/main/java/com/nebysse/minetomesh/scene/ChunkBatch.java`: carry adjustment stats with a compatibility constructor defaulting to zero.
- Modify `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java`: aggregate section and writer stats into the report.
- Modify `src/main/java/com/nebysse/minetomesh/report/ExportReport.java`: add `GeometryAdjustmentStats`, bump schema to 3.
- Modify `src/main/java/com/nebysse/minetomesh/report/ReportWriter.java`: serialize deterministic `geometryAdjustments`.
- Modify `src/test/java/com/nebysse/minetomesh/report/ReportWriterTest.java`: assert the new schema and object.

### Task 1: Define immutable adjustment statistics

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/scene/GeometryAdjustmentStats.java`
- Test: `src/test/java/com/nebysse/minetomesh/scene/GeometryAdjustmentStatsTest.java`

- [ ] **Step 1: Write the failing aggregation test**

```java
package com.nebysse.minetomesh.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GeometryAdjustmentStatsTest {
    @Test
    void mergesCountsMaxLayerAndSortedBlockGroups() {
        GeometryAdjustmentStats grass = GeometryAdjustmentStats.forBlock(
                "minecraft:grass_block", 2, 4, 3);
        GeometryAdjustmentStats rail = GeometryAdjustmentStats.forBlock(
                "minecraft:powered_rail", 1, 1, 2);

        GeometryAdjustmentStats merged = grass.plus(rail);

        assertEquals(3, merged.coplanarGroups());
        assertEquals(5, merged.offsetFaces());
        assertEquals(3, merged.maxLayers());
        assertEquals(Map.of(
                "minecraft:grass_block", 2L,
                "minecraft:powered_rail", 1L), merged.byBlock());
        assertThrows(UnsupportedOperationException.class,
                () -> merged.byBlock().put("minecraft:stone", 1L));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.scene.GeometryAdjustmentStatsTest
```

Expected: compilation fails because `GeometryAdjustmentStats` does not exist.

- [ ] **Step 3: Implement the value object**

```java
package com.nebysse.minetomesh.scene;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record GeometryAdjustmentStats(
        long coplanarGroups,
        long offsetFaces,
        int maxLayers,
        Map<String, Long> byBlock) {
    public static final GeometryAdjustmentStats ZERO =
            new GeometryAdjustmentStats(0, 0, 0, Map.of());

    public GeometryAdjustmentStats {
        if (coplanarGroups < 0 || offsetFaces < 0 || maxLayers < 0) {
            throw new IllegalArgumentException("Geometry adjustment counts must not be negative");
        }
        Objects.requireNonNull(byBlock, "byBlock");
        TreeMap<String, Long> sorted = new TreeMap<>();
        byBlock.forEach((key, value) -> {
            Objects.requireNonNull(key, "block id");
            Objects.requireNonNull(value, "block group count");
            if (value < 0) {
                throw new IllegalArgumentException("Block group count must not be negative");
            }
            sorted.put(key, value);
        });
        byBlock = Collections.unmodifiableMap(sorted);
    }

    public static GeometryAdjustmentStats forBlock(
            String blockId, long groups, long faces, int layers) {
        return groups == 0
                ? ZERO
                : new GeometryAdjustmentStats(groups, faces, layers, Map.of(blockId, groups));
    }

    public GeometryAdjustmentStats plus(GeometryAdjustmentStats other) {
        Objects.requireNonNull(other, "other");
        TreeMap<String, Long> blocks = new TreeMap<>(byBlock);
        other.byBlock.forEach((key, value) -> blocks.merge(key, value, Math::addExact));
        return new GeometryAdjustmentStats(
                Math.addExact(coplanarGroups, other.coplanarGroups),
                Math.addExact(offsetFaces, other.offsetFaces),
                Math.max(maxLayers, other.maxLayers),
                blocks);
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL` and one passing test.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/scene/GeometryAdjustmentStats.java src/test/java/com/nebysse/minetomesh/scene/GeometryAdjustmentStatsTest.java
git commit -m "feat: add geometry adjustment statistics"
```

### Task 2: Implement exact quad grouping and displacement

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/capture/CoplanarQuadLayering.java`
- Test: `src/test/java/com/nebysse/minetomesh/capture/CoplanarQuadLayeringTest.java`

- [ ] **Step 1: Write failing tests for same-facing, opposite-facing, exactness and three layers**

Use real `Vertex` values and verify positions rather than mocks:

```java
@Test
void offsetsEveryLaterExactLayerAlongItsOwnNormal() {
    List<Vertex> up = quad(0.0F, Vec3f.UP);
    List<Vertex> down = quad(0.0F, new Vec3f(0.0F, -1.0F, 0.0F));
    List<Vertex> third = quad(0.0F, Vec3f.UP);

    CoplanarQuadLayering.Result result = CoplanarQuadLayering.apply(
            List.of(up, down, third));

    assertEquals(0.0F, result.quads().get(0).get(0).position().y());
    assertEquals(-1.0F / 1024.0F,
            result.quads().get(1).get(0).position().y());
    assertEquals(2.0F / 1024.0F,
            result.quads().get(2).get(0).position().y());
    assertEquals(1, result.statistics().coplanarGroups());
    assertEquals(2, result.statistics().offsetFaces());
    assertEquals(3, result.statistics().maxLayers());
}

@Test
void ignoresNearButNonidenticalQuads() {
    CoplanarQuadLayering.Result result = CoplanarQuadLayering.apply(List.of(
            quad(0.0F, Vec3f.UP),
            quad(Math.nextUp(0.0F), Vec3f.UP)));
    assertEquals(CoplanarQuadLayering.Statistics.ZERO, result.statistics());
}

@Test
void treatsNegativeZeroAndReorderedVerticesAsTheSameGeometry() {
    List<Vertex> first = quad(-0.0F, Vec3f.UP);
    List<Vertex> second = List.of(first.get(2), first.get(3), first.get(0), first.get(1));
    assertEquals(1, CoplanarQuadLayering.apply(List.of(first, second))
            .statistics().coplanarGroups());
}
```

The test class must provide `quad(float y, Vec3f normal)` with four positions `(0,y,0)`, `(1,y,0)`, `(1,y,1)`, `(0,y,1)` and preserve UV/color values.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.capture.CoplanarQuadLayeringTest
```

Expected: compilation failure because the utility is absent.

- [ ] **Step 3: Implement canonical exact keys and immutable output**

Implement the utility with this complete algorithm:

```java
public final class CoplanarQuadLayering {
    public static final float LAYER_OFFSET = 1.0F / 1024.0F;

    private CoplanarQuadLayering() {}

    public static Result apply(List<List<Vertex>> quads) {
        Objects.requireNonNull(quads, "quads");
        List<List<Vertex>> output = new ArrayList<>(quads.size());
        Map<FaceGeometryKey, List<Integer>> groups = new LinkedHashMap<>();
        for (int index = 0; index < quads.size(); index++) {
            List<Vertex> quad = List.copyOf(quads.get(index));
            if (quad.size() != 4) {
                throw new IllegalArgumentException("Coplanar layering requires four vertices");
            }
            output.add(quad);
            groups.computeIfAbsent(FaceGeometryKey.of(quad), ignored -> new ArrayList<>())
                    .add(index);
        }

        long groupCount = 0;
        long offsetFaces = 0;
        long invalidNormals = 0;
        int maxLayers = 0;
        for (List<Integer> indices : groups.values()) {
            if (indices.size() < 2) {
                continue;
            }
            groupCount++;
            maxLayers = Math.max(maxLayers, indices.size());
            for (int layer = 1; layer < indices.size(); layer++) {
                int index = indices.get(layer);
                Optional<Vec3f> normal = faceNormal(output.get(index));
                if (normal.isEmpty()) {
                    invalidNormals++;
                    continue;
                }
                float distance = layer * LAYER_OFFSET;
                Vec3f direction = normal.orElseThrow();
                output.set(index, output.get(index).stream().map(vertex -> {
                    Vec3f position = vertex.position();
                    return new Vertex(new Vec3f(
                            position.x() + direction.x() * distance,
                            position.y() + direction.y() * distance,
                            position.z() + direction.z() * distance),
                            vertex.normal(), vertex.uv(), vertex.color());
                }).toList());
                offsetFaces++;
            }
        }
        Statistics statistics = groupCount == 0
                ? Statistics.ZERO
                : new Statistics(groupCount, offsetFaces, maxLayers, invalidNormals);
        return new Result(output, statistics);
    }

    private static Optional<Vec3f> faceNormal(List<Vertex> quad) {
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        for (Vertex vertex : quad) {
            x += vertex.normal().x();
            y += vertex.normal().y();
            z += vertex.normal().z();
        }
        Optional<Vec3f> averaged = normalized(x, y, z);
        if (averaged.isPresent()) {
            return averaged;
        }
        Vec3f p0 = quad.get(0).position();
        Vec3f p1 = quad.get(1).position();
        Vec3f p2 = quad.get(2).position();
        float ax = p1.x() - p0.x();
        float ay = p1.y() - p0.y();
        float az = p1.z() - p0.z();
        float bx = p2.x() - p0.x();
        float by = p2.y() - p0.y();
        float bz = p2.z() - p0.z();
        return normalized(
                ay * bz - az * by,
                az * bx - ax * bz,
                ax * by - ay * bx);
    }

    private static Optional<Vec3f> normalized(float x, float y, float z) {
        double lengthSquared = (double) x * x + (double) y * y + (double) z * z;
        if (lengthSquared == 0.0D) {
            return Optional.empty();
        }
        float inverse = (float) (1.0D / Math.sqrt(lengthSquared));
        return Optional.of(new Vec3f(x * inverse, y * inverse, z * inverse));
    }

    private record PositionBits(int x, int y, int z)
            implements Comparable<PositionBits> {
        static PositionBits of(Vec3f value) {
            return new PositionBits(bits(value.x()), bits(value.y()), bits(value.z()));
        }

        private static int bits(float value) {
            return Float.floatToIntBits(value == 0.0F ? 0.0F : value);
        }

        @Override
        public int compareTo(PositionBits other) {
            int cx = Integer.compare(x, other.x);
            int cy = Integer.compare(y, other.y);
            return cx != 0 ? cx : cy != 0 ? cy : Integer.compare(z, other.z);
        }
    }

    private record FaceGeometryKey(List<PositionBits> positions) {
        static FaceGeometryKey of(List<Vertex> quad) {
            return new FaceGeometryKey(quad.stream()
                    .map(Vertex::position)
                    .map(PositionBits::of)
                    .sorted()
                    .toList());
        }
    }

    public record Statistics(
            long coplanarGroups, long offsetFaces, int maxLayers, long invalidNormals) {
        public static final Statistics ZERO = new Statistics(0, 0, 0, 0);
    }

    public record Result(List<List<Vertex>> quads, Statistics statistics) {
        public Result {
            quads = quads.stream().map(List::copyOf).toList();
            Objects.requireNonNull(statistics, "statistics");
        }
    }
}
```

Add imports for `ArrayList`, `LinkedHashMap`, `List`, `Map`, `Objects`, `Optional`, `Vec3f`, and `Vertex`. The private key sorts the four exact position bit-tuples, so rotated or reversed winding remains equal while any representable coordinate difference remains distinct.

- [ ] **Step 4: Run tests and add the degenerate-normal case**

Add one test using four coincident positions and cancelling vertex normals. Assert the vertices remain unchanged and `invalidNormals == 1`. Re-run the focused test; expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/capture/CoplanarQuadLayering.java src/test/java/com/nebysse/minetomesh/capture/CoplanarQuadLayeringTest.java
git commit -m "feat: separate exact coplanar block quads"
```

### Task 3: Integrate layering before block routing

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/capture/BlockModelExtractor.java`
- Modify: `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java`
- Modify: `src/main/java/com/nebysse/minetomesh/scene/ChunkBatch.java`
- Test: `src/test/java/com/nebysse/minetomesh/capture/BlockModelExtractorLayeringPolicyTest.java`

- [ ] **Step 1: Write a failing source-policy test for ordering**

Create a focused policy test that reads `BlockModelExtractor.java` and asserts the call to `CoplanarQuadLayering.apply` appears before the loop that calls `target.append`. Also assert `CaptureResult` exposes `GeometryAdjustmentStats adjustments`.

```java
assertTrue(source.indexOf("CoplanarQuadLayering.apply")
        < source.indexOf("target.append"));
assertTrue(source.contains("GeometryAdjustmentStats adjustments"));
```

- [ ] **Step 2: Run the policy test and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.capture.BlockModelExtractorLayeringPolicyTest
```

Expected: assertion failure because no layering call exists.

- [ ] **Step 3: Apply transformed streams and expose block statistics**

Declare these values before the existing capture `try`:

```java
CoplanarQuadLayering.Statistics layeringStats = CoplanarQuadLayering.Statistics.ZERO;
GeometryAdjustmentStats adjustments = GeometryAdjustmentStats.ZERO;
```

After all render-type/direction loops, but still inside that `try` and before its `catch`, call the utility so unexpected malformed captured data is converted into the existing per-block `BLOCK_MODEL_CAPTURE_FAILED` path rather than aborting the whole export:

```java
CoplanarQuadLayering.Result layered = CoplanarQuadLayering.apply(
        pending.stream().map(PendingStream::vertices).toList());
for (int index = 0; index < pending.size(); index++) {
    PendingStream source = pending.get(index);
    pending.set(index, new PendingStream(
            source.material(), source.mode(), layered.quads().get(index), source.route()));
}
layeringStats = layered.statistics();
adjustments = GeometryAdjustmentStats.forBlock(
        objectId,
        layeringStats.coplanarGroups(),
        layeringStats.offsetFaces(),
        layeringStats.maxLayers());
```

After the `try/catch` succeeds, if `layeringStats.invalidNormals() > 0`, add one warning Diagnostic at the current block position with code `COPLANAR_FACE_NORMAL_INVALID` and a message containing the invalid layer count.

Extend `BlockModelExtractor.CaptureResult` with `GeometryAdjustmentStats adjustments`; return `ZERO` in empty and failed paths.

Extend `ChunkBatch` with a fourth field and preserve existing callers through:

```java
public ChunkBatch(List<CapturedNode> nodes, List<Diagnostic> diagnostics,
        BatchCounters counters) {
    this(nodes, diagnostics, counters, GeometryAdjustmentStats.ZERO);
}
```

In `SectionCursor`, add a `GeometryAdjustmentStats adjustments = ZERO`, merge each block result, and pass it from `finish()`.

- [ ] **Step 4: Run focused capture and scene tests**

```powershell
.\gradlew.bat test --tests "com.nebysse.minetomesh.capture.*" --tests "com.nebysse.minetomesh.scene.*"
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/capture/BlockModelExtractor.java src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java src/main/java/com/nebysse/minetomesh/scene/ChunkBatch.java src/test/java/com/nebysse/minetomesh/capture/BlockModelExtractorLayeringPolicyTest.java
git commit -m "feat: layer coincident baked block faces"
```

### Task 4: Carry statistics into report schema 3

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java`
- Modify: `src/main/java/com/nebysse/minetomesh/report/ExportReport.java`
- Modify: `src/main/java/com/nebysse/minetomesh/report/ReportWriter.java`
- Modify: `src/test/java/com/nebysse/minetomesh/report/ReportWriterTest.java`

- [ ] **Step 1: Update the report test first**

Construct `ExportReport` with:

```java
new GeometryAdjustmentStats(3, 5, 3, Map.of(
        "minecraft:grass_block", 2L,
        "minecraft:powered_rail", 1L))
```

Assert schema version 3 and exact JSON fields:

```java
assertEquals(3, json.get("schemaVersion").getAsInt());
JsonObject adjustments = json.getAsJsonObject("geometryAdjustments");
assertEquals(3, adjustments.get("coplanarGroups").getAsLong());
assertEquals(5, adjustments.get("offsetFaces").getAsLong());
assertEquals(3, adjustments.get("maxLayers").getAsInt());
assertEquals(2, adjustments.getAsJsonObject("byBlock")
        .get("minecraft:grass_block").getAsLong());
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.report.ReportWriterTest
```

Expected: constructor/schema assertions fail.

- [ ] **Step 3: Implement report serialization and writer aggregation**

Add `GeometryAdjustmentStats geometryAdjustments` after `BatchCounters counters` in `ExportReport`, require non-null, and return `3` from `schemaVersion()`.

In `ReportWriter`, emit a sorted `byBlock` object after counters. In `AsyncBatchSink.writeLoop`, initialize `GeometryAdjustmentStats adjustments = ZERO`, merge `batch.adjustments()`, and pass the result to `report(...)`.

- [ ] **Step 4: Run focused and full verification**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.report.ReportWriterTest
.\gradlew.bat test
.\gradlew.bat compileJava compileTestJava
```

Expected: all commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java src/main/java/com/nebysse/minetomesh/report/ExportReport.java src/main/java/com/nebysse/minetomesh/report/ReportWriter.java src/test/java/com/nebysse/minetomesh/report/ReportWriterTest.java
git commit -m "feat: report coplanar face adjustments"
```

## Plan completion checkpoint

Run:

```powershell
git status --short
.\gradlew.bat test
```

Expected: only pre-existing unrelated untracked files remain, and the full unit suite passes before starting the USDA plan.
