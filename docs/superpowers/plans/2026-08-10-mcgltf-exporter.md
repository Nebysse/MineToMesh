# MC glTF Exporter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. The project owner has explicitly disabled subagent execution.

**Goal:** Build a Minecraft 1.21.1 NeoForge client mod that exports a loaded two-point world selection, including modded blocks, fluids, block entities, entities, independent textures, materials, and diagnostics, as Blender-ready glTF 2.0.

**Architecture:** Minecraft render-facing adapters capture final baked or rendered geometry on the client thread into small immutable scene batches. A bounded writer thread streams those batches into an external glTF binary buffer, writes independent PNG/material files, assembles the JSON document, validates references, and atomically publishes the completed export directory.

**Tech Stack:** Java 21, NeoForge 21.1.248, ModDevGradle 2.0.143, Gradle 9.2.1, JUnit Jupiter 5.11.4, Gson supplied by Minecraft, glTF 2.0, Khronos glTF Validator npm package 2.0.0-dev.3.10.

---

## Scope and plan structure

The approved specification is `docs/superpowers/specs/2026-08-10-mcgltf-exporter-design.md`. The work remains one plan because block, fluid, BER, and entity capture all depend on the same vertex protocol, material registry, streaming writer, task state machine, and transaction boundary. Every task below ends in a buildable commit.

## File map

### Build and metadata

- `settings.gradle`: plugin repositories and project name.
- `build.gradle`: Java 21, NeoForge runs, JUnit, generated metadata, and validation task.
- `gradle.properties`: pinned Minecraft, NeoForge, mod, group, license, and memory properties.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: pinned Gradle 9.2.1 wrapper.
- `.gitignore`: Gradle, IDE, run, validator, and export outputs.
- `LICENSE`: MIT license.
- `src/main/templates/META-INF/neoforge.mods.toml`: generated mod metadata.
- `src/main/resources/META-INF/accesstransformer.cfg`: narrowly exposed RenderType state.
- `src/main/resources/assets/mcgltf/lang/{en_us,zh_cn}.json`: user-facing messages.

### Bootstrap and commands

- `src/main/java/com/onecuber/mcgltf/McGltf.java`: common no-op mod entry and constants.
- `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`: physical-client bootstrap.
- `src/main/java/com/onecuber/mcgltf/command/McGltfCommands.java`: Brigadier commands.
- `src/main/java/com/onecuber/mcgltf/command/ClientMessages.java`: translated chat output.

### World and output identity

- `src/main/java/com/onecuber/mcgltf/world/BlockPoint.java`: immutable dimension-aware integer point.
- `src/main/java/com/onecuber/mcgltf/world/Selection.java`: normalized inclusive selection.
- `src/main/java/com/onecuber/mcgltf/world/SelectionStore.java`: mutable per-session two-point store.
- `src/main/java/com/onecuber/mcgltf/world/ChunkSectionRef.java`: stable section work key.
- `src/main/java/com/onecuber/mcgltf/world/ExportPlan.java`: loaded-section and missing-chunk plan.
- `src/main/java/com/onecuber/mcgltf/world/WorldPlanner.java`: ClientLevel plan construction.
- `src/main/java/com/onecuber/mcgltf/output/ExportName.java`: Unicode-safe export name.
- `src/main/java/com/onecuber/mcgltf/output/OutputTransaction.java`: temporary directory and atomic publish.

### Pure scene model

- `src/main/java/com/onecuber/mcgltf/scene/Vec2f.java`, `Vec3f.java`, `ColorRgba.java`: value types.
- `src/main/java/com/onecuber/mcgltf/scene/Vertex.java`: normalized capture vertex.
- `src/main/java/com/onecuber/mcgltf/scene/PrimitiveMode.java`: supported source modes.
- `src/main/java/com/onecuber/mcgltf/scene/PrimitiveData.java`: one material-bound primitive.
- `src/main/java/com/onecuber/mcgltf/scene/PrimitiveAccumulator.java`: merge-by-material batch builder.
- `src/main/java/com/onecuber/mcgltf/scene/TextureKey.java`: stable texture identity.
- `src/main/java/com/onecuber/mcgltf/scene/MaterialKey.java`: stable material identity.
- `src/main/java/com/onecuber/mcgltf/scene/CapturedNode.java`: chunk/object node metadata.
- `src/main/java/com/onecuber/mcgltf/scene/ChunkBatch.java`: immutable writer queue payload.
- `src/main/java/com/onecuber/mcgltf/scene/BatchCounters.java`: typed additive batch statistics.
- `src/main/java/com/onecuber/mcgltf/scene/Diagnostic.java`: stable warning/failure record.
- `src/main/java/com/onecuber/mcgltf/scene/CoordinateTransform.java`: Minecraft-to-glTF conversion.
- `src/main/java/com/onecuber/mcgltf/scene/TopologyConverter.java`: source-mode index generation.

### glTF and report

- `src/main/java/com/onecuber/mcgltf/gltf/GltfConstants.java`: component, target, and mode constants.
- `src/main/java/com/onecuber/mcgltf/gltf/AccessorBounds.java`: finite min/max calculation.
- `src/main/java/com/onecuber/mcgltf/gltf/BinaryBufferWriter.java`: little-endian aligned stream.
- `src/main/java/com/onecuber/mcgltf/gltf/WrittenPrimitive.java`: lightweight binary descriptor.
- `src/main/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilder.java`: glTF JSON arrays and references.
- `src/main/java/com/onecuber/mcgltf/gltf/StreamingGltfSession.java`: batch-to-bin streaming session.
- `src/main/java/com/onecuber/mcgltf/gltf/InternalGltfValidator.java`: local structural validation.
- `src/main/java/com/onecuber/mcgltf/report/ExportReport.java`: report DTO and counters.
- `src/main/java/com/onecuber/mcgltf/report/ReportWriter.java`: deterministic JSON report.

### Job system

- `src/main/java/com/onecuber/mcgltf/job/JobState.java`: legal lifecycle states.
- `src/main/java/com/onecuber/mcgltf/job/CancellationToken.java`: idempotent cancellation.
- `src/main/java/com/onecuber/mcgltf/job/CaptureBudget.java`: monotonic per-tick deadline.
- `src/main/java/com/onecuber/mcgltf/job/ExportProgress.java`: immutable progress snapshot.
- `src/main/java/com/onecuber/mcgltf/job/ManagedJob.java`: testable lifecycle contract.
- `src/main/java/com/onecuber/mcgltf/job/ExportJob.java`: orchestrated rolling snapshot.
- `src/main/java/com/onecuber/mcgltf/job/ExportJobManager.java`: single-job owner and lifecycle hooks.

### Capture, texture, and material adapters

- `src/main/java/com/onecuber/mcgltf/capture/CapturingVertexConsumer.java`: stateful 1.21 VertexConsumer.
- `src/main/java/com/onecuber/mcgltf/capture/CapturingMultiBufferSource.java`: RenderType-separated capture.
- `src/main/java/com/onecuber/mcgltf/capture/RenderTypeDescriptor.java`: pure render semantics.
- `src/main/java/com/onecuber/mcgltf/capture/RenderTypeInspector.java`: access-transformed state inspection.
- `src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java`: BakedModel/BakedQuad extraction.
- `src/main/java/com/onecuber/mcgltf/capture/SelectionBlockView.java`: air outside selection.
- `src/main/java/com/onecuber/mcgltf/capture/FluidGeometryCapture.java`: renderLiquid capture and UV classification.
- `src/main/java/com/onecuber/mcgltf/capture/BlockEntityCapture.java`: BER capture.
- `src/main/java/com/onecuber/mcgltf/capture/EntityCapture.java`: static entity renderer capture.
- `src/main/java/com/onecuber/mcgltf/capture/PlaceholderFactory.java`: translucent magenta boxes.
- `src/main/java/com/onecuber/mcgltf/texture/TextureImage.java`: immutable RGBA image payload.
- `src/main/java/com/onecuber/mcgltf/texture/TextureRegistry.java`: stable texture index and payload registry.
- `src/main/java/com/onecuber/mcgltf/texture/SpriteTextureExtractor.java`: atlas Sprite extraction.
- `src/main/java/com/onecuber/mcgltf/texture/ResourceTextureExtractor.java`: resource/dynamic texture extraction.
- `src/main/java/com/onecuber/mcgltf/material/MaterialResolver.java`: RenderType to MaterialKey.
- `src/main/java/com/onecuber/mcgltf/material/MaterialSidecarWriter.java`: independent material JSON.

