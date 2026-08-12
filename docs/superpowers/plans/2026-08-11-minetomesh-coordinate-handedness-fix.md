# MineToMesh Coordinate Handedness Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. The project owner prohibits subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the intrinsic mirror reflection from both glTF and OBJ exports while preserving front-face orientation, normals, origin, scale, materials, and format parity.

**Architecture:** Correct the shared captured-scene contract at `CoordinateTransform`, then remove the reflection-compensation winding logic from the glTF and OBJ topology converters. Lock the basis-vector and index-order behavior with red tests before each production change, and document Blender's resulting axis rotation separately from MineToMesh's file-space coordinates.

**Tech Stack:** Java 21, NeoForge 21.1.244, JUnit 5, Gradle Wrapper, glTF 2.0, Wavefront OBJ, PowerShell, Git worktrees

---

## Execution Constraints

- Work only in `D:\data\code\mcgltf\.worktrees\coordinate-handedness-fix` on branch `fix/coordinate-handedness`.
- Do not modify production code before the corresponding test has failed for the expected assertion difference.
- Do not add a legacy-coordinate toggle or format-specific mirror switch.
- Keep glTF and OBJ on the same right-handed captured-scene coordinates.
- Do not change Mod ID, version, Java package, network protocol, wand data, export root, UVs, materials, hierarchy, origin, or scale.
- Automated tests establish mathematical handedness and winding. The project owner performs the final Blender visual acceptance with an asymmetric selection.
- Do not tag or publish a release in this plan.

## File Map

### Production files

- `src/main/java/com/nebysse/minetomesh/scene/CoordinateTransform.java` — relative position and normal mapping into shared export space.
- `src/main/java/com/nebysse/minetomesh/scene/TopologyConverter.java` — glTF triangle, fan, and strip index generation.
- `src/main/java/com/nebysse/minetomesh/obj/ObjTopologyConverter.java` — OBJ polygon and triangle index generation.

### Test files

- `src/test/java/com/nebysse/minetomesh/scene/CoordinateTransformTest.java` — basis direction, origin subtraction, normal direction, and zero-normal fallback.
- `src/test/java/com/nebysse/minetomesh/scene/TopologyConverterTest.java` — direct glTF topology contracts.
- `src/test/java/com/nebysse/minetomesh/scene/SourceTopologyTest.java` — separate-stream explicit triangle-strip isolation and source parity.
- `src/test/java/com/nebysse/minetomesh/obj/ObjTopologyConverterTest.java` — OBJ source polygon orientation.
- `src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java` — serialized OBJ face order.
- `src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java` — published axis-contract wording.

### Documentation

- `README.md` — corrected export-space and Blender-space mapping.
- `docs/testing/manual-client-matrix.md` — asymmetric east/west Blender comparison for both formats.

## Task 0: Create Isolated Worktree and Verify Baseline

**Files:**
- Read: `docs/superpowers/specs/2026-08-11-minetomesh-coordinate-handedness-fix-design.md`
- Read: `docs/superpowers/plans/2026-08-11-minetomesh-coordinate-handedness-fix.md`
- No source modifications

- [ ] **Step 1: Confirm main is clean and the worktree location is ignored**

Run from `D:\data\code\mcgltf`:

```powershell
git status --short --branch
git check-ignore -q .worktrees
if ($LASTEXITCODE -ne 0) { throw '.worktrees must be ignored' }
```

Expected: `## main`, no modified paths, and zero exit status.

- [ ] **Step 2: Create the bug-fix worktree**

```powershell
git worktree add .worktrees/coordinate-handedness-fix -b fix/coordinate-handedness main
```

Expected: worktree created at `D:\data\code\mcgltf\.worktrees\coordinate-handedness-fix` on `fix/coordinate-handedness`.

- [ ] **Step 3: Run the baseline suite**

Run from the new worktree:

```powershell
.\gradlew.bat clean test build
```

Expected: `BUILD SUCCESSFUL` before changing tests.

## Task 1: Establish the Red Handedness and Winding Contracts

