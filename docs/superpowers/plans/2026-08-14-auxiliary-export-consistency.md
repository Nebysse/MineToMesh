# MineToMesh Auxiliary Export Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. The project owner prohibits subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve right-handed coordinates in every auxiliary capture path and merge all dynamic block-entity geometry with equal `MaterialKey` values into one output object in both glTF and OBJ.

**Architecture:** Introduce a shared `CaptureCoordinates` utility so renderer replay, entity metadata, and fallback bounds cannot independently reintroduce axis reflection. Extend glTF document assembly with global block-entity material buckets, and extend OBJ streaming with one relative-index spool per block-entity material so global merging remains bounded-memory and cancellation-safe.

**Tech Stack:** Java 21, NeoForge 21.1.244, JUnit 5, Gradle Wrapper, JOML/PoseStack, glTF 2.0, Wavefront OBJ, PowerShell

---

## Execution Constraints

- Work only in `D:\data\code\mcgltf\.worktrees\author-metadata` on branch `fix/auxiliary-handedness-material-batching`.
- Preserve commit `543bf6d` and author metadata `岚苍穹 nebysse`.
- Do not modify production code until its corresponding focused test has failed for the expected behavioral reason.
- Apply coordinate changes to glTF and OBJ through their shared captured-scene data.
- Apply global material batching only to `CapturedNode.Kind.BLOCK_ENTITY`.
- Treat complete `MaterialKey` equality as the batching contract; do not batch by texture path alone.
- Do not merge ordinary entities, static chunks, placeholders, or overlays into block-entity material objects.
- Do not change version, tag, remote branches, or GitHub Release assets.

## File Map

### New production file

- `src/main/java/com/nebysse/minetomesh/capture/CaptureCoordinates.java` — one right-handed local-coordinate, renderer-pose, AABB, and block-bound contract.

### Coordinate production files

- `src/main/java/com/nebysse/minetomesh/capture/BlockEntityCapture.java` — translation-only block-entity replay pose and positive-Z extras.
- `src/main/java/com/nebysse/minetomesh/capture/EntityCapture.java` — translation-only entity replay pose and ordered positive-Z fallback bounds.
- `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java` — positive-Z block placeholder bounds.

### Batching production files

- `src/main/java/com/nebysse/minetomesh/gltf/GltfDocumentBuilder.java` — global block-entity material mesh/node buckets and actual node creation count.
- `src/main/java/com/nebysse/minetomesh/gltf/StreamingGltfSession.java` — consume integer node creation counts.
- `src/main/java/com/nebysse/minetomesh/obj/StreamingObjSession.java` — global block-entity material spools, final emission, and cleanup.

### Tests

- Create `src/test/java/com/nebysse/minetomesh/capture/CaptureCoordinatesTest.java`.
- Create `src/test/java/com/nebysse/minetomesh/capture/AuxiliaryCoordinatePolicyTest.java`.
- Modify `src/test/java/com/nebysse/minetomesh/gltf/StreamingGltfSessionTest.java`.
- Modify `src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java`.

### Documentation

- Modify `docs/testing/manual-client-matrix.md` with Create belt orientation, normal, and object-count checks.

## Task 0: Baseline and Specification Commit

- [ ] **Step 1: Verify branch and baseline**

Run:

```powershell
git status --short --branch
.\gradlew.bat clean test build
```

Expected: branch `fix/auxiliary-handedness-material-batching`; only the new specification and plan are untracked before commit; build succeeds.

- [ ] **Step 2: Commit the approved documents**

```powershell
git add -- docs/superpowers/specs/2026-08-14-auxiliary-export-consistency-design.md docs/superpowers/plans/2026-08-14-auxiliary-export-consistency.md
git diff --cached --check
git commit -m "docs: specify auxiliary export consistency"
```

## Task 1: Establish the Red Coordinate Contract

**Files:**
- Create: `src/test/java/com/nebysse/minetomesh/capture/CaptureCoordinatesTest.java`
- Create: `src/test/java/com/nebysse/minetomesh/capture/AuxiliaryCoordinatePolicyTest.java`

- [ ] **Step 1: Write the behavior test against the desired utility API**

Create `CaptureCoordinatesTest` with tests that:

```java
@Test
void preservesPositiveZInLocalPositionsAndTranslationOnlyPoses() {
    Selection selection = selectionWithMinimum(10, 64, 20);
    Vec3f local = CaptureCoordinates.localPosition(12.0, 67.0, 24.0, selection);
    PoseStack pose = CaptureCoordinates.translatedPose(local);
    Vector3f transformedOrigin = pose.last().pose()
            .transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
    Vector3f transformedNormal = pose.last().normal()
            .transform(new Vector3f(0.0F, 0.0F, 1.0F));

    assertEquals(new Vec3f(2.0F, 3.0F, 4.0F), local);
    assertVectorEquals(new Vector3f(2.0F, 3.0F, 4.0F), transformedOrigin);
    assertVectorEquals(new Vector3f(0.0F, 0.0F, 1.0F), transformedNormal);
    assertTrue(pose.last().pose().determinant3x3() > 0.0F);
}

@Test
void preservesAscendingPositiveZBoundsForEntitiesAndBlocks() {
    Selection selection = selectionWithMinimum(10, 64, 20);
    CaptureCoordinates.Bounds entity = CaptureCoordinates.localBounds(
            new AABB(11.0, 65.0, 22.0, 13.0, 68.0, 25.0), selection);
    CaptureCoordinates.Bounds block = CaptureCoordinates.blockBounds(
            new BlockPos(12, 66, 24), selection);

    assertEquals(new Vec3f(1.0F, 1.0F, 2.0F), entity.min());
    assertEquals(new Vec3f(3.0F, 4.0F, 5.0F), entity.max());
    assertEquals(new Vec3f(2.0F, 2.0F, 4.0F), block.min());
    assertEquals(new Vec3f(3.0F, 3.0F, 5.0F), block.max());
}
```

Build the `Selection` with two `BlockPoint` values in `minecraft:overworld`, following existing world-selection tests. Use a tolerance helper for JOML vectors.

- [ ] **Step 2: Write the residual-reflection policy test**

`AuxiliaryCoordinatePolicyTest` shall read the three production source files and assert they do not contain:

```text
scale(1.0F, 1.0F, -1.0F)
-(position.getZ() - selection.min().z())
-(entity.getZ() - selection.min().z()
new Vec3f(x, y, -z - 1.0F)
```

It shall also assert all three files reference `CaptureCoordinates` after implementation.

- [ ] **Step 3: Run the tests and verify red**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.capture.CaptureCoordinatesTest --tests com.nebysse.minetomesh.capture.AuxiliaryCoordinatePolicyTest
```

Expected: compilation initially fails because the wished-for `CaptureCoordinates` API is absent. Add a test-only compile stub only if required to reach behavioral red; otherwise implement the production class only after recording the missing-API red, then rerun before changing callers so the policy test fails on residual reflection. The final red evidence must include assertion failures from existing reflected call sites.

- [ ] **Step 4: Commit red contract tests**

```powershell
git add -- src/test/java/com/nebysse/minetomesh/capture/CaptureCoordinatesTest.java src/test/java/com/nebysse/minetomesh/capture/AuxiliaryCoordinatePolicyTest.java
git diff --cached --check
git commit -m "test: reproduce auxiliary coordinate reflection"
```

## Task 2: Implement Shared Right-Handed Capture Coordinates

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/capture/CaptureCoordinates.java`
- Modify: `src/main/java/com/nebysse/minetomesh/capture/BlockEntityCapture.java`
- Modify: `src/main/java/com/nebysse/minetomesh/capture/EntityCapture.java`
- Modify: `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java`

- [ ] **Step 1: Implement `CaptureCoordinates`**

Provide these APIs:

```java
public static Vec3f localPosition(double x, double y, double z, Selection selection)
public static PoseStack translatedPose(Vec3f translation)
public static Bounds localBounds(AABB box, Selection selection)
public static Bounds blockBounds(BlockPos position, Selection selection)
public record Bounds(Vec3f min, Vec3f max)
```

`localPosition` subtracts selection minima without negation. `translatedPose` calls only `translate`, never negative `scale`. Bounds use the actual minimum and maximum Z values; block maximum is local minimum plus `(1, 1, 1)`.

- [ ] **Step 2: Route block-entity capture through the shared contract**

Replace `blockEntityPose` with local-position calculation followed by `CaptureCoordinates.translatedPose`. Replace `extras.localPosition` with the same local-position API.

- [ ] **Step 3: Route entity capture and fallback bounds through the shared contract**

Use the entity position plus renderer offset to create a translation-only pose. Replace the private reflected `localPosition` helper with `CaptureCoordinates.localPosition`. Replace reversed `box.maxZ`/`box.minZ` fallback construction with `CaptureCoordinates.localBounds`.

- [ ] **Step 4: Route block placeholders through the shared contract**

Replace the manual `new Vec3f(x, y, -z - 1)` and `new Vec3f(x + 1, y + 1, -z)` pair with `CaptureCoordinates.blockBounds(position, selection)`.