### Validation and documentation

- `tools/package.json`: pinned Khronos validator dependency.
- `tools/validate-gltf.mjs`: validate one external-resource glTF and fail on errors.
- `README.md`: installation, commands, limits, output, Blender workflow, and compatibility.
- `docs/testing/manual-client-matrix.md`: repeatable client and Blender acceptance matrix.

Tests mirror these packages under `src/test/java/com/onecuber/mcgltf/`.

---

### Task 1: Scaffold the NeoForge client-only project

**Files:**
- Create: `.gitignore`
- Create: `LICENSE`
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `src/main/templates/META-INF/neoforge.mods.toml`
- Create: `src/main/java/com/onecuber/mcgltf/McGltf.java`
- Create: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- Create: `src/test/java/com/onecuber/mcgltf/McGltfMetadataTest.java`

- [ ] **Step 1: Download the pinned Gradle wrapper files from the official MDK commit**

Run in PowerShell:

```powershell
$base = 'https://raw.githubusercontent.com/NeoForgeMDKs/MDK-1.21-ModDevGradle/e723563da86825da5d6e6a7c1422896b4e2e57f3'
New-Item -ItemType Directory -Force gradle/wrapper | Out-Null
Invoke-WebRequest "$base/gradlew" -OutFile gradlew
Invoke-WebRequest "$base/gradlew.bat" -OutFile gradlew.bat
Invoke-WebRequest "$base/gradle/wrapper/gradle-wrapper.jar" -OutFile gradle/wrapper/gradle-wrapper.jar
Invoke-WebRequest "$base/gradle/wrapper/gradle-wrapper.properties" -OutFile gradle/wrapper/gradle-wrapper.properties
```

Expected: all four files exist and `gradle-wrapper.properties` names `gradle-9.2.1-bin.zip`.

- [ ] **Step 2: Create exact project metadata**

Use these properties:

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.21.2)
neo_version=21.1.248
loader_version_range=[4,)
mod_id=mcgltf
mod_name=MC glTF Exporter
mod_license=MIT
mod_version=0.1.0
mod_group_id=com.onecuber.mcgltf
```

Create `settings.gradle` exactly as:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'mcgltf'
```

Create `build.gradle` exactly as:

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '2.0.143'
    id 'idea'
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

sourceSets.main.resources {
    srcDir 'src/generated/resources'
}