**Files:**
- Modify: `src/test/java/com/nebysse/minetomesh/scene/CoordinateTransformTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/scene/TopologyConverterTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/scene/SourceTopologyTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/scene/PrimitiveAccumulatorTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/obj/ObjTopologyConverterTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java`

- [ ] **Step 1: Replace reflected coordinate expectations with a right-handed basis contract**

Replace the two tests in `CoordinateTransformTest` with:

```java
@Test
void subtractsOriginWithoutReflectingHorizontalAxes() {
    CoordinateTransform transform = new CoordinateTransform(
            new Vec3f(10.0F, 64.0F, -5.0F));

    assertEquals(new Vec3f(2.5F, 2.0F, 2.0F),
            transform.position(new Vec3f(12.5F, 66.0F, -3.0F)));
    assertEquals(new Vec3f(1.0F, 0.0F, 0.0F),
            transform.position(new Vec3f(11.0F, 64.0F, -5.0F)));
    assertEquals(new Vec3f(0.0F, 0.0F, 1.0F),
            transform.position(new Vec3f(10.0F, 64.0F, -4.0F)));
}

@Test
void preservesAndNormalizesNormals() {
    CoordinateTransform transform = new CoordinateTransform(Vec3f.ZERO);

    assertEquals(new Vec3f(0.0F, 0.0F, 1.0F),
            transform.normal(new Vec3f(0.0F, 0.0F, 2.0F)));
    assertEquals(Vec3f.UP,
            transform.normal(Vec3f.ZERO));
}
```

If `Vec3f.ZERO` does not exist, use `new Vec3f(0.0F, 0.0F, 0.0F)` in both locations; do not add a production constant for test convenience.

- [ ] **Step 2: Replace reflected glTF topology expectations with source-order expectations**

In `TopologyConverterTest`, replace the first three tests and update the incomplete-tail expectations:

```java
@Test
void preservesQuadAndTriangleWinding() {
    TopologyConverter.ConvertedTopology quad = TopologyConverter.convert(
            PrimitiveMode.QUADS, 4, "quad");
    TopologyConverter.ConvertedTopology triangle = TopologyConverter.convert(
            PrimitiveMode.TRIANGLES, 3, "triangle");

    assertEquals(4, quad.gltfMode());
    assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3}, quad.indices());
    assertArrayEquals(new int[] {0, 1, 2}, triangle.indices());
}

@Test
void preservesFanOuterVertexOrder() {
    TopologyConverter.ConvertedTopology result = TopologyConverter.convert(
            PrimitiveMode.TRIANGLE_FAN, 4, "fan");

    assertEquals(6, result.gltfMode());
    assertArrayEquals(new int[] {0, 1, 2, 3}, result.indices());
}

@Test
void preservesTriangleStripOrderWithoutParityDuplicates() {
    assertArrayEquals(new int[] {0, 1, 2, 3, 4},
            TopologyConverter.convert(
                    PrimitiveMode.TRIANGLE_STRIP, 5, "odd-strip").indices());
    assertArrayEquals(new int[] {0, 1, 2, 3},
            TopologyConverter.convert(
                    PrimitiveMode.TRIANGLE_STRIP, 4, "even-strip").indices());
}
```

In `discardsIncompleteQuadAndTriangleTailsWithDiagnostics`, use:

```java
assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3}, quads.indices());
assertArrayEquals(new int[] {0, 1, 2}, triangles.indices());
```

- [ ] **Step 3: Update separate-stream glTF strip expectations**

In `SourceTopologyTest.gltfConversionDoesNotConnectSeparateTriangleStrips`, replace the expected indices with:

```java
assertArrayEquals(new int[] {
        0, 1, 2, 2, 1, 3,
        4, 5, 6, 6, 5, 7
}, converted.indices());
```

This proves explicit triangle conversion preserves strip parity without joining separate renderer streams.

In `PrimitiveAccumulatorTest.mergesMatchingStreamsAndOffsetsTheirIndices`, replace the expected merged indices with:

```java
assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5},
        result.primitives().getFirst().indices());
```

This catches the same source-order contract through `PrimitiveAccumulator.seal()` rather than only calling the converter directly.

- [ ] **Step 4: Replace reflected OBJ face expectations with source-order expectations**

In `ObjTopologyConverterTest`, rename `quadBecomesOneReversedFourVertexFace` to `quadPreservesFourVertexSourceOrder` and change its expected face to:

```java
assertArrayEquals(new int[] {0, 1, 2, 3}, faces.getFirst());
```

In `separateFansDoNotShareFaces`, use:

```java
assertArrayEquals(new int[] {0, 1, 2}, faces.get(0));
assertArrayEquals(new int[] {0, 2, 3}, faces.get(1));
assertArrayEquals(new int[] {4, 5, 6}, faces.get(2));
assertArrayEquals(new int[] {4, 6, 7}, faces.get(3));
```

Add this test to cover OBJ strip parity:

```java
@Test
void triangleStripPreservesSourceParity() {
    PrimitiveData strip = new PrimitiveData(
            vertices(4), PrimitiveMode.TRIANGLE_STRIP,
            new int[] {4}, material());

    List<int[]> faces = ObjTopologyConverter.faces(strip);

    assertEquals(2, faces.size());
    assertArrayEquals(new int[] {0, 1, 2}, faces.get(0));
    assertArrayEquals(new int[] {2, 1, 3}, faces.get(1));
}
```

- [ ] **Step 5: Update serialized OBJ face order expectations**

In `StreamingObjSessionTest.writesQuadObjectMaterialAndSharedTexturePath`, replace:

```java
assertTrue(obj.contains("f 1/1/1 4/4/4 3/3/3 2/2/2"));
```

with:

```java
assertTrue(obj.contains("f 1/1/1 2/2/2 3/3/3 4/4/4"));
```

In `appendsAllOverlayFragmentsUnderOneObjectAndDeletesSpool`, replace the face token with:

```java
assertEquals(2, occurrences(obj,
        "f -4/-4/-4 -3/-3/-3 -2/-2/-2 -1/-1/-1"));
```

- [ ] **Step 6: Run the focused tests and verify the red phase**

```powershell
.\gradlew.bat test `
  --tests com.nebysse.minetomesh.scene.CoordinateTransformTest `
  --tests com.nebysse.minetomesh.scene.TopologyConverterTest `
  --tests com.nebysse.minetomesh.scene.SourceTopologyTest `
  --tests com.nebysse.minetomesh.scene.PrimitiveAccumulatorTest `
  --tests com.nebysse.minetomesh.obj.ObjTopologyConverterTest `
  --tests com.nebysse.minetomesh.obj.StreamingObjSessionTest
```

Expected: compilation succeeds and assertions fail because positions/normals are still reflected and both topology converters still reverse winding. Do not continue if the failure is a compilation error or unrelated exception.

- [ ] **Step 7: Commit the verified red contracts**

```powershell
git add -- src/test/java/com/nebysse/minetomesh/scene/CoordinateTransformTest.java src/test/java/com/nebysse/minetomesh/scene/TopologyConverterTest.java src/test/java/com/nebysse/minetomesh/scene/SourceTopologyTest.java src/test/java/com/nebysse/minetomesh/scene/PrimitiveAccumulatorTest.java src/test/java/com/nebysse/minetomesh/obj/ObjTopologyConverterTest.java src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java
git diff --cached --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git commit -m "test: reproduce mirrored export coordinates"
```

## Task 2: Remove Reflection from Shared Coordinates

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/scene/CoordinateTransform.java`
- Test: `src/test/java/com/nebysse/minetomesh/scene/CoordinateTransformTest.java`

- [ ] **Step 1: Implement origin subtraction without axis reflection**

Replace `position` and `normal` with:

```java
public Vec3f position(Vec3f world) {
    Objects.requireNonNull(world, "world");
    return new Vec3f(
            world.x() - origin.x(),
            world.y() - origin.y(),
            world.z() - origin.z());
}

