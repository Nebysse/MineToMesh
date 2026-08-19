# MineToMesh Fabric 26.2 Multiloader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the Minecraft 1.21.1 NeoForge 1.2.0 build while adding a full-featured Minecraft 26.2 Fabric `1.2.0-fabric-alpha.1` build with one shared export core.

**Architecture:** Convert the repository into `common`, `neoforge-1.21.1`, and `fabric-26.2` Gradle subprojects. Keep all Minecraft and loader APIs in platform modules; package the Java 21 common output into both final mod JARs. Port the Fabric platform incrementally from registration and networking through client rendering capture, with compile/test/smoke gates after every subsystem.

**Tech Stack:** Gradle 9.2.1, Java 21/25 Toolchains, NeoForge ModDev 2.0.143, NeoForge 21.1.244, Fabric Loom 1.17-SNAPSHOT, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, JUnit 5.11.4, Gson 2.10.1.

---

## File structure map

- `common/`: loader-free scene model, exporters, writer transaction, reports, job state machine, pure policies, and their tests.
- `neoforge-1.21.1/`: the current Minecraft 1.21.1 implementation, resources, test mod, and all NeoForge-facing tests.
- `fabric-26.2/`: Minecraft 26.2/Fabric initializers, content, payloads, menu, client UI, render capture, textures, pipeline, resources, and Fabric tests.
- `build.gradle`: root aggregation only.
- `gradle.properties`: shared identity plus platform-specific version properties.
- `settings.gradle`: plugin repositories, Foojay resolver, and the three included projects.
- `README.md`: platform matrix and platform-specific build/install instructions.

## Execution precondition

Run implementation in an isolated worktree created from commit `52f1942`. Do not copy the current main worktree’s unrelated `.gitignore`, `$null`, or `模组封面.png` changes into it.

---

### Task 1: Freeze the NeoForge baseline and create structure contracts

**Files:**
- Create: `docs/testing/multiloader-baseline.md`
- Create: `src/test/java/com/nebysse/minetomesh/MultiloaderStructureContractTest.java` temporarily, then move it to `neoforge-1.21.1/src/test/java/com/nebysse/minetomesh/MultiloaderStructureContractTest.java` in Task 2
- Read: `build.gradle`
- Read: `gradle.properties`
- Read: `settings.gradle`

- [ ] **Step 1: Record the baseline commands and results**

Write `docs/testing/multiloader-baseline.md` with the exact baseline:

```markdown
# Multiloader migration baseline

Source commit: 52f1942

- `./gradlew.bat test --no-configuration-cache`: must pass before migration.
- Java runtime before migration: Temurin 21.0.12.
- NeoForge target: Minecraft 1.21.1, NeoForge 21.1.244, MineToMesh 1.2.0.
- Fabric target: Minecraft 26.2, Loader 0.19.3, Fabric API 0.157.0+26.2, MineToMesh 1.2.0-fabric-alpha.1.
```

- [ ] **Step 2: Write the failing structure contract**

```java
package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MultiloaderStructureContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void repositoryContainsAllThreeModules() {
        assertTrue(Files.isDirectory(ROOT.resolve("common")));
        assertTrue(Files.isDirectory(ROOT.resolve("neoforge-1.21.1")));
        assertTrue(Files.isDirectory(ROOT.resolve("fabric-26.2")));
    }
}
```

- [ ] **Step 3: Run the contract and verify RED**

Run:

```powershell
./gradlew.bat test --tests "*MultiloaderStructureContractTest" --no-configuration-cache
```

Expected: FAIL because `common`, `neoforge-1.21.1`, and `fabric-26.2` do not exist.

- [ ] **Step 4: Re-run the existing baseline**

Run:

```powershell
./gradlew.bat test --no-configuration-cache
```

Expected: BUILD SUCCESSFUL before files are moved.

- [ ] **Step 5: Commit the baseline**

```powershell
git add docs/testing/multiloader-baseline.md src/test/java/com/nebysse/minetomesh/MultiloaderStructureContractTest.java
git commit -m "test: freeze multiloader migration baseline"
```

---

### Task 2: Convert the root into a three-project Gradle build without changing NeoForge behavior