neoForge {
    version = project.neo_version

    runs {
        client { client() }
        server {
            server()
            programArgument '--nogui'
        }
        configureEach {
            logLevel = org.slf4j.event.Level.INFO
        }
    }

    mods {
        "${mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

configurations {
    runtimeClasspath.extendsFrom localRuntime
}

dependencies {
    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

tasks.named('test', Test).configure {
    useJUnitPlatform()
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

var generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    var replaceProperties = [
            minecraft_version: minecraft_version,
            minecraft_version_range: minecraft_version_range,
            neo_version: neo_version,
            loader_version_range: loader_version_range,
            mod_id: mod_id,
            mod_name: mod_name,
            mod_license: mod_license,
            mod_version: mod_version
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from 'src/main/templates'
    into 'build/generated/sources/modMetadata'
}

sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata

idea {
    module {
        downloadSources = true
        downloadJavadoc = true
    }
}
```

Create `src/main/templates/META-INF/neoforge.mods.toml` exactly as:

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
authors="OneCuber"
description='''
Client-side loaded-world exporter for Blender-ready glTF 2.0 scenes.
'''

[[dependencies.${mod_id}]]
modId="neoforge"
type="required"
versionRange="[${neo_version},)"
ordering="NONE"
side="CLIENT"

[[dependencies.${mod_id}]]
modId="minecraft"
type="required"
versionRange="${minecraft_version_range}"
ordering="NONE"
side="CLIENT"
```

This metadata declares MIT through the expanded `mod_license` property.

- [ ] **Step 3: Add the MIT license and ignore generated state**

Use the standard MIT text with copyright line:

```text
Copyright (c) 2026 OneCuber
```

Ignore `.gradle/`, `build/`, `run/`, `run-data/`, `.idea/`, `*.iml`, `out/`, `node_modules/`, `tools/package-lock.json`, and `mcgltf-exports/`.

- [ ] **Step 4: Write a failing metadata test**

```java
package com.onecuber.mcgltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class McGltfMetadataTest {
    @Test
    void exposesStableModIdentity() {
        assertEquals("mcgltf", McGltf.MOD_ID);
        assertEquals("MC glTF Exporter", McGltf.DISPLAY_NAME);
        assertEquals("0.1.0", McGltf.VERSION);
    }
}
```

- [ ] **Step 5: Run the test and verify the intended failure**

Run: `./gradlew.bat test --tests com.onecuber.mcgltf.McGltfMetadataTest`

Expected: compilation fails because `McGltf` does not exist.

- [ ] **Step 6: Add common and client entry classes**

```java
package com.onecuber.mcgltf;

import net.neoforged.fml.common.Mod;

@Mod(McGltf.MOD_ID)
public final class McGltf {
    public static final String MOD_ID = "mcgltf";
    public static final String DISPLAY_NAME = "MC glTF Exporter";
    public static final String VERSION = "0.1.0";
}
```

```java
package com.onecuber.mcgltf.client;

import com.onecuber.mcgltf.McGltf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = McGltf.MOD_ID, dist = Dist.CLIENT)
public final class McGltfClient {
    public McGltfClient(IEventBus modBus) {
    }
}
```

The common entry contains no `net.minecraft.client` imports, so the jar safely no-ops if accidentally placed on a dedicated server.

- [ ] **Step 7: Run baseline verification**

Run: `./gradlew.bat test build`

Expected: `BUILD SUCCESSFUL`, one passing test, and `build/libs/mcgltf-0.1.0.jar`.

- [ ] **Step 8: Commit the scaffold**

```powershell
git add .gitignore LICENSE settings.gradle build.gradle gradle.properties gradlew gradlew.bat gradle src/main src/test
git commit -m "build: scaffold NeoForge 1.21.1 client mod"
```

---

### Task 2: Implement selection, naming, and deterministic section planning values

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/world/BlockPoint.java`
- Create: `src/main/java/com/onecuber/mcgltf/world/Selection.java`
- Create: `src/main/java/com/onecuber/mcgltf/world/SelectionStore.java`
- Create: `src/main/java/com/onecuber/mcgltf/world/ChunkSectionRef.java`
- Create: `src/main/java/com/onecuber/mcgltf/output/ExportName.java`
- Test: `src/test/java/com/onecuber/mcgltf/world/SelectionTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/world/SelectionStoreTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/output/ExportNameTest.java`

- [ ] **Step 1: Write failing selection tests**

Tests must assert that `(10,80,5)` and `(-2,64,20)` normalize to min `(-2,64,5)`, max `(10,80,20)`, inclusive sizes `(13,17,16)`, volume `3536`, and containment includes both boundary points. A dimension mismatch must throw `IllegalArgumentException("Selection points must be in the same dimension")`.

- [ ] **Step 2: Run the selection tests and verify compilation failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.world.*"`

Expected: FAIL because the world value classes do not exist.

- [ ] **Step 3: Implement immutable points and selections**

```java
public record BlockPoint(String dimension, int x, int y, int z) {
    public BlockPoint {
        Objects.requireNonNull(dimension, "dimension");
    }
}
```

`Selection.of(a,b)` must normalize each axis with `Math.min/Math.max`, expose `sizeX/sizeY/sizeZ`, use `Math.multiplyExact` for `long volume()`, implement inclusive `contains(x,y,z)`, and return local coordinates by subtracting the minimum point.

`ChunkSectionRef` is ordered by chunk X, chunk Z, then section Y:

```java
public record ChunkSectionRef(int chunkX, int sectionY, int chunkZ)
        implements Comparable<ChunkSectionRef> {
    @Override
    public int compareTo(ChunkSectionRef other) {
        int x = Integer.compare(chunkX, other.chunkX);
        if (x != 0) return x;
        int z = Integer.compare(chunkZ, other.chunkZ);
        if (z != 0) return z;
        return Integer.compare(sectionY, other.sectionY);
    }
}
```

- [ ] **Step 4: Implement the session selection store**

`SelectionStore` stores nullable `pos1/pos2`, replaces a point on each set, exposes `Optional<Selection> selection()`, and clears both points when the active dimension changes or the client disconnects. Its tests must cover partial selection and clear.

- [ ] **Step 5: Write failing export-name tests**

Test acceptance of `城堡_01` and `castle.v2`; NFC normalization of `e\u0301` to `é`; rejection of empty text, `.`, `..`, `CON`, `con.txt`, `a/b`, `a\\b`, trailing dot, trailing space, a control character, and 65 Unicode code points.

- [ ] **Step 6: Implement path-safe Unicode names**

`ExportName.parse(String)` trims no meaningful characters, normalizes NFC, rejects the listed values, compares Windows device names case-insensitively before the first dot, and exposes `value()`. Error messages must identify the violated rule without echoing control characters.

- [ ] **Step 7: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.world.*" --tests "com.onecuber.mcgltf.output.ExportNameTest"`

Expected: all tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/world src/main/java/com/onecuber/mcgltf/output/ExportName.java src/test/java/com/onecuber/mcgltf/world src/test/java/com/onecuber/mcgltf/output/ExportNameTest.java
git commit -m "feat: add selection and safe export names"
```

---

### Task 3: Define the pure scene contract and coordinate conversion

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/scene/Vec2f.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/Vec3f.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/ColorRgba.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/Vertex.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/PrimitiveMode.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/TextureKey.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/MaterialKey.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/Diagnostic.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/CoordinateTransform.java`
- Test: `src/test/java/com/onecuber/mcgltf/scene/CoordinateTransformTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/scene/SceneValueTest.java`

- [ ] **Step 1: Write failing conversion and validation tests**

Assert world `(12.5,66,-3)` with origin `(10,64,-5)` becomes `(2.5,2,-2)`. Assert normal `(0,0,1)` becomes `(0,0,-1)`. Reject every non-finite vector component and color channel outside `0..255`.

- [ ] **Step 2: Run the tests and verify compilation failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.scene.*"`

Expected: FAIL because scene records do not exist.

- [ ] **Step 3: Implement the scene value records**

Use compact constructors with `Float.isFinite`. `ColorRgba.WHITE` is `(255,255,255,255)`. `Vertex` contains `Vec3f position`, `Vec3f normal`, `Vec2f uv`, and `ColorRgba color`.

`PrimitiveMode` contains `QUADS`, `TRIANGLES`, `TRIANGLE_STRIP`, `TRIANGLE_FAN`, `LINES`, and `LINE_STRIP`.

`TextureKey` contains `kind`, `sourceId`, and `outputPath`. `MaterialKey` contains `TextureKey texture`, `AlphaMode {OPAQUE,MASK,BLEND}`, `Optional<Float> alphaCutoff` (present with `0.5F` only for MASK), `boolean doubleSided`, `boolean emissive`, `BlendSemantic {STANDARD,ADDITIVE,GLINT}`, and `SamplerMode {NEAREST,NEAREST_MIPMAP}`.

`Diagnostic` is the exact record `Diagnostic(Severity severity, String code, String objectId, Optional<BlockPoint> position, String rendererClass, String exceptionType, String message)` with `Severity {WARNING,FAILURE,FATAL}`. Empty renderer/exception values use the empty string rather than null.

- [ ] **Step 4: Implement coordinate conversion**

```java
public final class CoordinateTransform {
    private final Vec3f origin;

    public CoordinateTransform(Vec3f origin) {
        this.origin = origin;
    }

    public Vec3f position(Vec3f world) {
        return new Vec3f(
                world.x() - origin.x(),
                world.y() - origin.y(),
                -(world.z() - origin.z()));
    }

    public Vec3f normal(Vec3f value) {
        return new Vec3f(value.x(), value.y(), -value.z()).normalizedOrUp();
    }
}
```

`normalizedOrUp()` returns `(0,1,0)` for a zero vector and otherwise returns the unit vector.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.scene.*"`

Expected: all scene tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/scene src/test/java/com/onecuber/mcgltf/scene
git commit -m "feat: define scene values and coordinate transform"
```

---

### Task 4: Convert captured topology into valid glTF index streams

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/scene/PrimitiveData.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/PrimitiveAccumulator.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/TopologyConverter.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/CapturedNode.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/ChunkBatch.java`
- Create: `src/main/java/com/onecuber/mcgltf/scene/BatchCounters.java`
- Test: `src/test/java/com/onecuber/mcgltf/scene/TopologyConverterTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/scene/PrimitiveAccumulatorTest.java`

- [ ] **Step 1: Write failing topology tests**

Use numbered vertices and assert:

- QUADS `0,1,2,3` becomes mirrored triangles `0,2,1,0,3,2`.
- TRIANGLES `0,1,2` becomes `0,2,1`.
- TRIANGLE_FAN `0,1,2,3` becomes fan order `0,3,2,1` with glTF mode 6.
- A five-vertex strip reverses to `4,3,2,1,0`; a four-vertex strip prepends a duplicate and becomes `3,3,2,1,0`, preserving geometry while flipping strip parity.
- LINES and LINE_STRIP retain order.
- Incomplete quad and triangle tails produce `Diagnostic` code `INCOMPLETE_PRIMITIVE` and are discarded.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests com.onecuber.mcgltf.scene.TopologyConverterTest`

Expected: FAIL because `TopologyConverter` does not exist.

- [ ] **Step 3: Implement topology conversion**

Return a record `ConvertedTopology(int gltfMode, int[] indices, List<Diagnostic> diagnostics)`. Use glTF mode values: lines 1, line strip 3, triangles 4, triangle strip 5, triangle fan 6. Every triangle path must account for the Z reflection.

- [ ] **Step 4: Implement immutable primitive and node batches**

`PrimitiveData` owns immutable `List<Vertex>`, `int[] indices`, glTF mode, and `MaterialKey`. Its constructor checks index bounds and clones the index array.

`PrimitiveAccumulator` groups by `(MaterialKey, PrimitiveMode)`, appends complete renderer streams, converts each group on `seal()`, and returns immutable primitives and diagnostics.

`CapturedNode` contains `name`, `kind {CHUNK,BLOCK_ENTITY,ENTITY,PLACEHOLDER}`, `List<PrimitiveData>`, and `Map<String,Object> extras`. `ChunkBatch` contains `List<CapturedNode>`, `List<Diagnostic>`, and `BatchCounters`.

`BatchCounters` is a record of long values `scannedPositions`, `renderedBlocks`, `renderedFluids`, `blockEntities`, `entities`, `materials`, `textures`, `triangles`, and `placeholders`, with `ZERO` and an overflow-checking `plus(BatchCounters)` method.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.scene.*"`

Expected: all scene tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/scene src/test/java/com/onecuber/mcgltf/scene
git commit -m "feat: convert captured topology into scene batches"
```

---

### Task 5: Build the aligned binary glTF stream

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/gltf/GltfConstants.java`
- Create: `src/main/java/com/onecuber/mcgltf/gltf/AccessorBounds.java`
- Create: `src/main/java/com/onecuber/mcgltf/gltf/BinaryBufferWriter.java`
- Create: `src/main/java/com/onecuber/mcgltf/gltf/WrittenPrimitive.java`
- Test: `src/test/java/com/onecuber/mcgltf/gltf/BinaryBufferWriterTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/gltf/AccessorBoundsTest.java`

- [ ] **Step 1: Write failing binary tests**

Write one triangle and assert:

- every returned segment offset is divisible by four;
- floats and unsigned ints are little-endian;
- color bytes remain unpadded inside their own segment and the next segment begins aligned;
- position bounds equal `[0,0,-1]` and `[2,3,4]` for the supplied values;
- NaN and infinity are rejected before any bytes are written.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.gltf.*"`

Expected: FAIL because the glTF stream classes do not exist.

- [ ] **Step 3: Implement constants and bounds**

Use component constants `UNSIGNED_BYTE=5121`, `UNSIGNED_INT=5125`, `FLOAT=5126`; targets `ARRAY_BUFFER=34962`, `ELEMENT_ARRAY_BUFFER=34963`; primitive modes from Task 4.

`AccessorBounds.positions(List<Vertex>)` returns two three-element float arrays and rejects an empty list.

- [ ] **Step 4: Implement the binary writer**

`BinaryBufferWriter` wraps a counting `OutputStream`, aligns before each segment with zero bytes, and exposes:

```java
public Segment writePositions(List<Vertex> vertices);
public Segment writeNormals(List<Vertex> vertices);
public Segment writeTexCoords(List<Vertex> vertices);
public Segment writeColors(List<Vertex> vertices);
public Segment writeIndices(int[] indices);
public long byteLength();
```

`Segment` stores `byteOffset`, `byteLength`, `target`, `componentType`, `componentCount`, `elementCount`, and optional min/max. Close only flushes and closes the owned stream.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.gltf.*"`

Expected: all binary tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/gltf src/test/java/com/onecuber/mcgltf/gltf
git commit -m "feat: add aligned glTF binary writer"
```

---

### Task 6: Assemble external-resource glTF JSON and validate references

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilder.java`
- Create: `src/main/java/com/onecuber/mcgltf/gltf/StreamingGltfSession.java`
- Create: `src/main/java/com/onecuber/mcgltf/gltf/InternalGltfValidator.java`
- Test: `src/test/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilderTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/gltf/StreamingGltfSessionTest.java`

- [ ] **Step 1: Write a failing one-triangle document test**

Create one textured triangle and assert JSON has asset version `2.0`, generator `MC glTF Exporter 0.1.0`, one scene, root nodes `Chunks`, `BlockEntities`, `Entities`, `Placeholders`, external buffer URI `sample.bin`, four attribute accessors plus indices, `COLOR_0.normalized=true`, and relative image URI `textures/minecraft/block/stone.png`.

- [ ] **Step 2: Run the test and verify failure**

Run: `./gradlew.bat test --tests com.onecuber.mcgltf.gltf.GltfDocumentBuilderTest`

Expected: FAIL because the document classes do not exist.

- [ ] **Step 3: Implement deterministic JSON construction**

Use Gson `JsonObject` and `JsonArray`. Keep insertion-ordered maps for texture and material indices. Emit:

- samplers with `magFilter=9728`, `minFilter=9984` or `9728`, wraps `10497`;
- images, textures, materials, bufferViews, accessors, meshes, nodes, scenes, and one buffer;
- PBR metallic 0 and roughness 1;
- MASK cutoff, BLEND mode, double-sided, emissive texture/factor;
- root extras supplied by the caller.

Use `GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()` and UTF-8.

- [ ] **Step 4: Implement streaming session ownership**

`StreamingGltfSession.append(ChunkBatch)` writes each primitive's five binary segments immediately, records only `WrittenPrimitive`, and drops references to vertex lists after return. `finish()` closes the binary stream, supplies the final byte length to the document, writes `<name>.gltf`, and returns immutable output statistics.

- [ ] **Step 5: Implement internal validation**

`InternalGltfValidator.validate(JsonObject, Path root)` checks index bounds across every glTF array, relative URI containment under root, referenced file existence, four-byte bufferView offsets, finite accessor min/max, and declared buffer length equal to the `.bin` size. Return a list of messages; any message is fatal before publish.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.gltf.*"`

Expected: all glTF tests pass and the test-created `.gltf` parses as JSON.

```powershell
git add src/main/java/com/onecuber/mcgltf/gltf src/test/java/com/onecuber/mcgltf/gltf
git commit -m "feat: stream and validate external glTF documents"
```

---

### Task 7: Add transaction-safe output and structured reporting

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/output/OutputTransaction.java`
- Create: `src/main/java/com/onecuber/mcgltf/report/ExportReport.java`
- Create: `src/main/java/com/onecuber/mcgltf/report/ReportWriter.java`
- Test: `src/test/java/com/onecuber/mcgltf/output/OutputTransactionTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/report/ReportWriterTest.java`

- [ ] **Step 1: Write failing transaction tests**

Using `@TempDir`, assert the temporary directory is `.tmp-<UUID>`, successful publish creates `castle`, a second publish creates `castle-2`, cancellation deletes only its temporary directory, and an existing formal directory is never modified.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.output.*" --tests "com.onecuber.mcgltf.report.*"`

Expected: FAIL because transaction/report classes do not exist.

- [ ] **Step 3: Implement output transactions**

`OutputTransaction.begin(exportRoot, ExportName)` creates the export root and UUID temp directory. `publish()` chooses the first available suffix and uses `Files.move(temp, final, ATOMIC_MOVE)` with a same-filesystem fallback to `Files.move(temp, final)` only when atomic moves are unsupported. `close()` recursively deletes an unpublished temp tree without following symbolic links.

- [ ] **Step 4: Implement the report model**

`ExportReport` contains schema version 1, status, rolling snapshot metadata, dimension, min/max/origin arrays, volume, start/end game time, counters, sorted missing chunks, diagnostics, and timings. Counters distinguish scanned positions, rendered blocks, rendered fluids, block entities, entities, materials, textures, triangles, and placeholders.

- [ ] **Step 5: Test and implement deterministic report JSON**

The report test must compare parsed JSON fields and confirm diagnostics sort by code, object ID, then position. `ReportWriter` uses UTF-8 pretty Gson and writes `report.json` only inside the transaction directory.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.output.*" --tests "com.onecuber.mcgltf.report.*"`

Expected: all tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/output src/main/java/com/onecuber/mcgltf/report src/test/java/com/onecuber/mcgltf/output src/test/java/com/onecuber/mcgltf/report
git commit -m "feat: add transactional output and reports"
```

---

### Task 8: Implement job lifecycle, bounded queue, and capture budget

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/job/JobState.java`
- Create: `src/main/java/com/onecuber/mcgltf/job/CancellationToken.java`
- Create: `src/main/java/com/onecuber/mcgltf/job/CaptureBudget.java`
- Create: `src/main/java/com/onecuber/mcgltf/job/ExportProgress.java`
- Create: `src/main/java/com/onecuber/mcgltf/job/ManagedJob.java`
- Create: `src/main/java/com/onecuber/mcgltf/job/ExportJobManager.java`
- Test: `src/test/java/com/onecuber/mcgltf/job/ExportJobManagerTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/job/CaptureBudgetTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

Assert legal transitions `IDLE→PLANNING→CAPTURING→WRITING→COMPLETED`, terminal alternatives CANCELLED/FAILED, rejection of a second active job, idempotent cancellation, queue capacity exactly two, and a fake monotonic clock crossing a six-millisecond budget.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.job.*"`

Expected: FAIL because job classes do not exist.

- [ ] **Step 3: Implement pure lifecycle components**

Define `static final long captureBudgetMs = 6L` in `ExportJobManager` and construct each tick budget with `Duration.ofMillis(captureBudgetMs)`. `CaptureBudget.start(Duration, LongSupplier nanoTime)` stores a deadline and exposes `hasTime()`. `CancellationToken.cancel(reason)` stores the first reason. `ExportProgress` reports state, completed/total work items, queue depth, elapsed time, and current object ID.

Define the exact manager seam:

```java
public interface ManagedJob {
    void tick();
    void cancel(String reason);
    JobState state();
    ExportProgress progress();
    default boolean isTerminal() {
        return switch (state()) {
            case COMPLETED, CANCELLED, FAILED -> true;
            default -> false;
        };
    }
}
```

- [ ] **Step 4: Implement the single-job manager**

Use an `AtomicReference<ManagedJob>`. `start` uses compare-and-set; `tick` delegates only to the active job; `cancel` is safe with no job; a terminal job remains available for `status` until a new job starts. Expose package-private construction seams so tests use fake jobs without Minecraft.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.job.*"`

Expected: all job tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/job src/test/java/com/onecuber/mcgltf/job
git commit -m "feat: add bounded export job lifecycle"
```

---

### Task 9: Register client commands and lifecycle cancellation

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/command/McGltfCommands.java`
- Create: `src/main/java/com/onecuber/mcgltf/command/ClientMessages.java`
- Create: `src/main/resources/assets/mcgltf/lang/en_us.json`
- Create: `src/main/resources/assets/mcgltf/lang/zh_cn.json`
- Modify: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- Test: `src/test/java/com/onecuber/mcgltf/command/CommandPolicyTest.java`

- [ ] **Step 1: Write failing command-policy tests**

Extract pure methods for `requiresConfirmation(volume)` and status formatting. Assert `4_194_304` needs no confirmation and `4_194_305` does. Assert running progress includes state, percentage, queue depth, and current object.

- [ ] **Step 2: Run test and verify failure**

Run: `./gradlew.bat test --tests com.onecuber.mcgltf.command.CommandPolicyTest`

Expected: FAIL because command policy does not exist.

- [ ] **Step 3: Register the exact Brigadier tree**

Register on `RegisterClientCommandsEvent`:

```text
mcgltf
├─ pos1
├─ pos2
├─ export <name>
│  └─ confirm
├─ status
└─ cancel
```

Use `StringArgumentType.string()` for Unicode names so the trailing `confirm` literal remains reachable; names containing spaces are entered as quoted Brigadier strings. `pos1/pos2` read `Minecraft.getInstance().player.blockPosition()` and `level.dimension().location().toString()`. Commands return Brigadier success 1 or failure 0 and send translated components.

- [ ] **Step 4: Add client event wiring**

Register game-bus listeners for `RegisterClientCommandsEvent`, `ClientTickEvent.Post`, and `ClientPlayerNetworkEvent.LoggingOut`. Register a client reload listener on the mod bus; its reload callback cancels an active job before resources change. A dimension change detected in the tick clears `SelectionStore` and cancels the job.

- [ ] **Step 5: Add exact translations**

Provide English and Simplified Chinese keys for point set, incomplete/cross-dimension selection, unsafe name, soft-limit confirmation, already running, progress, cancelled, completed, completed-with-warnings, and failure. Include clickable command text for the confirmation command.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew.bat test build`

Expected: `BUILD SUCCESSFUL`; `neoforge.mods.toml` and both language files are present in the jar.

```powershell
git add src/main/java/com/onecuber/mcgltf/client src/main/java/com/onecuber/mcgltf/command src/main/resources/assets src/test/java/com/onecuber/mcgltf/command
git commit -m "feat: register client export commands"
```

---

### Task 10: Capture 1.21 vertex streams and inspect RenderType semantics

**Files:**
- Create: `src/main/resources/META-INF/accesstransformer.cfg`
- Create: `src/main/java/com/onecuber/mcgltf/capture/RenderTypeDescriptor.java`
- Create: `src/main/java/com/onecuber/mcgltf/capture/RenderTypeInspector.java`
- Create: `src/main/java/com/onecuber/mcgltf/capture/CapturingVertexConsumer.java`
- Create: `src/main/java/com/onecuber/mcgltf/capture/CapturingMultiBufferSource.java`
- Test: `src/test/java/com/onecuber/mcgltf/capture/CapturingVertexConsumerTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/capture/RenderTypePolicyTest.java`

- [ ] **Step 1: Write failing stateful-consumer tests**

Call `addVertex`, color/UV/normal setters, then another `addVertex`. Assert the first vertex commits on the second start; `finish()` commits the last vertex; omitted fields default to white, UV zero, and up normal; setters before a position throw `IllegalStateException`; repeated `finish()` is idempotent.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.capture.*"`

Expected: FAIL because capture classes do not exist.

- [ ] **Step 3: Implement the 1.21 VertexConsumer state machine**

Implement all six abstract methods: `addVertex`, `setColor`, `setUv`, `setUv1`, `setUv2`, and `setNormal`; UV1/UV2 are accepted and intentionally ignored for clean materials. Keep a mutable pending vertex and flush it on the next `addVertex` or `finish()`.

- [ ] **Step 4: Add narrowly scoped access transforms**

Use official Mojang names:

```text
public net.minecraft.client.renderer.RenderType$CompositeRenderType
public net.minecraft.client.renderer.RenderType$CompositeRenderType state()Lnet/minecraft/client/renderer/RenderType$CompositeState;
public net.minecraft.client.renderer.RenderType$CompositeState textureState
public net.minecraft.client.renderer.RenderType$CompositeState transparencyState
public net.minecraft.client.renderer.RenderType$CompositeState cullState
public net.minecraft.client.renderer.RenderStateShard$EmptyTextureStateShard cutoutTexture()Ljava/util/Optional;
public net.minecraft.client.renderer.RenderStateShard$MultiTextureStateShard cutoutTexture()Ljava/util/Optional;
public net.minecraft.client.renderer.RenderStateShard$TextureStateShard cutoutTexture()Ljava/util/Optional;
```

If ModDevGradle reports an owner/name mismatch, inspect `build/createMinecraftArtifacts` sources and correct only these narrowly scoped class, field, and override methods before proceeding; do not widen unrelated Minecraft classes.

- [ ] **Step 5: Implement RenderType inspection**

`RenderTypeDescriptor` contains name, `PrimitiveMode`, optional texture resource ID, alpha mode, `Optional<Float>` cutoff, cull, emissive, blend semantic, mipmap, and `discard` flag. Discard text, text background, shadow, outline, fire, and debug RenderTypes by stable lower-case name. Recognize solid, cutout, translucent, eyes/emissive, and glint names; otherwise inspect transparency/cull/texture state and emit diagnostic `UNKNOWN_RENDER_TYPE` when using inference.

- [ ] **Step 6: Implement MultiBufferSource grouping**

`getBuffer(RenderType)` creates one consumer per RenderType identity. `finishAll()` flushes each, skips discarded descriptors, converts source mode, and returns primitives plus diagnostics. Preserve ordered first use with `LinkedHashMap`.

- [ ] **Step 7: Verify and commit**

Run: `./gradlew.bat test build`

Expected: consumer tests pass and access transforms compile against NeoForge 21.1.248.

```powershell
git add src/main/resources/META-INF src/main/java/com/onecuber/mcgltf/capture src/test/java/com/onecuber/mcgltf/capture
git commit -m "feat: capture renderer vertex streams"
```

---

### Task 11: Export independent textures and resolve materials

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/texture/TextureImage.java`
- Create: `src/main/java/com/onecuber/mcgltf/texture/TextureRegistry.java`
- Create: `src/main/java/com/onecuber/mcgltf/texture/SpriteTextureExtractor.java`
- Create: `src/main/java/com/onecuber/mcgltf/texture/ResourceTextureExtractor.java`
- Create: `src/main/java/com/onecuber/mcgltf/material/MaterialResolver.java`
- Create: `src/main/java/com/onecuber/mcgltf/material/MaterialSidecarWriter.java`
- Test: `src/test/java/com/onecuber/mcgltf/texture/TextureRegistryTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/texture/UvNormalizationTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/material/MaterialResolverTest.java`

- [ ] **Step 1: Write failing pure texture/material tests**

Assert atlas UV `(u0,v0)` becomes `(0,0)`, `(u1,v1)` becomes `(1,1)`, and midpoint becomes `(0.5,0.5)`. Reject zero-width Sprite bounds. Assert same resource ID deduplicates and different IDs with identical bytes remain separate. Assert solid/cutout/translucent/emissive/glint descriptors map to the approved material semantics.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.texture.*" --tests "com.onecuber.mcgltf.material.*"`

Expected: FAIL because texture/material classes do not exist.

- [ ] **Step 3: Implement immutable texture payloads and registry**

`TextureImage` stores width, height, cloned RGBA bytes, optional source PNG bytes, optional mcmeta bytes, and `Optional<AnimationInfo>`. Its nested `AnimationInfo` record contains frame width, frame height, immutable frame order, immutable frame times, and interpolation flag. `TextureRegistry` keys by `TextureKey`, assigns insertion-order indices, and writes PNG payloads under the transaction root without path escape.

- [ ] **Step 4: Implement Sprite extraction on the client thread**

Use `sprite.contents().name()` for identity, `contents.width()/height()` for logical frame dimensions, and `contents.byMipLevel[0]` exposed by NeoForge. Parse `.png.mcmeta` before cropping: choose the first explicit animation frame or frame 0, compute `columns = imageWidth / frameWidth`, `sourceX = (frameIndex % columns) * frameWidth`, and `sourceY = (frameIndex / columns) * frameHeight`, then copy exactly one logical frame into the RGBA payload. Convert NativeImage ABGR integers explicitly into RGBA bytes. Copy original PNG and `.png.mcmeta` from the resource manager when metadata reports multiple frames.

- [ ] **Step 5: Implement resource and dynamic texture extraction**

For resource textures, resolve `textures/<path>.png` through the active ResourceManager and copy bytes. For `DynamicTexture`, copy `getPixels()` on the render thread and hash RGBA bytes with SHA-256 for `textures/generated/<first16>.png`. When no CPU image exists, register the generated purple/black 16×16 checkerboard and diagnostic `TEXTURE_READ_FAILED`.

- [ ] **Step 6: Implement material resolution and sidecars**

Resolve `MaterialKey` from `RenderTypeDescriptor` and texture identity. `MaterialSidecarWriter` writes one JSON per glTF material with schema version, glTF index, source texture, exported relative URI, render type, alpha, double-sided, emissive, sampler, animation, and degradation codes.

- [ ] **Step 7: Verify and commit**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.texture.*" --tests "com.onecuber.mcgltf.material.*"`

Expected: all texture/material tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/texture src/main/java/com/onecuber/mcgltf/material src/test/java/com/onecuber/mcgltf/texture src/test/java/com/onecuber/mcgltf/material
git commit -m "feat: export independent textures and materials"
```

---

### Task 12: Extract final BakedModel block geometry

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java`
- Create: `src/main/java/com/onecuber/mcgltf/world/ExportPlan.java`
- Create: `src/main/java/com/onecuber/mcgltf/world/WorldPlanner.java`
- Test: `src/test/java/com/onecuber/mcgltf/capture/BlockQuadPolicyTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/world/ExportPlanTest.java`

- [ ] **Step 1: Write failing policy and planning tests**

Test pure helpers for stable direction order `[DOWN,UP,NORTH,SOUTH,WEST,EAST,null]`, resetting the random seed before every direction, keeping a face at selection or missing-chunk boundary, and culling a face only when both positions are inside loaded selection data and `Block.shouldRenderFace` says hidden. Test section sorting and missing-chunk deduplication.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.capture.BlockQuadPolicyTest" --tests "com.onecuber.mcgltf.world.ExportPlanTest"`

Expected: FAIL because block extraction/planning classes do not exist.

- [ ] **Step 3: Implement ClientLevel planning**

Enumerate intersecting chunk coordinates. Use `level.hasChunk(chunkX,chunkZ)` without causing loads. Add loaded section intersections clipped to selection and build height; collect missing chunk pairs. Sort loaded sections using `ChunkSectionRef.compareTo`.

- [ ] **Step 4: Implement BakedModel extraction**

For each non-air MODEL block:

1. Get `BakedModel` from `Minecraft.getInstance().getBlockRenderer().getBlockModel(state)`.
2. Get base `ModelData` from the block entity or `ModelData.EMPTY`.
3. Call `model.getModelData(level,pos,state,base)`.
4. Call `model.getRenderTypes(state,random,data)` using the block seed.
5. For each render type and direction, reset random to `state.getSeed(pos)` and call the five-argument `getQuads`.
6. Apply selection-aware face culling.
7. Read tint with `Minecraft.getInstance().getBlockColors().getColor(state,level,pos,tintIndex)`.
8. Build a PoseStack translated by local block position and `state.getOffset(level,pos)`.
9. Feed each quad through `CapturingVertexConsumer.putBulkData` using brightness 1, full-bright packed light, and no overlay.
10. Normalize Sprite UVs, resolve texture/material, transform coordinates, and append four vertices plus six mirrored indices to the chunk accumulator.

Skip INVISIBLE; leave ENTITYBLOCK_ANIMATED dynamic output to BER capture. Catch one block at a time and emit `BLOCK_MODEL_CAPTURE_FAILED` without aborting the section.

- [ ] **Step 5: Verify compilation and unit tests**

Run: `./gradlew.bat test build`

Expected: `BUILD SUCCESSFUL` and all pure tests pass.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/capture/BlockModelExtractor.java src/main/java/com/onecuber/mcgltf/world src/test/java/com/onecuber/mcgltf/capture/BlockQuadPolicyTest.java src/test/java/com/onecuber/mcgltf/world
git commit -m "feat: extract baked block models"
```

---

### Task 13: Capture fluids with selection-boundary sealing

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/capture/SelectionBlockView.java`
- Create: `src/main/java/com/onecuber/mcgltf/capture/FluidGeometryCapture.java`
- Test: `src/test/java/com/onecuber/mcgltf/capture/FluidSpriteClassifierTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/capture/SelectionBlockViewPolicyTest.java`

- [ ] **Step 1: Write failing fluid policy tests**

Test that positions outside selection or inside a missing chunk return air/empty-fluid policy, positions inside loaded selection delegate, UVs wholly inside still/flow/overlay rectangles classify correctly, ambiguous UVs use the smallest containing Sprite rectangle, and unmatched UVs produce `FLUID_SPRITE_UNRESOLVED`.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.capture.Fluid*" --tests "com.onecuber.mcgltf.capture.SelectionBlockViewPolicyTest"`

Expected: FAIL because fluid classes do not exist.

- [ ] **Step 3: Implement the clipped BlockAndTintGetter**

Delegate `getShade`, `getLightEngine`, `getBlockTint`, build height, and all inside-selection block/entity/fluid reads. Return `Blocks.AIR.defaultBlockState()`, `Fluids.EMPTY.defaultFluidState()`, and null block entity outside loaded selection.

- [ ] **Step 4: Implement fluid capture**

For each non-empty FluidState, obtain level-aware still/flow/overlay resource IDs and ARGB tint from `IClientFluidTypeExtensions`. Call `BlockRenderDispatcher.renderLiquid` with the clipped view and a QUADS consumer. After capture, classify each complete quad by its UV rectangle, normalize UVs to the selected Sprite, apply fluid tint, transform geometry, and append material-bound primitives.

If a custom fluid throws `ClassCastException` against the clipped view, retry once with the real ClientLevel and emit `FLUID_BOUNDARY_VIEW_REJECTED`. Any second failure creates a placeholder and `FLUID_CAPTURE_FAILED`.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew.bat test build`

Expected: all tests pass and client API compilation succeeds.

```powershell
git add src/main/java/com/onecuber/mcgltf/capture/SelectionBlockView.java src/main/java/com/onecuber/mcgltf/capture/FluidGeometryCapture.java src/test/java/com/onecuber/mcgltf/capture
git commit -m "feat: capture fluid geometry and textures"
```

---

### Task 14: Capture block entities, entities, and unsupported placeholders

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/capture/BlockEntityCapture.java`
- Create: `src/main/java/com/onecuber/mcgltf/capture/EntityCapture.java`
- Create: `src/main/java/com/onecuber/mcgltf/capture/PlaceholderFactory.java`
- Test: `src/test/java/com/onecuber/mcgltf/capture/PlaceholderFactoryTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/capture/EntityFilterTest.java`

- [ ] **Step 1: Write failing placeholder and entity-filter tests**

Assert a unit AABB generates 8 vertices, 12 triangles, BLEND material, alpha 128, magenta RGB, and outward normals. Assert players and removed entities are excluded while living entities, vehicles, armor stands, and item entities are included when their bounding box intersects the inclusive selection AABB.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat test --tests "com.onecuber.mcgltf.capture.PlaceholderFactoryTest" --tests "com.onecuber.mcgltf.capture.EntityFilterTest"`

Expected: FAIL because object capture classes do not exist.

- [ ] **Step 3: Implement block-entity capture**

Get the actual renderer from `Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity)`. Translate PoseStack to local block coordinates. Call renderer directly with partial tick 0, `LightTexture.FULL_BRIGHT`, and `OverlayTexture.NO_OVERLAY`. Finish all RenderType buffers. Store registry ID, world/local position, and renderer class in extras. On exception or zero captured vertices from a non-null renderer, emit a block AABB placeholder and stable diagnostic.

- [ ] **Step 4: Implement entity capture**

Query `level.getEntities(null, selectionAabb, predicate)` once before block work and sort by registry ID then UUID. Exclude Player and removed entities. Get the actual renderer and call `renderer.render(entity, entity.getYRot(), 0.0F, poseStack, buffers, LightTexture.FULL_BRIGHT)` directly, avoiding dispatcher shadow/fire passes. Discard text/name RenderTypes in the buffer source. Store registry ID, UUID, world/local position, and renderer class. On exception or no geometry for a visible non-marker entity, emit its inflated AABB placeholder.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew.bat test build`

Expected: all tests pass and generic renderer calls compile without unchecked failures being promoted to errors.

```powershell
git add src/main/java/com/onecuber/mcgltf/capture src/test/java/com/onecuber/mcgltf/capture
git commit -m "feat: capture block entities and entities"
```

---

### Task 15: Add a development-only compatibility fixture mod

**Files:**
- Modify: `build.gradle`
- Create: `src/testmod/resources/META-INF/neoforge.mods.toml`
- Create: `src/testmod/resources/assets/mcgltf_test/blockstates/model_data_block.json`
- Create: `src/testmod/resources/assets/mcgltf_test/blockstates/rendered_block.json`
- Create: `src/testmod/resources/assets/mcgltf_test/blockstates/gpu_only_block.json`
- Create: `src/testmod/resources/assets/mcgltf_test/models/block/model_data_block.json`
- Create: `src/testmod/resources/assets/mcgltf_test/models/block/rendered_block.json`
- Create: `src/testmod/resources/assets/mcgltf_test/models/block/gpu_only_block.json`
- Create: `src/testmod/resources/assets/mcgltf_test/lang/en_us.json`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/McGltfTestMod.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/TestContent.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/TestModelDataBlockEntity.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/TestRenderedBlockEntity.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/GpuOnlyBlockEntity.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/TestEntity.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/TestFluidRegistration.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/client/McGltfTestClient.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/client/TestBakedModel.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/client/TestBlockEntityRenderer.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/client/TestEntityRenderer.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/client/GpuOnlyBlockEntityRenderer.java`

- [ ] **Step 1: Add a source set that loads only in development runs**

Configure `sourceSets.testmod`, extend its compile/runtime configurations from main, and add a second NeoForge mod binding:

```groovy
sourceSets {
    testmod {
        java.srcDir 'src/testmod/java'
        resources.srcDir 'src/testmod/resources'
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}

configurations {
    testmodImplementation.extendsFrom implementation
    testmodRuntimeOnly.extendsFrom runtimeOnly
}

neoForge.mods {
    mcgltf_test {
        sourceSet(sourceSets.testmod)
    }
}
```

The production jar remains sourced only from `sourceSets.main`; confirm `mcgltf_test` classes do not appear in `build/libs/mcgltf-0.1.0.jar`.

- [ ] **Step 2: Register deterministic fixture content**

Use mod ID `mcgltf_test`. Register three blocks and matching block entities, one simple entity, and a `BaseFlowingFluid` family with source, flowing, liquid block, bucket, and FluidType. The custom fluid client extension must return vanilla `minecraft:block/water_still`, `minecraft:block/water_flow`, and tint `0xFF8A4FFF`, allowing geometry and tint verification without shipping binary test textures.

The model-data block entity exposes `ModelProperty<Integer> PHASE` with value 1. The rendered BER emits a one-block cyan cube through `RenderType.translucent()`. The GPU-only BER intentionally emits no `VertexConsumer` calls, exercising placeholder fallback.

- [ ] **Step 3: Wrap the baked model after model baking**

In `ModelEvent.ModifyBakingResult`, locate every `ModelResourceLocation` whose namespace is `mcgltf_test` and path is `model_data_block`, replace its value with `TestBakedModel(delegate)`, and preserve all other map entries.

`TestBakedModel` delegates base methods, returns the incoming ModelData, verifies `PHASE == 1` in its five-argument `getQuads`, and returns `ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent())`. It emits delegate quads only for cutout and emits an empty list for translucent, proving that the exporter asks every model-provided RenderType without duplicating geometry.

- [ ] **Step 4: Register deterministic BER and entity renderers**

Register renderers through `EntityRenderersEvent.RegisterRenderers`. Both visible renderers output complete QUADS with explicit position, RGBA, UV, and normal fields. The entity renderer uses `minecraft:textures/block/amethyst_block.png`; the BER uses `minecraft:textures/block/diamond_block.png`. The GPU-only renderer records no standard vertices.

- [ ] **Step 5: Add resource JSON**

Each blockstate maps the empty variant to its same-named block model. The model-data block uses parent `minecraft:block/cube_all` and texture `minecraft:block/amethyst_block`. The rendered and GPU-only blocks use parent `minecraft:block/block` so only their BER contributes geometry. The test metadata declares MIT and dependencies on `mcgltf`, Minecraft 1.21.1, and NeoForge 21.1.248.

- [ ] **Step 6: Compile the fixture and verify production isolation**

Run:

```powershell
./gradlew.bat compileTestmodJava processTestmodResources build
jar tf build/libs/mcgltf-0.1.0.jar | Select-String 'mcgltf_test'
```

Expected: compilation succeeds and `Select-String` produces no output.

- [ ] **Step 7: Launch the fixture client and record expected objects**

Run: `./gradlew.bat runClient`

Place `model_data_block`, `rendered_block`, `gpu_only_block`, the custom fluid, and summon the custom entity. Export the enclosing selection. Expected: model-data block appears once, cyan BER and custom entity appear, purple fluid tint is preserved, and the GPU-only block creates one magenta placeholder with `UNSUPPORTED_GPU_RENDERER` or `BLOCK_ENTITY_ZERO_VERTICES` in the report.

- [ ] **Step 8: Commit the fixture**

```powershell
git add build.gradle src/testmod
git commit -m "test: add client rendering compatibility fixtures"
```

---

### Task 16: Orchestrate rolling snapshots, writer backpressure, completion, and external validation

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/job/ExportJob.java`
- Modify: `src/main/java/com/onecuber/mcgltf/job/ExportJobManager.java`
- Modify: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- Create: `src/test/java/com/onecuber/mcgltf/job/ExportJobTest.java`
- Create: `tools/package.json`
- Create: `tools/validate-gltf.mjs`
- Create: `README.md`
- Create: `docs/testing/manual-client-matrix.md`

- [ ] **Step 1: Write failing orchestration tests with fakes**

Use fake planner, entity capture, section capture, and writer. Assert entities run first; sections run in stable order; a six-millisecond budget resumes next tick; queue depth two pauses capture; cancellation stops new batches and closes the transaction; writer failure transitions FAILED; successful writer completion transitions WRITING then COMPLETED; warning diagnostics produce `completed_with_warnings`.

- [ ] **Step 2: Run orchestration tests and verify failure**

Run: `./gradlew.bat test --tests com.onecuber.mcgltf.job.ExportJobTest`

Expected: FAIL because `ExportJob` does not exist.

- [ ] **Step 3: Implement the rolling export job**

`ExportJob` implements `ManagedJob`; all state mutation occurs on the client thread except writer completion, which posts one immutable result consumed at the start of the next `tick()`.

On start:

1. Validate world, selection, name, and confirmation.
2. Build `ExportPlan` without loading chunks and copy its sorted missing-chunk list into the report.
3. Resolve the export root as `Minecraft.getInstance().gameDirectory.toPath().resolve("mcgltf-exports")` and begin `OutputTransaction`.
4. Record game time and root extras, including Minecraft/NeoForge versions, dimension, selection, origin, active resource packs, and loaded mods.
5. Start one named daemon writer thread with `ArrayBlockingQueue<>(2)`.
6. Capture sorted entities.
7. On each tick, process positions until the six-millisecond budget expires or queue backpressure applies.
8. Seal each section into `ChunkBatch` and enqueue it.
9. Send one terminal marker after capture.
10. Let the writer finish textures, materials, glTF, and report; run internal validation; publish only when no fatal diagnostics exist.

Catch `OutOfMemoryError` and disk I/O failure at the job boundary, record a fatal code, close the transaction, and never publish.

- [ ] **Step 4: Wire manager factories and translated completion messages**

`McGltfClient` constructs the selection store, job manager, and default factory once. Tick calls manager tick. Commands call the manager. Logout, dimension change, and reload call `cancel(reason)`. Completion message includes final directory and warning count.

- [ ] **Step 5: Add Khronos validator tooling**

`tools/package.json`:

```json
{
  "private": true,
  "type": "module",
  "devDependencies": {
    "gltf-validator": "2.0.0-dev.3.10"
  },
  "scripts": {
    "validate": "node validate-gltf.mjs"
  }
}
```

`validate-gltf.mjs` must read the input path from `process.argv[2]`, call `validateBytes` with an `externalResourceFunction` resolving URIs relative to the glTF file, print the JSON report, and exit 1 when `issues.numErrors > 0`.

Run:

```powershell
Set-Location tools
npm install
npm run validate -- ..\run\mcgltf-exports\smoke\smoke.gltf
```

Expected after a smoke export: validator JSON reports `numErrors: 0`.

- [ ] **Step 6: Add README and the manual acceptance matrix**

README must document installation, client-only behavior, every command, 4,194,304 soft limit, six-millisecond budget, output tree, rolling snapshot, Blender import, unsupported GPU paths, and MIT license.

The manual matrix must contain repeatable rows for stone, grass, leaves, glass, water, lava, chest, sign, banner, cow, armor stand, dropped item, boat, one installed modded BakedModel, one modded BER/entity, one unloaded chunk, cancellation, resource reload cancellation, duplicate name suffixing, Blender import, and Khronos zero-error validation. Each row has setup, command, expected files, expected visual result, and report expectation.

- [ ] **Step 7: Run the complete automated verification**

Run:

```powershell
./gradlew.bat clean test build
```

Expected: `BUILD SUCCESSFUL`, no failed tests, and mod jar at `build/libs/mcgltf-0.1.0.jar`.

- [ ] **Step 8: Run the development client smoke workflow**

Run: `./gradlew.bat runClient`

In a test world:

```text
/mcgltf pos1
/mcgltf pos2
/mcgltf export smoke
/mcgltf status
```

Expected: `<gameDir>/mcgltf-exports/smoke/` contains `smoke.gltf`, `smoke.bin`, `textures/`, `materials/`, and `report.json`; unloaded chunks are listed rather than loaded.

- [ ] **Step 9: Validate and inspect in Blender**

Run the npm validator command from Step 5 and import `smoke.gltf` into Blender. Expected: zero validator errors, textures resolve without relinking, one-block units import at one meter, hierarchy contains Chunks/BlockEntities/Entities/Placeholders, and world origin metadata appears in extras.

- [ ] **Step 10: Commit the complete pipeline**

```powershell
git add src/main src/test tools README.md docs/testing
git commit -m "feat: complete client world glTF export pipeline"
```

---

## Final verification gate

Before declaring implementation complete, run every command below from the repository root and preserve their output in the execution log:

```powershell
git status --short
./gradlew.bat clean test build
```

Expected:

- `git status --short` is empty before release packaging.
- Gradle reports `BUILD SUCCESSFUL`.
- All JUnit tests pass.
- `build/libs/mcgltf-0.1.0.jar` exists.

Then perform the Task 16 client smoke export, Khronos validation, and Blender matrix. Completion requires all functional checklist items in the approved design spec to have evidence in either an automated test or `docs/testing/manual-client-matrix.md`.