public Vec3f normal(Vec3f value) {
    Objects.requireNonNull(value, "value");
    return value.normalizedOrUp();
}
```

- [ ] **Step 2: Run the coordinate tests**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.scene.CoordinateTransformTest
```

Expected: `BUILD SUCCESSFUL`; positive X and positive Z remain positive, and zero normal still falls back to `UP`.

- [ ] **Step 3: Commit the coordinate fix**

```powershell
git add -- src/main/java/com/nebysse/minetomesh/scene/CoordinateTransform.java
git diff --cached --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git commit -m "fix: preserve export coordinate handedness"
```

## Task 3: Restore Source Winding in glTF

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/scene/TopologyConverter.java`
- Test: `src/test/java/com/nebysse/minetomesh/scene/TopologyConverterTest.java`
- Test: `src/test/java/com/nebysse/minetomesh/scene/SourceTopologyTest.java`

- [ ] **Step 1: Restore grouped triangle and quad order**

Replace the body of the `if (quads)` branch in `groupedTriangles` with:

```java
indices[cursor++] = base;
indices[cursor++] = base + 1;
indices[cursor++] = base + 2;
indices[cursor++] = base;
indices[cursor++] = base + 2;
indices[cursor++] = base + 3;
```

Replace the `else` branch with:

```java
indices[cursor++] = base;
indices[cursor++] = base + 1;
indices[cursor++] = base + 2;
```

- [ ] **Step 2: Restore explicit fan and strip orientation**

In `explicitFan`, generate each triangle as:

```java
indices[cursor++] = 0;
indices[cursor++] = index;
indices[cursor++] = index + 1;
```

Replace the triangle-producing branch inside `explicitStrip` with:

```java
if ((index & 1) == 0) {
    indices[cursor++] = index;
    indices[cursor++] = index + 1;
    indices[cursor++] = index + 2;
} else {
    indices[cursor++] = index + 1;
    indices[cursor++] = index;
    indices[cursor++] = index + 2;
}
```

- [ ] **Step 3: Remove reflection-only reversal from native fan and strip modes**

Replace `fan` with:

```java
private static ConvertedTopology fan(int count, String objectId) {
    if (count < 3) {
        return new ConvertedTopology(6, new int[0], incompleteDiagnostic(count, objectId));
    }
    return new ConvertedTopology(6, range(count), List.of());
}
```

Replace `strip` with:

```java
private static ConvertedTopology strip(int count, String objectId) {
    if (count < 3) {
        return new ConvertedTopology(5, new int[0], incompleteDiagnostic(count, objectId));
    }
    return new ConvertedTopology(5, range(count), List.of());
}
```

- [ ] **Step 4: Run the glTF topology tests**

```powershell
.\gradlew.bat test `
  --tests com.nebysse.minetomesh.scene.TopologyConverterTest `
  --tests com.nebysse.minetomesh.scene.SourceTopologyTest `
  --tests com.nebysse.minetomesh.scene.PrimitiveAccumulatorTest `
  --tests com.nebysse.minetomesh.gltf.StreamingGltfSessionTest
```

Expected: `BUILD SUCCESSFUL`; direct and separate-stream indices use source orientation, and glTF streaming remains valid.

- [ ] **Step 5: Commit the glTF winding fix**

```powershell
git add -- src/main/java/com/nebysse/minetomesh/scene/TopologyConverter.java
git diff --cached --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git commit -m "fix: preserve glTF source winding"
```

## Task 4: Restore Source Winding in OBJ

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/obj/ObjTopologyConverter.java`
- Test: `src/test/java/com/nebysse/minetomesh/obj/ObjTopologyConverterTest.java`
- Test: `src/test/java/com/nebysse/minetomesh/obj/StreamingObjSessionTest.java`

- [ ] **Step 1: Restore quad, triangle, and fan orientation**

In `appendFaces`, replace the `QUADS` face with:

```java
output.add(new int[] {
        offset + index,
        offset + index + 1,
        offset + index + 2,
        offset + index + 3});
```