**Files:**
- Modify: `settings.gradle`
- Modify: `build.gradle`
- Modify: `gradle.properties`
- Create: `common/build.gradle`
- Create: `neoforge-1.21.1/build.gradle`
- Create: `fabric-26.2/build.gradle`
- Move: `src/**` → `neoforge-1.21.1/src/**`
- Move: current NeoForge build logic → `neoforge-1.21.1/build.gradle`

- [ ] **Step 1: Replace `settings.gradle` with the project declaration**

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'MineToMesh'
include 'common', 'neoforge-1.21.1', 'fabric-26.2'
```

- [ ] **Step 2: Make the root build an aggregator**

```groovy
plugins {
    id 'base'
}

group = mod_group_id
version = neoforge_mod_version

subprojects {
    group = rootProject.mod_group_id

    repositories {
        mavenCentral()
    }
}

tasks.named('build') {
    dependsOn ':common:build', ':neoforge-1.21.1:build', ':fabric-26.2:build'
}
```

- [ ] **Step 3: Split version properties**

Keep existing JVM/Gradle settings and replace platform properties with:

```properties
minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.21.2)
neo_version=21.1.244
loader_version_range=[4,)

fabric_minecraft_version=26.2
fabric_loader_version=0.19.3
fabric_api_version=0.157.0+26.2
fabric_loom_version=1.17-SNAPSHOT