- [ ] **Step 5: Run focused and adjacent tests**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.capture.CaptureCoordinatesTest --tests com.nebysse.minetomesh.capture.AuxiliaryCoordinatePolicyTest --tests com.nebysse.minetomesh.capture.PlaceholderFactoryTest --tests com.nebysse.minetomesh.capture.EntityFilterTest
```

Expected: all pass.

- [ ] **Step 6: Commit coordinate fix**

```powershell
git add -- src/main/java/com/nebysse/minetomesh/capture/CaptureCoordinates.java src/main/java/com/nebysse/minetomesh/capture/BlockEntityCapture.java src/main/java/com/nebysse/minetomesh/capture/EntityCapture.java src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java
git diff --cached --check
git commit -m "fix: preserve auxiliary capture handedness"
```

## Task 3: Establish Red glTF Global-Material Batching

**Files:**
- Modify: `src/test/java/com/nebysse/minetomesh/gltf/StreamingGltfSessionTest.java`

- [ ] **Step 1: Add same-material block-entity batching test**

Create two `CapturedNode.Kind.BLOCK_ENTITY` nodes with different position-specific names and the same triangle material. Append them in separate `ChunkBatch` values. Assert:

```java
assertEquals(1L, statistics.nodeCount());
assertEquals(2L, statistics.primitiveCount());
assertEquals(1, document.getAsJsonArray("meshes").size());
assertEquals(2, document.getAsJsonArray("meshes").get(0)
        .getAsJsonObject().getAsJsonArray("primitives").size());
assertEquals(1L, nodesNamed(document, "BlockEntities/material_0000"));
assertEquals("GLOBAL_MATERIAL", mergedNode.getAsJsonObject("extras")
        .get("mergePolicy").getAsString());
```

- [ ] **Step 2: Add boundary tests**

Add one test proving two distinct `MaterialKey` values create two block-entity meshes/nodes, and one test proving two ordinary `ENTITY` nodes with a shared material remain two meshes/nodes.

- [ ] **Step 3: Run focused test and verify red**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.gltf.StreamingGltfSessionTest
```

Expected: same-material block entities currently report two nodes and two meshes.

- [ ] **Step 4: Commit red glTF tests**

```powershell
git add -- src/test/java/com/nebysse/minetomesh/gltf/StreamingGltfSessionTest.java
git diff --cached --check
git commit -m "test: reproduce split block entity glTF meshes"
```

## Task 4: Implement glTF Global-Material Buckets

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/gltf/GltfDocumentBuilder.java`
- Modify: `src/main/java/com/nebysse/minetomesh/gltf/StreamingGltfSession.java`

- [ ] **Step 1: Change `addNode` to return created-node count**

Change the return type from `boolean` to `int`:

- empty input returns `0`
- a new ordinary or overlay node returns `1`
- an existing overlay fragment returns `0`
- block-entity material batching returns the number of newly created material buckets

Update `StreamingGltfSession.append` to add the returned count with `Math.addExact`.

- [ ] **Step 2: Add block-entity material bucket map**

Maintain `LinkedHashMap<MaterialKey, MergedNode> blockEntityMaterialNodes`. Before ordinary handling, pair every captured primitive with its corresponding `WrittenPrimitive` and route each block-entity primitive independently.

For a new material:

```text
name = BlockEntities/material_%04d
extras.mergePolicy = GLOBAL_MATERIAL
extras.materialSourceId = material.texture().sourceId()
```

Create one mesh/node containing the first primitive. For existing material, append the primitive JSON to the existing mesh's primitive array.

- [ ] **Step 3: Preserve existing overlay and ordinary behavior**

Do not route overlays, chunks, entities, or placeholders through the material map. Preserve overlay extras conflict validation.

- [ ] **Step 4: Run glTF tests**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.gltf.GltfDocumentBuilderTest --tests com.nebysse.minetomesh.gltf.StreamingGltfSessionTest --tests com.nebysse.minetomesh.gltf.InternalGltfValidatorTest
```

Expected: all pass; actual node statistics equal actual output nodes.

- [ ] **Step 5: Commit glTF batching**

```powershell
git add -- src/main/java/com/nebysse/minetomesh/gltf/GltfDocumentBuilder.java src/main/java/com/nebysse/minetomesh/gltf/StreamingGltfSession.java
git diff --cached --check
git commit -m "feat: batch block entity glTF meshes by material"
```

## Task 5: Establish Red OBJ Global-Material Batching

**Files:**
- Modify: `src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java`

- [ ] **Step 1: Add same-material block-entity object test**

Append two position-named `BLOCK_ENTITY` nodes with the same material in separate batches. Assert:

```java
assertEquals(1, occurrences(obj, "o BlockEntities_m0000"));
assertEquals(2, occurrences(obj, "f -3/-3/-3 -2/-2/-2 -1/-1/-1"));
assertEquals(1L, statistics.nodeCount());
assertEquals(2L, statistics.primitiveCount());
assertFalse(hasBlockEntitySpool(tempDir));
```

- [ ] **Step 2: Add distinct-material and ordinary-node boundaries**

Two block-entity materials must create `BlockEntities_m0000` and `BlockEntities_m0001`. Two ordinary entity nodes sharing one material must retain their individual `o` declarations.

- [ ] **Step 3: Add cancellation cleanup test**

Append one block entity, verify a hidden block-entity spool exists during the open session, close without `finish`, and assert no `*.objpart` remains.

- [ ] **Step 4: Run focused test and verify red**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.obj.StreamingObjSessionTest
```

Expected: current output contains two position-specific `o` declarations and no material spool.

- [ ] **Step 5: Commit red OBJ tests**

```powershell
git add -- src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java
git diff --cached --check
git commit -m "test: reproduce split block entity OBJ objects"
```

## Task 6: Implement OBJ Per-Material Spooling

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/obj/StreamingObjSession.java`

- [ ] **Step 1: Add ordered spool state**

Add a `LinkedHashMap<MaterialKey, BlockEntitySpool>` where each spool stores:

```java
MaterialKey material
String materialName
Path path
BufferedWriter writer
boolean wrotePrimitive
```

Spool paths shall be deterministic hidden files:

```text
.<export-name>-block-entities-m0000.objpart
```

- [ ] **Step 2: Route only block-entity primitives to spools**

In `append`, preserve overlay handling, route `BLOCK_ENTITY` to `writeBlockEntityNode`, and leave other kinds in `writeOrdinaryNode`.

For each exportable block-entity primitive, write vertices and relative topology to its material spool. Increment primitive, face, and line counts normally. Do not emit source-position object declarations.

- [ ] **Step 3: Emit one final object per material**

At `finish`, close all block-entity writers, then for each nonempty spool in insertion order emit:

```text
o BlockEntities_m0000
g BlockEntities_m0000
usemtl m0000
<copied relative-index fragments>
```

Increment `nodeCount` once per emitted material object. Preserve global overlay emission afterward.

- [ ] **Step 4: Make cleanup total and failure-aware**

Both `finish` and `close` must close every spool writer and delete every spool path. Keep overlay failures under `GLOBAL_OVERLAY_SPOOL_FAILED`; report block-entity spool I/O failures under `GLOBAL_BLOCK_ENTITY_SPOOL_FAILED`. Suppress secondary cleanup failures onto the primary exception.

- [ ] **Step 5: Run OBJ tests**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.obj.StreamingObjSessionTest --tests com.nebysse.minetomesh.obj.ObjTopologyConverterTest
```

Expected: all pass and no spool survives success or cancellation.

- [ ] **Step 6: Commit OBJ batching**

```powershell
git add -- src/main/java/com/nebysse/minetomesh/obj/StreamingObjSession.java
git diff --cached --check
git commit -m "feat: batch block entity OBJ objects by material"
```

## Task 7: Documentation and Full Verification

**Files:**
- Modify: `docs/testing/manual-client-matrix.md`

- [ ] **Step 1: Add manual Create regression matrix**

Document ten connected belts plus asymmetric marker, outward normals/front faces, one object per exact material, separate different-material object, ordinary entity isolation, and glTF/OBJ parity.

- [ ] **Step 2: Run full verification**

```powershell
.\gradlew.bat clean test build
.\gradlew.bat runServerSmoke
```

Expected: both builds succeed and smoke output contains `MINETOMESH_SERVER_READY`.

- [ ] **Step 3: Audit candidate JAR**

Verify:

- `META-INF/neoforge.mods.toml` contains `authors="岚苍穹 nebysse"`
- version remains `1.0.0`
- `minetomesh_logo.png` remains 512×491
- no test classes, legacy packages, temporary spools, or design documents enter the JAR
- record SHA-256

- [ ] **Step 4: Commit documentation**

```powershell
git add -- docs/testing/manual-client-matrix.md
git diff --cached --check
git commit -m "docs: add Create export regression matrix"
```

- [ ] **Step 5: Present candidate without remote side effects**

Stage `build/libs/MineToMesh-1.0.0.jar` for the owner. Report commit, SHA-256, automated evidence, and the remaining Blender visual checks. Do not merge, tag, push, or replace the existing GitHub Release without explicit authorization.