Replace the `TRIANGLES` face with:

```java
output.add(new int[] {
        offset + index,
        offset + index + 1,
        offset + index + 2});
```

Replace each `TRIANGLE_FAN` face with:

```java
output.add(new int[] {
        offset,
        offset + index,
        offset + index + 1});
```

- [ ] **Step 2: Restore OBJ triangle-strip parity**

Replace the `TRIANGLE_STRIP` branch with:

```java
case TRIANGLE_STRIP -> {
    for (int index = 0; index < count - 2; index++) {
        if ((index & 1) == 0) {
            output.add(new int[] {
                    offset + index,
                    offset + index + 1,
                    offset + index + 2});
        } else {
            output.add(new int[] {
                    offset + index + 1,
                    offset + index,
                    offset + index + 2});
        }
    }
}
```

- [ ] **Step 3: Run the OBJ topology and serialization tests**

```powershell
.\gradlew.bat test `
  --tests com.nebysse.minetomesh.obj.ObjTopologyConverterTest `
  --tests com.nebysse.minetomesh.obj.StreamingObjSessionTest
```

Expected: `BUILD SUCCESSFUL`; emitted OBJ faces use source vertex order for positive and negative relative indices.

- [ ] **Step 4: Run the complete focused geometry set**

```powershell
.\gradlew.bat test `
  --tests com.nebysse.minetomesh.scene.CoordinateTransformTest `
  --tests com.nebysse.minetomesh.scene.TopologyConverterTest `
  --tests com.nebysse.minetomesh.scene.SourceTopologyTest `
  --tests com.nebysse.minetomesh.scene.PrimitiveAccumulatorTest `
  --tests com.nebysse.minetomesh.gltf.StreamingGltfSessionTest `
  --tests com.nebysse.minetomesh.obj.ObjTopologyConverterTest `
  --tests com.nebysse.minetomesh.obj.StreamingObjSessionTest
```

Expected: all coordinate, glTF, and OBJ tests pass together.

- [ ] **Step 5: Commit the OBJ winding fix**

```powershell
git add -- src/main/java/com/nebysse/minetomesh/obj/ObjTopologyConverter.java
git diff --cached --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git commit -m "fix: preserve OBJ source winding"
```

## Task 5: Correct the Published Axis Contract

**Files:**
- Modify: `src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java`
- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`

- [ ] **Step 1: Add a failing documentation contract**

In `DocumentationPolicyTest.readmeDocumentsTheWandReleaseAndMigration`, add these required fragments:

```java
"不执行轴反射",
"Minecraft `+X` → Blender `+X`",
"Minecraft `+Y` → Blender `+Z`",
"Minecraft `+Z` → Blender `-Y`"
```

After the existing workstation assertions, add:

```java
assertFalse(readme.contains("(X,Y,Z) → (X,Y,-Z)"));
```

In `manualMatrixCoversTheExactWandClosure`, add:

```java
"非对称坐标方位"
```