mod_id=minetomesh
mod_name=MineToMesh
mod_license=MIT
neoforge_mod_version=1.2.0
fabric_mod_version=1.2.0-fabric-alpha.1
mod_group_id=com.nebysse.minetomesh
```

- [ ] **Step 4: Move the current project into `neoforge-1.21.1`**

Use `git mv` for every tracked source subtree. Preserve `src/main`, `src/test`, and `src/testmod` exactly. Copy the current `build.gradle` into `neoforge-1.21.1/build.gradle`, then change only:

```groovy
version = rootProject.neoforge_mod_version
base.archivesName = "MineToMesh-${version}-neoforge-1.21.1"
```

Change template expansion from `mod_version: mod_version` to:

```groovy
mod_version: rootProject.neoforge_mod_version
```

Move the structure contract with the rest of `src/test`.

- [ ] **Step 5: Add the initial `common/build.gradle`**

```groovy
plugins {
    id 'java-library'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

dependencies {
    api 'com.google.code.gson:gson:2.10.1'
    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}
```

- [ ] **Step 6: Add a compilable Fabric Gradle shell**

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${rootProject.fabric_loom_version}"
    id 'maven-publish'
}

version = rootProject.fabric_mod_version
group = rootProject.mod_group_id
base.archivesName = "MineToMesh-${version}+mc26.2"

loom {
    splitEnvironmentSourceSets()

    mods {
        minetomesh {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}

dependencies {
    minecraft "com.mojang:minecraft:${rootProject.fabric_minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${rootProject.fabric_loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${rootProject.fabric_api_version}"
    implementation project(':common')
    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 25
}
```

- [ ] **Step 7: Verify the NeoForge migration GREEN**

Run:

```powershell
./gradlew.bat :neoforge-1.21.1:test :neoforge-1.21.1:build --no-configuration-cache
```

Expected: all pre-existing tests pass and the NeoForge JAR is generated with the new platform-qualified filename.

- [ ] **Step 8: Commit the structural migration**

```powershell
git add settings.gradle build.gradle gradle.properties common neoforge-1.21.1 fabric-26.2
git commit -m "build: split MineToMesh into platform modules"
```

---

### Task 3: Extract and enforce the loader-free common core

**Files:**
- Move to `common/src/main/java`: `gltf/**`, `material/**`, `output/**`, `report/**`, `scene/**`, `usd/**`
- Move to `common/src/main/java`: pure job, world, capture, texture, backend, command, wand, and client-policy classes listed below
- Move matching tests to `common/src/test/java`
- Create: `common/src/test/java/com/nebysse/minetomesh/CommonPlatformIsolationTest.java`
- Modify: `neoforge-1.21.1/build.gradle`
- Modify: `fabric-26.2/build.gradle`

- [ ] **Step 1: Write the common isolation test and verify RED**

```java
package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CommonPlatformIsolationTest {
    private static final List<String> FORBIDDEN = List.of(
            "net.minecraft.", "net.neoforged.", "net.fabricmc.",
            "com.mojang.blaze3d.", "org.lwjgl.");

    @Test
    void commonSourcesDoNotImportPlatformApis() throws IOException {
        Path root = Path.of("src/main/java");
        try (var files = Files.walk(root)) {
            List<Path> sources = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            assertTrue(sources.size() >= 50,
                    () -> "Common extraction is incomplete: " + sources.size());
            List<Path> violations = sources.stream()
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return FORBIDDEN.stream().anyMatch(source::contains);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertTrue(violations.isEmpty(), () -> "Platform imports in common: " + violations);
        }
    }
}
```

Run `./gradlew.bat :common:test --tests "*CommonPlatformIsolationTest"`. Expected RED until the source split is complete.

- [ ] **Step 2: Move whole loader-free packages**

Move these complete packages and corresponding behavioral tests into `common`:

```text
com/nebysse/minetomesh/gltf/**
com/nebysse/minetomesh/material/**
com/nebysse/minetomesh/output/**
com/nebysse/minetomesh/report/**
com/nebysse/minetomesh/scene/**
com/nebysse/minetomesh/usd/**
```

- [ ] **Step 3: Move individually verified pure classes**

Move these production classes and their direct unit tests:

```text
backend/RenderBackendAdapter.java
capture/BlockPrimitiveRouter.java
capture/CaptureState.java
capture/CoplanarQuadLayering.java
capture/ObjectCaptureDecision.java
capture/PlaceholderFactory.java
capture/RendererReplay.java
capture/RenderTypeDescriptor.java
client/selection/WorldProfileKey.java
client/wand/CoordinateEditorModel.java
client/wand/ExportWandBorderPolicy.java
client/wand/OverlaySnapshotPolicy.java
command/CommandPolicy.java
job/CancellationToken.java
job/CaptureBudget.java
job/ExportJob.java
job/ExportJobManager.java
job/ExportOptions.java
job/ExportProgress.java
job/ExportSummary.java
job/ExportTelemetry.java
job/JobState.java
job/ManagedJob.java
texture/GpuTextureAccess.java
texture/TextureAcquisitionChain.java
texture/TextureImage.java
texture/TextureRegistry.java
wand/Axis.java
wand/Endpoint.java
wand/WandInteractionPolicy.java
world/BlockPoint.java
world/ChunkSectionRef.java
world/ExportPlan.java
world/Selection.java
world/SelectionStore.java
```

Do not move `WandClientReceiver`, `RenderBackendRegistry`, `DefaultExportPipeline`, `WorldPlanner`, Minecraft-facing texture classes, payloads, GUI, menus, or capture adapters.

- [ ] **Step 4: Add common output to both platform classpaths and final JARs**

Add to each platform module:

```groovy
dependencies {
    implementation project(':common')
}

tasks.named('jar', Jar).configure {
    from(project(':common').sourceSets.main.output)
}
```

Keep Fabric’s `remapJar` as the distributable artifact and NeoForge’s normal `jar` as its distributable artifact.

- [ ] **Step 5: Verify common and NeoForge GREEN**

Run:

```powershell
./gradlew.bat :common:test :neoforge-1.21.1:test :neoforge-1.21.1:build --no-configuration-cache
```

Expected: common isolation passes, all moved tests pass, and all remaining NeoForge tests pass.

- [ ] **Step 6: Verify JAR embedding**

Open the NeoForge JAR as ZIP and assert it contains:

```text
com/nebysse/minetomesh/gltf/GltfDocumentBuilder.class
com/nebysse/minetomesh/usd/StreamingUsdaSession.class
com/nebysse/minetomesh/scene/Vertex.class
```

- [ ] **Step 7: Commit the common split**

```powershell
git add common neoforge-1.21.1/build.gradle fabric-26.2/build.gradle
git commit -m "refactor: extract loader-free export core"
```

---

### Task 4: Remove remaining reverse dependencies from common abstractions

**Files:**
- Create: `common/src/main/java/com/nebysse/minetomesh/backend/RenderBackendDiscovery.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/job/ExportEnvironment.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/job/StreamingBatchSink.java`
- Create matching tests in `common/src/test/java`
- Modify: `neoforge-1.21.1/.../backend/RenderBackendRegistry.java`
- Modify: `neoforge-1.21.1/.../job/DefaultExportPipeline.java`

- [ ] **Step 1: Test explicit backend discovery**

Create a test requiring platform adapters to be supplied explicitly:

```java
@Test
void explicitAdaptersPrecedeServiceLoadedAdapters() {
    RenderBackendAdapter explicit = adapter("explicit", true);
    RenderBackendDiscovery discovery = new RenderBackendDiscovery(List.of(explicit));
    RenderBackendRegistry registry = discovery.discover(getClass().getClassLoader());
    assertEquals(List.of("explicit"), registry.adapterIds());
}
```

Expected RED because the discovery seam does not exist.

- [ ] **Step 2: Implement the discovery seam**

`RenderBackendDiscovery` accepts `List<RenderBackendAdapter>` from the platform, then appends unique `ServiceLoader` adapters by ID. Move the loader-neutral `RenderBackendRegistry` into common and remove direct construction of `FlywheelBackendAdapter`. NeoForge passes `FlywheelBackendAdapter.discover(classLoader).stream().toList()`.

- [ ] **Step 3: Add immutable export environment metadata**

```java
package com.nebysse.minetomesh.job;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExportEnvironment(
        String minecraftVersion,
        String loaderName,
        String loaderVersion,
        String exporterVersion,
        List<String> activeResourcePacks,
        List<String> loadedMods) {
    public ExportEnvironment {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loaderName, "loaderName");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(exporterVersion, "exporterVersion");
        activeResourcePacks = List.copyOf(activeResourcePacks);
        loadedMods = List.copyOf(loadedMods);
    }

    public Map<String, Object> asExtras() {
        return Map.of(
                "minecraftVersion", minecraftVersion,
                "loader", loaderName,
                "loaderVersion", loaderVersion,
                "exporterVersion", exporterVersion,
                "activeResourcePacks", activeResourcePacks,
                "loadedMods", loadedMods);
    }
}
```

Add tests for defensive copies and exact metadata keys.

- [ ] **Step 4: Extract the asynchronous writer from `DefaultExportPipeline`**

Move the current `AsyncBatchSink` logic into `StreamingBatchSink` without changing queue capacity, finish/cancel behavior, transaction cleanup, report writing, glTF validation, USDA writing, or writer result semantics. Its constructor receives `OutputTransaction`, `TextureRegistry`, `ExportName`, `ExportPlan`, root extras, game time, and `ExportTelemetry`; it must not receive `Minecraft` or a loader object.

- [ ] **Step 5: Rewire NeoForge and verify parity**

`DefaultExportPipeline` builds an `ExportEnvironment("1.21.1", "neoforge", loadedModVersion("neoforge"), MineToMesh.VERSION, ...)`, merges selection extras, then constructs `StreamingBatchSink`.

Run all common and NeoForge tests. Expected GREEN with byte-for-byte-equivalent schema keys except the new generic `loader` and `loaderVersion` additions.

- [ ] **Step 6: Commit the dependency inversion**

```powershell
git add common neoforge-1.21.1
git commit -m "refactor: isolate platform capture from shared export writer"
```

---

### Task 5: Create the Fabric 26.2 mod shell, metadata, resources, and packaging tests

**Files:**
- Create: `fabric-26.2/src/main/java/com/nebysse/minetomesh/fabric/MineToMeshFabric.java`
- Create: `fabric-26.2/src/client/java/com/nebysse/minetomesh/fabric/client/MineToMeshFabricClient.java`
- Create: `fabric-26.2/src/main/resources/fabric.mod.json`
- Create: `fabric-26.2/src/test/java/com/nebysse/minetomesh/fabric/FabricMetadataTest.java`
- Copy/adapt: textures, language files, logo, item model, recipe

- [ ] **Step 1: Write failing metadata tests**

Assert `fabric.mod.json` contains:

```json
{
  "id": "minetomesh",
  "environment": "*",
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```

Also assert separate `main` and `client` entrypoints and icon `minetomesh_logo.png`. Expected RED because metadata is absent.

- [ ] **Step 2: Implement initializers**

```java
public final class MineToMeshFabric implements ModInitializer {
    public static final String MOD_ID = "minetomesh";
    public static final String DISPLAY_NAME = "MineToMesh";
    public static final String VERSION = "1.2.0-fabric-alpha.1";

    @Override
    public void onInitialize() {
        FabricContent.register();
        FabricWandPayloads.registerServer();
        FabricWandInteractions.register();
    }
}
```

```java
@Environment(EnvType.CLIENT)
public final class MineToMeshFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientBootstrap.initialize();
    }
}
```

Create empty package-private registration methods only long enough to establish compilation; each subsequent task replaces them with tested behavior.

- [ ] **Step 3: Process version metadata**

Configure `processResources` to expand only `${version}` in `fabric.mod.json`, and include shared textures/languages/logo from the NeoForge resource source only through an explicit `from` block with duplicate strategy `EXCLUDE`. Keep Fabric-specific item model and recipe local.

- [ ] **Step 4: Verify Java 25 toolchain and Fabric packaging**

Run:

```powershell
./gradlew.bat :fabric-26.2:test :fabric-26.2:build --no-configuration-cache
```

Expected: Gradle provisions Java 25 if absent, tests pass, and `MineToMesh-1.2.0-fabric-alpha.1+mc26.2.jar` contains metadata, entrypoints, logo, language files, GUI textures, and common exporter classes.

- [ ] **Step 5: Commit the shell**

```powershell
git add fabric-26.2
git commit -m "feat(fabric): add 26.2 mod shell and metadata"
```

---

### Task 6: Port content registration, wand state, menu, and resources

**Files:**
- Create under `fabric-26.2/src/main/java`: `content/FabricContent.java`, `wand/ExportWandItem.java`, `wand/ExportWandMenu.java`, `wand/ExportWandSelection.java`, `wand/ExportWandService.java`, `wand/WandBinding.java`, `wand/WandAirTarget.java`
- Create Fabric tests mirroring current NeoForge content/wand tests
- Create/adapt item model and recipe JSON for 26.2

- [ ] **Step 1: Port behavioral tests before implementation**

Copy the existing tests for selection serialization, wand binding, menu validity, air targeting, service mutation, item resource existence, recipe, and workstation removal. Change only package imports and platform bootstrap. Run Fabric tests and verify RED due to missing classes/registrations.

- [ ] **Step 2: Register Fabric content**

Use `Registry.register` for the item, data component types, menu type, and creative-tab exposure. Preserve runtime IDs:

```text
minetomesh:export_wand
minetomesh:wand_id
minetomesh:wand_selection
minetomesh:wand_export_name
minetomesh:wand_overlay
minetomesh:wand_include_players
minetomesh:export_wand_menu
```

No compatibility aliases for the removed `mcgltf` identity.

- [ ] **Step 3: Port immutable wand state and menu binding**

Preserve UUID identity, dimension, optional POS1/POS2, export name, overlay flag, include-player flag, hand/slot binding, and invalidation when the bound stack moves or changes identity. Use 26.2 codecs and item component APIs rather than reflective storage.

- [ ] **Step 4: Port item interactions and menu opening**

Maintain the existing left/right/shift behavior table and sounds. Menu opening must serialize only the binding snapshot required by the client and must revalidate the live stack on every mutation/export request.

- [ ] **Step 5: Verify resources and content GREEN**

Run the Fabric content/wand tests and `:fabric-26.2:build`. Inspect the JAR for the item model, texture, recipe, translations, and registered entrypoints.

- [ ] **Step 6: Commit content**

```powershell
git add fabric-26.2
git commit -m "feat(fabric): port export wand content and menu"
```

---

### Task 7: Port Fabric networking and server authority

**Files:**
- Create: Fabric counterparts for all nine existing payload records
- Create: `fabric-26.2/.../network/FabricWandPayloads.java`
- Create: Fabric payload codec, permission, menu mutation, receiver lifecycle, and direction tests

- [ ] **Step 1: Copy payload contract tests and verify RED**

Cover round-trip codec behavior for endpoint updates, air endpoint, clear, export request, grant, reject, overlay, include-player, and export-name payloads. Include malformed coordinate, wrong direction, stale UUID, wrong dimension, and insufficient permission cases.

- [ ] **Step 2: Register payload types**

Use Fabric 26.2 networking APIs:

```java
PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
ServerPlayNetworking.registerGlobalReceiver(TYPE, handler);
ClientPlayNetworking.registerGlobalReceiver(TYPE, handler);
```

Register each type in exactly one direction. Client receiver registration belongs in the client initializer.

- [ ] **Step 3: Enforce server-authoritative handlers**

Every C2S handler must execute on the server context and re-read the bound live `ItemStack`. Reuse shared policy logic for permission level 2, coordinate bounds, dimension equality, UUID identity, menu validity, and soft-limit confirmation. Send grant/reject through `ServerPlayNetworking.send`.

- [ ] **Step 4: Keep receiver lifecycle process-wide**

Install grant/reject consumers once during client initialization. World logout clears controller state but does not unregister global receivers.

- [ ] **Step 5: Verify GREEN and dedicated-server class isolation**

Run Fabric network tests, then inspect main entrypoint dependency closure to ensure it does not reference `net.minecraft.client.*` or Fabric client networking classes.

- [ ] **Step 6: Commit networking**

```powershell
git add fabric-26.2
git commit -m "feat(fabric): port authoritative wand networking"
```

---

### Task 8: Port the Fabric client lifecycle, GUI, input, commands, and Overlay

**Files:**
- Create: Fabric counterparts of `MineToMeshClient`, `ExportWandController`, `ExportWandScreen`, `ExportWandTextures`, `HeldWandOverlaySource`, `SelectionOverlayRenderer`, `WandClientInput`, `ClientMessages`, `MineToMeshCommands`
- Reuse common policy classes from Task 3
- Create/adapt Fabric client tests

- [ ] **Step 1: Port GUI and lifecycle tests and verify RED**

Cover layout, Unicode `charTyped`, Enter/Tab behavior, focus loss, movement/hotbar suppression, stale menu binding, lock toggle semantics, dimension visibility, profile isolation, logout cancellation, and network receiver persistence.

- [ ] **Step 2: Register client lifecycle hooks**

Use `ClientTickEvents.END_CLIENT_TICK`, `ClientPlayConnectionEvents.DISCONNECT`, `ClientCommandRegistrationCallback.EVENT`, resource reload registration, screen registration, and `WorldRenderEvents` for Overlay rendering. Keep event adapters thin and delegate behavior to existing controllers/services.

- [ ] **Step 3: Port persistent locked selections**

Retain `config/minetomesh/locked-selections.json`, hashed multiplayer profile keys, singleplayer root keys, corrupt-file quarantine, dimension filtering, and no network synchronization.

- [ ] **Step 4: Port the screen and input policy**

Adapt only changed 26.2 widget/render signatures. Preserve the existing GUI atlas, field positions, scroll modifiers, buttons, status messages, keyboard consumption, and invalid-menu cancellation.

- [ ] **Step 5: Port Overlay rendering**

Render the orange translucent volume and blue depth-obscured border through the 26.2 world render context. Convert the camera-relative transform at the platform boundary; keep lock/held-wand source selection semantics unchanged.

- [ ] **Step 6: Verify client subsystem GREEN**

Run all Fabric client unit/source-contract tests and `:fabric-26.2:compileClientJava`.

- [ ] **Step 7: Commit client shell**

```powershell
git add fabric-26.2
git commit -m "feat(fabric): port client GUI input and selection overlay"
```

---

### Task 9: Port the 26.2 vertex capture and render-type bridge

**Files:**
- Create Fabric 26.2 versions of `CapturingVertexConsumer`, `CapturingMultiBufferSource`, `RenderTypeInspector`, and `CaptureCoordinates`
- Create/adapt capture unit tests
- Create Access Widener or Mixin only if compile-time public APIs cannot expose required state

- [ ] **Step 1: Port capture contract tests and verify RED**

Test position, color, UV, overlay/light tolerance, normals, primitive mode, incomplete vertices, buffer separation, material grouping, and camera-relative transforms. Tests must assert resulting shared `Vertex` and `PrimitiveData`, not implementation call counts.

- [ ] **Step 2: Implement the 26.2 vertex adapter**

Implement the exact 26.2 `VertexConsumer` contract and terminate each logical vertex according to the current API. Convert all captured data immediately into common `Vec2f`, `Vec3f`, `ColorRgba`, and `Vertex` values.

- [ ] **Step 3: Implement render-type inspection**

Extract texture identity, translucency/cutout/blend, culling, emissive/lightmap, and primitive mode into common `RenderTypeDescriptor`. If 26.2 no longer exposes a field publicly, add one narrowly targeted accessor Mixin or Access Widener and document the member signature in the access file.

- [ ] **Step 4: Verify GREEN**

Run Fabric capture tests and `compileClientJava`. Confirm the common isolation test still rejects all platform imports.

- [ ] **Step 5: Commit the capture bridge**

```powershell
git add fabric-26.2 common
git commit -m "feat(fabric): capture 26.2 render vertices and materials"
```

---

### Task 10: Port block models, fluids, world planning, and textures

**Files:**
- Create Fabric versions of `WorldPlanner`, `SelectionBlockView`, `BlockQuadPolicy`, `BlockModelExtractor`, `FluidGeometryCapture`
- Create Fabric versions of `AtlasSpriteIndex`, `AtlasSpriteResolver`, `SpriteTextureExtractor`, `ResourceTextureExtractor`, `GpuTextureProvider`, `GlGpuTextureAccess`, `TextureProvider`
- Port corresponding tests

- [ ] **Step 1: Port planning/model/texture tests and verify RED**

Cover loaded-chunk-only planning, section ordering, deterministic random seeds, culling, model quads, tint overlay routing, actual atlas UV-to-sprite resolution, connected textures, static/dynamic/GPU texture fallback, fluid still/flow sprites, and missing-texture diagnostics.

- [ ] **Step 2: Implement 26.2 world planning**

Iterate only loaded client chunks, produce common `ExportPlan.SectionWork`, and preserve X/Y/Z selection order and section object IDs. Never request or force-load missing chunks.

- [ ] **Step 3: Implement block model extraction**

Use 26.2 model-manager/block-renderer APIs. Convert baked/model geometry to shared vertices, apply tint, route grass overlay separately, preserve source quads for USDA, triangulate for glTF, and apply `1/1024` coplanar layering through common logic.

- [ ] **Step 4: Implement fluid extraction**

Use the 26.2 fluid renderer/sprite source without a NeoForge `FluidSpriteCache`. Preserve top/bottom/side geometry, flow UVs, tint, translucency, and material IDs.

- [ ] **Step 5: Implement texture acquisition**

Retain the acquisition order: resource file → dynamic/native image → GPU readback → missing texture. Normalize atlas UVs to sprite-local UVs and register each shared `TextureKey` once.

- [ ] **Step 6: Verify GREEN**

Run all Fabric planning/model/fluid/texture tests and `compileClientJava`.

- [ ] **Step 7: Commit world capture**

```powershell
git add fabric-26.2
git commit -m "feat(fabric): port block fluid and texture capture"
```

---

### Task 11: Port block entities, entities, backend fallback, and the production pipeline

**Files:**
- Create Fabric versions of `BlockEntityCapture`, `EntityCapture`, `FlywheelBackendAdapter` or a Fabric-safe optional backend adapter, `RenderBackendRegistry`, `DefaultExportPipeline`
- Create/adapt entity, renderer replay, backend, pipeline policy, and auxiliary consistency tests

- [ ] **Step 1: Port entity/backend tests and verify RED**

Cover block-entity and entity render replay, player include/exclude, marker exclusion, entity world bounds, dynamic material global batching, renderer failure stages, partial capture, placeholders, backend scope restoration, and diagnostic counters.

- [ ] **Step 2: Implement renderer replay**

Call 26.2 block-entity and entity render dispatchers with a platform capture buffer and camera-relative pose. Always restore backend state and close scopes in `finally`.

- [ ] **Step 3: Implement backend discovery safely**

Use `FabricLoader.getInstance().isModLoaded` and reflection only after the target mod is present. If no compatible 26.2 Flywheel API exists, register no active Flywheel adapter; renderer failure then follows the existing placeholder/diagnostic path. Do not make Flywheel a hard dependency.

- [ ] **Step 4: Implement the Fabric production pipeline**

Construct `WorldPlanner`, block/fluid/entity capture, texture acquisition, shared `StreamingBatchSink`, and `ExportJob`. Build `ExportEnvironment` from `FabricLoader` mod containers, selected resource packs, Minecraft 26.2, Loader version, and exporter version. Preserve the six-millisecond capture budget and writer queue capacity two.

- [ ] **Step 5: Verify full automated Fabric GREEN**

Run:

```powershell
./gradlew.bat :common:test :fabric-26.2:test :fabric-26.2:build --no-configuration-cache
```

Expected: all Fabric automated tests pass and the remapped JAR is generated.

- [ ] **Step 6: Commit the complete Fabric pipeline**

```powershell
git add fabric-26.2 common
git commit -m "feat(fabric): complete 26.2 scene export pipeline"
```

---

### Task 12: Add server smoke runs, packaging checks, documentation, and final verification

**Files:**
- Modify: both platform `build.gradle` files
- Create: `fabric-26.2/src/test/java/com/nebysse/minetomesh/fabric/FabricJarContractTest.java`
- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`
- Create: `docs/releases/1.2.0-fabric-alpha.1.md`

- [ ] **Step 1: Add platform smoke tasks**

Keep NeoForge `runServerSmoke`. Configure Loom’s server run and add a bounded `fabricServerSmoke` JavaExec/task wrapper that starts the dedicated server with `--nogui`, accepts the EULA in its isolated run directory, and treats successful MineToMesh initialization followed by controlled timeout/shutdown as success.

- [ ] **Step 2: Add final JAR contract tests**

Assert the Fabric JAR contains:

```text
fabric.mod.json
com/nebysse/minetomesh/fabric/MineToMeshFabric.class
com/nebysse/minetomesh/fabric/client/MineToMeshFabricClient.class
com/nebysse/minetomesh/gltf/GltfDocumentBuilder.class
com/nebysse/minetomesh/usd/StreamingUsdaSession.class
assets/minetomesh/lang/zh_cn.json
assets/minetomesh/textures/item/export_wand.png
```

Also scan dedicated-server entrypoint dependencies for forbidden client references.

- [ ] **Step 3: Update user documentation**

Document the platform matrix, exact JAR names, Java requirements, separate build commands, full root build, Fabric alpha status, server/client installation requirement, and third-party compatibility caveat. Keep the existing NeoForge instructions intact rather than replacing them.

- [ ] **Step 4: Run the complete verification suite**

Run in this order:

```powershell
./gradlew.bat clean :common:test --no-configuration-cache
./gradlew.bat :neoforge-1.21.1:test :neoforge-1.21.1:build --no-configuration-cache
./gradlew.bat :fabric-26.2:test :fabric-26.2:build --no-configuration-cache
./gradlew.bat :neoforge-1.21.1:runServerSmoke --no-configuration-cache
./gradlew.bat :fabric-26.2:fabricServerSmoke --no-configuration-cache
./gradlew.bat build --no-configuration-cache
```

Expected: every command succeeds and both final JARs exist with the approved names.

- [ ] **Step 5: Perform the available client acceptance**

Launch Fabric 26.2 client when GUI execution is available. Verify wand interactions, GUI, Chinese input, lock/Overlay, original blocks, fluids, block entities, entities, cancellation, and simultaneous glTF/USDA output. Run the project validator against the produced glTF. Record every unexecuted manual item explicitly; do not convert an unperformed check into a pass.

- [ ] **Step 6: Inspect repository cleanliness**

Run:

```powershell
git status --short
git diff --check
git log --oneline --decorate -12
```

Expected: only intentional task changes exist in the implementation worktree; no unrelated main-worktree files appear.

- [ ] **Step 7: Commit final verification/docs**

```powershell
git add README.md docs common neoforge-1.21.1 fabric-26.2 build.gradle gradle.properties settings.gradle
git commit -m "docs: finalize Fabric 26.2 alpha verification"
```

---

## Plan self-review result

- Every approved design requirement maps to Tasks 2–12.
- The NeoForge build remains independently testable and keeps version `1.2.0`.
- Fabric uses Java 25 and version `1.2.0-fabric-alpha.1`.
- Common Java 21 output is embedded in both platform JARs.
- Platform APIs cannot leak into `common` without failing `CommonPlatformIsolationTest`.
- Automated build/smoke success and manual client behavior are reported separately.
- The implementation worktree starts from the design commit, isolating the user’s unrelated current-worktree changes.