- [ ] **Step 2: Run the documentation test and verify it fails**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.DocumentationPolicyTest
```

Expected: assertion failures for the new coordinate wording and manual-matrix row.

- [ ] **Step 3: Replace the reflected README mapping**

Replace:

```text
- 坐标转换为 `(X,Y,Z) → (X,Y,-Z)`，选区最小点为局部原点，一格对应 Blender 一米。
```

with:

```text
- 坐标以选区最小点为局部原点，一格对应 Blender 一米；导出空间保留 Minecraft 的 `(X,Y,Z)` 相对方向，不执行轴反射。
- Blender 导入 glTF 后执行 Y-up 到 Z-up 的轴旋转：Minecraft `+X` → Blender `+X`、Minecraft `+Y` → Blender `+Z`、Minecraft `+Z` → Blender `-Y`。
```

- [ ] **Step 4: Add the manual Blender handedness row**

Under `文件与 Blender 闭环` in `docs/testing/manual-client-matrix.md`, add:

```markdown
| 非对称坐标方位 | 在选区东侧放金块、西侧放钻石块，分别导入 glTF 与 OBJ | 两种格式均无镜像；Minecraft +X 对应 Blender +X，材质、法线和正面方向正确 |
```

- [ ] **Step 5: Run documentation and focused geometry tests**

```powershell
.\gradlew.bat test `
  --tests com.nebysse.minetomesh.DocumentationPolicyTest `
  --tests com.nebysse.minetomesh.scene.CoordinateTransformTest `
  --tests com.nebysse.minetomesh.scene.TopologyConverterTest `
  --tests com.nebysse.minetomesh.scene.SourceTopologyTest `
  --tests com.nebysse.minetomesh.scene.PrimitiveAccumulatorTest `
  --tests com.nebysse.minetomesh.obj.ObjTopologyConverterTest `
  --tests com.nebysse.minetomesh.obj.StreamingObjSessionTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the corrected public contract**

```powershell
git add -- README.md docs/testing/manual-client-matrix.md src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java
git diff --cached --check
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git commit -m "docs: document non-mirrored export coordinates"
```

## Task 6: Full Verification and Handoff

**Files:**
- Verify: all modified source, tests, and documentation
- Produce locally: `build/libs/MineToMesh-0.5.1.jar`

- [ ] **Step 1: Run the complete clean build**

```powershell
.\gradlew.bat clean test build
```

Expected: `BUILD SUCCESSFUL` and `build/libs/MineToMesh-0.5.1.jar` exists. The unchanged artifact version is intentional because this plan does not authorize a release bump.

- [ ] **Step 2: Run the dedicated-server smoke test**

```powershell
.\gradlew.bat runServerSmoke
```

Expected: mod list contains `MineToMesh 0.5.1 (minetomesh)`, `MINETOMESH_SERVER_READY` appears, and Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 3: Audit the production JAR**

```powershell
$jar = (Resolve-Path build\libs\MineToMesh-0.5.1.jar).Path
$entries = & jar tf $jar
if ($LASTEXITCODE -ne 0) { throw 'jar tf failed' }
foreach ($required in @(
    'com/nebysse/minetomesh/scene/CoordinateTransform.class',
    'com/nebysse/minetomesh/scene/TopologyConverter.class',
    'com/nebysse/minetomesh/obj/ObjTopologyConverter.class',
    'assets/minetomesh/models/item/export_wand.json',
    'data/minetomesh/recipe/export_wand.json')) {
    if ($entries -notcontains $required) { throw "Missing JAR entry: $required" }
}
$forbidden = @($entries | Where-Object {
    $_ -match '^com/onecuber/mcgltf/|^assets/mcgltf/|^data/mcgltf/|mcgltf_test|/testmod/|\.objpart$|superpowers'
})
if ($forbidden.Count -gt 0) {
    $forbidden
    throw 'Forbidden legacy or test content in production JAR'
}
Get-FileHash $jar -Algorithm SHA256
```

Expected: required classes and resources exist, zero forbidden entries, and SHA-256 is printed.

- [ ] **Step 4: Verify repository cleanliness and commit history**

```powershell
git diff --check
git status --short --branch
git log -6 --oneline
```

Expected: clean `fix/coordinate-handedness` branch containing the red-test, coordinate, glTF, OBJ, and documentation commits.

- [ ] **Step 5: Present the manual owner checkpoint**

Report these exact remaining checks:

```text
Automated handedness contract: passed
Automated glTF winding contract: passed
Automated OBJ winding contract: passed
Full build and server smoke: passed
Pending owner check: export an asymmetric east/west-marked selection and compare glTF with OBJ in Blender
```

Do not claim the visual bug closed until the project owner confirms the Blender comparison.

- [ ] **Step 6: Complete the branch without publishing a release**

Invoke `superpowers:finishing-a-development-branch`. The intended default is to preserve the verified branch and worktree for the owner's Blender test. Merge, version bump, tag, GitHub Release, and worktree cleanup require the owner's next explicit choice.
