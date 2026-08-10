# MineToMesh 0.3.0 Export Workstation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Session constraint:** The user has explicitly disabled subagents. Execute this plan inline with `superpowers:executing-plans`; do not dispatch implementation or review work.

**Goal:** Release MineToMesh 0.3.0 with a server-authoritative, persistent export-workstation block, a Blender-colored Minecraft GUI, client-side selection visualization, truthful export progress, and the existing glTF/OBJ pipeline behind a validated menu workflow.

**Architecture:** NeoForge common-side registries provide the block, block entity and menu; explicit payloads mutate server-owned coordinates and grant immutable export snapshots. Client-only screen, overlay and controller classes consume those snapshots and reuse `DefaultExportPipeline` and `ExportJobManager`, while dedicated servers never load rendering or capture classes.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.244, JUnit 5, Gson, glTF 2.0, OBJ/MTL, PNG sprite atlases.

---

## File Structure

### Common-side production files

- `src/main/java/com/onecuber/mcgltf/McGltf.java` — 0.3.0 identity and common registration entry point.
- `src/main/java/com/onecuber/mcgltf/content/McGltfContent.java` — deferred registers for block, item, block entity, menu and creative tab.
- `src/main/java/com/onecuber/mcgltf/workstation/Endpoint.java` — FIRST/SECOND endpoint enum.
- `src/main/java/com/onecuber/mcgltf/workstation/Axis.java` — X/Y/Z axis enum.
- `src/main/java/com/onecuber/mcgltf/workstation/WorkstationCoordinates.java` — immutable two-point state and normalized `Selection` conversion.
- `src/main/java/com/onecuber/mcgltf/workstation/WorkstationCoordinatesCodec.java` — defensive NBT serialization.
- `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlock.java` — horizontal placement, block entity creation and server menu opening.
- `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlockEntity.java` — persisted coordinates, container data and update tags.
- `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationMenu.java` — slotless menu, position identity and distance validation.
- `src/main/java/com/onecuber/mcgltf/network/UpdateCoordinatePayload.java` — C2S one-axis edit.
- `src/main/java/com/onecuber/mcgltf/network/CaptureFeetPayload.java` — C2S server-derived endpoint capture.
- `src/main/java/com/onecuber/mcgltf/network/ExportRequestPayload.java` — C2S export request.
- `src/main/java/com/onecuber/mcgltf/network/ExportGrantedPayload.java` — S2C immutable selection grant.
- `src/main/java/com/onecuber/mcgltf/network/ExportRejectedPayload.java` — S2C localized rejection key.
- `src/main/java/com/onecuber/mcgltf/network/WorkstationRequestPolicy.java` — pure validation of menu identity, bounds and names.
- `src/main/java/com/onecuber/mcgltf/network/WorkstationPayloads.java` — payload registration and common-side handlers.
- `src/main/java/com/onecuber/mcgltf/network/WorkstationClientReceiver.java` — server-safe callback bridge installed by client code.

### Client production files

- `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java` — owns client runtime and installs menu, payload and render hooks.
- `src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationExportController.java` — validates grants, starts/cancels jobs and exposes screen state.
- `src/main/java/com/onecuber/mcgltf/job/ExportTelemetry.java` — monotonic stage snapshots shared by capture and writer threads.
- `src/main/java/com/onecuber/mcgltf/job/ExportSummary.java` — immutable completed/failed summary.
- `src/main/java/com/onecuber/mcgltf/client/workstation/CoordinateEditorModel.java` — parse, commit and step behavior independent of rendering.
- `src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java` — fixed 384×216 layout and widget lifecycle.
- `src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationTextures.java` — production sprite rectangles.
- `src/main/java/com/onecuber/mcgltf/client/workstation/OverlayKey.java` — dimension plus workstation identity.
- `src/main/java/com/onecuber/mcgltf/client/workstation/SelectionOverlayState.java` — per-session visible overlays.
- `src/main/java/com/onecuber/mcgltf/client/workstation/SelectionOverlayRenderer.java` — depth-tested six-face/twelve-edge rendering.

### Modified export files

- `src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java` — telemetry checkpoints and summary counters.
- `src/main/java/com/onecuber/mcgltf/job/ExportJob.java` — capture progress publication and summary result.
- `src/main/java/com/onecuber/mcgltf/job/ExportProgress.java` — stage percent and label fields.
- `src/main/java/com/onecuber/mcgltf/job/ManagedJob.java` — optional summary contract.

### Resources

- `src/main/resources/assets/mcgltf/blockstates/export_workstation.json`
- `src/main/resources/assets/mcgltf/models/block/export_workstation.json`
- `src/main/resources/assets/mcgltf/models/item/export_workstation.json`
- `src/main/resources/assets/mcgltf/textures/block/export_workstation_{front,side,back,top,bottom}.png`
- `src/main/resources/assets/mcgltf/textures/gui/export_workstation.png`
- `src/main/resources/assets/mcgltf/lang/en_us.json`
- `src/main/resources/assets/mcgltf/lang/zh_cn.json`
- `src/main/resources/data/mcgltf/recipe/export_workstation.json`
- `src/main/resources/data/mcgltf/loot_table/blocks/export_workstation.json`
- `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json`
- `src/main/templates/META-INF/neoforge.mods.toml`
- `README.md`
- `docs/testing/manual-client-matrix.md`

---

### Task 1: Establish the 0.3.0 Dual-Side Baseline

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/java/com/onecuber/mcgltf/McGltf.java`
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`
- Modify: `src/test/java/com/onecuber/mcgltf/McGltfMetadataTest.java`

- [ ] **Step 1: Write the failing metadata tests**

Replace the identity expectations with:

```java
@Test
void exposesMineToMeshReleaseIdentity() {
    assertEquals("mcgltf", McGltf.MOD_ID);
    assertEquals("MineToMesh", McGltf.DISPLAY_NAME);
    assertEquals("0.3.0", McGltf.VERSION);
}

@Test
void metadataRequiresBothSides() throws Exception {
    String metadata;
    try (var input = McGltfMetadataTest.class.getResourceAsStream(
            "/META-INF/neoforge.mods.toml")) {
        metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue(metadata.contains("version=\"0.3.0\""));
    assertFalse(metadata.contains("side=\"CLIENT\""));
    assertTrue(metadata.contains("Client and server export workstation"));
}
```

- [ ] **Step 2: Run the focused test and verify red**

Run:

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.McGltfMetadataTest
```

Expected: failures still report `MC glTF Exporter`, `0.2.0`, and client-only metadata.

- [ ] **Step 3: Apply the release identity**

Set:

```properties
mod_version=0.3.0
mod_name=MineToMesh
```

Set constants:

```java
public static final String DISPLAY_NAME = "MineToMesh";
public static final String VERSION = "0.3.0";
```

Change both dependency entries in `neoforge.mods.toml` from `side="CLIENT"` to `side="BOTH"`, and set the description to `Client and server export workstation with client-side Blender-ready glTF and OBJ capture.`

- [ ] **Step 4: Run the focused test**

Expected: `McGltfMetadataTest` passes.

- [ ] **Step 5: Commit**

```powershell
git add gradle.properties src/main/java/com/onecuber/mcgltf/McGltf.java src/main/templates/META-INF/neoforge.mods.toml src/test/java/com/onecuber/mcgltf/McGltfMetadataTest.java
git commit -m "build: begin MineToMesh 0.3.0"
```

### Task 2: Add the Immutable Workstation Coordinate Model

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/workstation/Endpoint.java`
- Create: `src/main/java/com/onecuber/mcgltf/workstation/Axis.java`
- Create: `src/main/java/com/onecuber/mcgltf/workstation/WorkstationCoordinates.java`
- Test: `src/test/java/com/onecuber/mcgltf/workstation/WorkstationCoordinatesTest.java`

- [ ] **Step 1: Write failing value-object tests**

Cover initialization, one-axis replacement, normalized conversion and inclusive volume:

```java
@Test
void updatesOneEndpointAxisWithoutMutatingOtherValues() {
    WorkstationCoordinates source = new WorkstationCoordinates(
            new BlockPos(1, 2, 3), new BlockPos(4, 5, 6));
    WorkstationCoordinates changed = source.with(Endpoint.SECOND, Axis.Y, 99);
    assertEquals(new BlockPos(1, 2, 3), changed.first());
    assertEquals(new BlockPos(4, 99, 6), changed.second());
}

@Test
void createsNormalizedInclusiveSelection() {
    Selection selection = new WorkstationCoordinates(
            new BlockPos(12, 82, 146), new BlockPos(-24, 64, 108))
            .toSelection("minecraft:overworld");
    assertEquals(37L, selection.sizeX());
    assertEquals(19L, selection.sizeY());
    assertEquals(39L, selection.sizeZ());
    assertEquals(27_417L, selection.volume());
}

@Test
void doesNotImposeAWorkstationVolumeLimit() {
    Selection selection = new WorkstationCoordinates(
            new BlockPos(0, -64, 0), new BlockPos(511, 319, 511))
            .toSelection("minecraft:overworld");
    assertEquals(100_663_296L, selection.volume());
}
```

- [ ] **Step 2: Run and verify missing-type failures**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.workstation.WorkstationCoordinatesTest
```

- [ ] **Step 3: Implement the records and enums**

Use this public contract:

```java
public enum Endpoint { FIRST, SECOND }
public enum Axis { X, Y, Z }

public record WorkstationCoordinates(BlockPos first, BlockPos second) {
    public WorkstationCoordinates {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
    }

    public static WorkstationCoordinates at(BlockPos position) {
        return new WorkstationCoordinates(position.immutable(), position.immutable());
    }

    public WorkstationCoordinates with(Endpoint endpoint, Axis axis, int value) {
        BlockPos source = endpoint == Endpoint.FIRST ? first : second;
        BlockPos changed = switch (axis) {
            case X -> new BlockPos(value, source.getY(), source.getZ());
            case Y -> new BlockPos(source.getX(), value, source.getZ());
            case Z -> new BlockPos(source.getX(), source.getY(), value);
        };
        return endpoint == Endpoint.FIRST
                ? new WorkstationCoordinates(changed, second)
                : new WorkstationCoordinates(first, changed);
    }

    public Selection toSelection(String dimension) {
        return Selection.of(point(dimension, first), point(dimension, second));
    }
}
```

The private `point` method copies x/y/z into `BlockPoint`.

- [ ] **Step 4: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.workstation.WorkstationCoordinatesTest
git add src/main/java/com/onecuber/mcgltf/workstation src/test/java/com/onecuber/mcgltf/workstation/WorkstationCoordinatesTest.java
git commit -m "feat: add workstation coordinate model"
```

### Task 3: Register and Persist the Workstation Block

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/content/McGltfContent.java`
- Create: `src/main/java/com/onecuber/mcgltf/workstation/WorkstationCoordinatesCodec.java`
- Create: `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlock.java`
- Create: `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlockEntity.java`
- Modify: `src/main/java/com/onecuber/mcgltf/McGltf.java`
- Test: `src/test/java/com/onecuber/mcgltf/workstation/WorkstationCoordinatesCodecTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/content/McGltfContentTest.java`

- [ ] **Step 1: Write failing codec tests**

Assert `First` and `Second` int arrays round-trip, and missing/wrong-length arrays return `WorkstationCoordinates.at(fallback)`.

```java
CompoundTag tag = new CompoundTag();
WorkstationCoordinatesCodec.save(tag, coordinates);
assertEquals(coordinates, WorkstationCoordinatesCodec.load(tag, fallback));
```

- [ ] **Step 2: Run focused tests and verify red**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.workstation.WorkstationCoordinatesCodecTest" --tests "com.onecuber.mcgltf.content.McGltfContentTest"
```

- [ ] **Step 3: Implement defensive NBT and registration**

`WorkstationCoordinatesCodec` must use exactly:

```java
public static void save(CompoundTag tag, WorkstationCoordinates value) {
    tag.putIntArray("First", coordinates(value.first()));
    tag.putIntArray("Second", coordinates(value.second()));
}

public static WorkstationCoordinates load(CompoundTag tag, BlockPos fallback) {
    int[] first = tag.getIntArray("First");
    int[] second = tag.getIntArray("Second");
    return first.length == 3 && second.length == 3
            ? new WorkstationCoordinates(pos(first), pos(second))
            : WorkstationCoordinates.at(fallback);
}
```

`McGltfContent` registers Block, BlockItem, BlockEntityType and CreativeModeTab. `ExportWorkstationBlockEntity` overrides 1.21.1 `loadAdditional` and `saveAdditional` with `HolderLookup.Provider`, exposes `coordinates()`, and performs `setChanged()` plus `sendBlockUpdated` in `setCoordinates`.

`McGltf` receives `IEventBus` and calls `McGltfContent.register(modBus)`.

- [ ] **Step 4: Run focused and full registration tests**

Expected: codec and content-holder tests pass without starting a client.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/McGltf.java src/main/java/com/onecuber/mcgltf/content src/main/java/com/onecuber/mcgltf/workstation src/test/java/com/onecuber/mcgltf/content src/test/java/com/onecuber/mcgltf/workstation
git commit -m "feat: register persistent export workstation"
```

### Task 4: Add the Slotless Menu and Block Interaction

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationMenu.java`
- Modify: `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlock.java`
- Modify: `src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlockEntity.java`
- Modify: `src/main/java/com/onecuber/mcgltf/content/McGltfContent.java`
- Test: `src/test/java/com/onecuber/mcgltf/workstation/ExportWorkstationMenuPolicyTest.java`

- [ ] **Step 1: Write failing menu-policy tests**

Extract a pure helper and prove valid station, wrong block and excessive distance outcomes:

```java
assertTrue(ExportWorkstationMenu.isValidStation(
        playerPosition, stationPosition, true));
assertFalse(ExportWorkstationMenu.isValidStation(
        new Vec3(100, 64, 100), stationPosition, true));
assertFalse(ExportWorkstationMenu.isValidStation(
        playerPosition, stationPosition, false));
```

- [ ] **Step 2: Run red, then implement menu registration**

Register `MenuType<ExportWorkstationMenu>` with `IMenuTypeExtension.create`.

The menu contract is:

```java
public final class ExportWorkstationMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 6;
    private final BlockPos stationPos;
    private final ContainerLevelAccess access;

    public WorkstationCoordinates coordinates();
    public BlockPos stationPos();
    @Override public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
```

Client constructor reads `stationPos` from `RegistryFriendlyByteBuf` and uses `SimpleContainerData(6)`. Server constructor binds six `ContainerData` entries to the block entity.

- [ ] **Step 3: Open the menu only on the logical server**

`ExportWorkstationBlock#useWithoutItem` calls `ServerPlayer#openMenu(blockEntity, buffer -> buffer.writeBlockPos(pos))` and returns `InteractionResult.sidedSuccess(level.isClientSide)`.

The block entity implements `MenuProvider` and creates `ExportWorkstationMenu`.

- [ ] **Step 4: Run tests and compile all source sets**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.workstation.ExportWorkstationMenuPolicyTest compileTestmodJava
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/content src/main/java/com/onecuber/mcgltf/workstation src/test/java/com/onecuber/mcgltf/workstation/ExportWorkstationMenuPolicyTest.java
git commit -m "feat: open workstation menu from block"
```

### Task 5: Define and Validate the Network Protocol

**Files:**
- Create: all five payload files listed in File Structure
- Create: `src/main/java/com/onecuber/mcgltf/network/WorkstationRequestPolicy.java`
- Create: `src/main/java/com/onecuber/mcgltf/network/WorkstationClientReceiver.java`
- Create: `src/main/java/com/onecuber/mcgltf/network/WorkstationPayloads.java`
- Modify: `src/main/java/com/onecuber/mcgltf/McGltf.java`
- Test: `src/test/java/com/onecuber/mcgltf/network/WorkstationPayloadCodecTest.java`
- Test: `src/test/java/com/onecuber/mcgltf/network/WorkstationRequestPolicyTest.java`

- [ ] **Step 1: Write payload round-trip and policy tests**

Round-trip representative negative and positive coordinates through each `STREAM_CODEC`. Test rejection keys for wrong menu position, missing station, out-of-range Y, unsafe export name and valid request.

Use this decision record:

```java
public record Validation(boolean accepted, String reasonKey) {
    public static Validation accept() { return new Validation(true, ""); }
    public static Validation reject(String key) { return new Validation(false, key); }
}
```

- [ ] **Step 2: Run and verify red**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.network.*"
```

- [ ] **Step 3: Implement exact payload contracts**

Each payload implements `CustomPacketPayload`, defines a namespaced `TYPE`, and uses `StreamCodec.composite`. Endpoint and Axis encode as bounded `VAR_INT` ordinals; BlockPos uses `BlockPos.STREAM_CODEC`; names and rejection keys use UTF-8 strings with the server-side 64-code-point check retained.

`WorkstationClientReceiver` contains no client imports:

```java
public final class WorkstationClientReceiver {
    private static Consumer<ExportGrantedPayload> granted = value -> { };
    private static Consumer<ExportRejectedPayload> rejected = value -> { };

    public static void install(
            Consumer<ExportGrantedPayload> grantedHandler,
            Consumer<ExportRejectedPayload> rejectedHandler) {
        granted = Objects.requireNonNull(grantedHandler);
        rejected = Objects.requireNonNull(rejectedHandler);
    }

    public static void receive(ExportGrantedPayload payload) {
        granted.accept(payload);
    }

    public static void receive(ExportRejectedPayload payload) {
        rejected.accept(payload);
    }

    public static void reset() {
        granted = value -> { };
        rejected = value -> { };
    }

    private WorkstationClientReceiver() { }
}
```

- [ ] **Step 4: Implement server handlers**

`WorkstationPayloads.register` uses protocol version `1` and registers three play-to-server and two play-to-client payloads. Server handlers require the current `ExportWorkstationMenu`, run `WorkstationRequestPolicy`, mutate only the matching block entity, and send grant/reject with `PacketDistributor.sendToPlayer`.

`CaptureFeetPayload` uses `serverPlayer.blockPosition().below()` for the foot-supporting block, then clamps Y only through normal world-bounds validation.

- [ ] **Step 5: Run network tests and dedicated compile**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.network.*" compileJava
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/McGltf.java src/main/java/com/onecuber/mcgltf/network src/test/java/com/onecuber/mcgltf/network
git commit -m "feat: add workstation network protocol"
```

### Task 6: Publish Truthful Export Telemetry and Summaries

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/job/ExportTelemetry.java`
- Create: `src/main/java/com/onecuber/mcgltf/job/ExportSummary.java`
- Modify: `src/main/java/com/onecuber/mcgltf/job/ExportProgress.java`
- Modify: `src/main/java/com/onecuber/mcgltf/job/ManagedJob.java`
- Modify: `src/main/java/com/onecuber/mcgltf/job/ExportJob.java`
- Modify: `src/main/java/com/onecuber/mcgltf/job/DefaultExportPipeline.java`
- Test: `src/test/java/com/onecuber/mcgltf/job/ExportTelemetryTest.java`
- Modify: existing `ExportJobTest` and `DefaultExportPipelinePolicyTest`

- [ ] **Step 1: Write failing monotonic telemetry tests**

```java
ExportTelemetry telemetry = new ExportTelemetry();
telemetry.capture(5, 10, "section/5", 1);
telemetry.writerStage(ExportTelemetry.WriterStage.TEXTURES);
telemetry.capture(4, 10, "late", 0);
assertEquals(88, telemetry.snapshot().percent());
assertEquals("textures", telemetry.snapshot().stageKey());
```

Also assert completion is exactly 100 and percent never decreases.

- [ ] **Step 2: Run focused tests and verify red**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.job.ExportTelemetryTest
```

- [ ] **Step 3: Implement telemetry stages**

Use immutable snapshots and an `AtomicReference`. Fixed stage floors are:

```java
public enum WriterStage {
    DRAINING(80, "draining"),
    TEXTURES(88, "textures"),
    DOCUMENTS(93, "documents"),
    REPORT(97, "report"),
    COMMITTED(100, "committed");
}
```

Capture maps `completed / total` onto `0..80`. Update uses `max(previous.percent, candidate)`.

Extend `ExportProgress` with `int percent` and `String stageKey`; validate `0 <= percent <= 100` and a nonblank stage key. `ExportJob.progress()` copies those two values from the shared telemetry snapshot while preserving existing work-item, queue, elapsed and current-object fields.

Define the final summary contract exactly as:

```java
public record ExportSummary(
        String status,
        Optional<Path> outputDirectory,
        long nodeCount,
        long primitiveCount,
        long textureCount,
        long warningCount,
        Duration elapsed,
        Optional<String> failureReason) { }
```

Successful summaries require an output directory and empty failure reason; failed or cancelled summaries require a failure reason and may not expose an output directory.

- [ ] **Step 4: Instrument the existing pipeline**

Add `DefaultExportPipeline.create(Minecraft minecraft, Selection selection, ExportName name, ExportTelemetry telemetry)`; preserve the current three-argument overload by delegating with a new telemetry instance. Pass the same telemetry object to `ExportJob` and `AsyncBatchSink`. Update telemetry before `textures.writeAll`, before `session.finish`, before `ReportWriter.write`, and after `transaction.publish`.

Extend `WriterResult.success` with `nodeCount`, `primitiveCount` and `textureCount`, sourced from `StreamingSceneSession.OutputStatistics.gltf()` and `TextureRegistry.size()`. `ExportJob.consumeWriterResult()` combines those writer metrics with warning count, status, final directory and its own elapsed clock to construct `ExportSummary`. `fail` and `cancel` construct failure/cancelled summaries with zero unavailable metrics. `ManagedJob` exposes `default Optional<ExportSummary> summary()` and `ExportJob` overrides it.

- [ ] **Step 5: Run job and pipeline tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.job.*"
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/job src/test/java/com/onecuber/mcgltf/job
git commit -m "feat: expose export telemetry and summary"
```

### Task 7: Bridge Authorized Workstation Exports to the Existing Job Manager

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationExportController.java`
- Modify: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- Test: `src/test/java/com/onecuber/mcgltf/client/workstation/WorkstationExportControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Use fake `ManagedJob` and factory instances to prove: matching open station starts, closed screen rejects late grant, wrong dimension rejects, concurrent active job rejects, and `screenClosed()` cancels only the workstation-owned running job.

```java
controller.bind(station, "minecraft:overworld");
controller.requested("flower_factory");
assertTrue(controller.accept(grant));
controller.screenClosed();
assertEquals(JobState.CANCELLED, fakeJob.state());
```

- [ ] **Step 2: Run red and implement controller state**

Controller states are `READY`, `WAITING_FOR_GRANT`, `EXPORTING`, `COMPLETED`, `FAILED`, `CANCELLED`. It stores the bound station, dimension, telemetry and the exact `ManagedJob` it started. It receives dependencies through constructor interfaces so tests do not initialize `Minecraft`.

- [ ] **Step 3: Install the common receiver bridge on the client**

`McGltfClient` owns one controller, installs `WorkstationClientReceiver.install(controller::accept, controller::reject)`, ticks the existing job manager, and resets receiver/controller/overlays on logout, dimension change and resource reload.

- [ ] **Step 4: Run client controller and command regression tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.client.workstation.WorkstationExportControllerTest" --tests "com.onecuber.mcgltf.command.*"
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/client src/test/java/com/onecuber/mcgltf/client
git commit -m "feat: control client exports from workstation grants"
```

### Task 8: Implement Coordinate Editing as a Pure Model

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/client/workstation/CoordinateEditorModel.java`
- Test: `src/test/java/com/onecuber/mcgltf/client/workstation/CoordinateEditorModelTest.java`

- [ ] **Step 1: Write failing input and stepping tests**

Cover signed parsing, empty/overflow invalidity, button `±1`, wheel `±1`, Shift-wheel `±10`, and no outbound value for invalid text.

```java
model.setText("-24");
assertEquals(OptionalInt.of(-24), model.commit());
assertEquals(-14, model.step(1, true));
assertTrue(model.setText("999999999999").isInvalid());
```

- [ ] **Step 2: Run red, implement, then run green**

The model owns raw text, last server value and validity. `serverValue(int)` replaces text only when the field is not actively editing; `commit()` uses `Integer.parseInt`; `step(direction, shift)` uses `Math.addExact` and leaves the model invalid on overflow.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/client/workstation/CoordinateEditorModel.java src/test/java/com/onecuber/mcgltf/client/workstation/CoordinateEditorModelTest.java
git commit -m "feat: add workstation coordinate editor model"
```

### Task 9: Build the Fixed 384×216 Screen

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationTextures.java`
- Create: `src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java`
- Modify: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- Test: `src/test/java/com/onecuber/mcgltf/client/workstation/WorkstationLayoutTest.java`

- [ ] **Step 1: Write layout geometry tests**

Define immutable rectangles and assert exact design coordinates and non-overlap:

```java
assertEquals(new Rect(0, 0, 384, 20), Layout.HEADER);
assertEquals(new Rect(4, 24, 208, 166), Layout.LEFT);
assertEquals(new Rect(216, 24, 164, 166), Layout.RIGHT);
assertEquals(new Rect(4, 194, 376, 18), Layout.LOG);
assertFalse(Layout.LEFT.intersects(Layout.RIGHT));
```

- [ ] **Step 2: Run red and implement layout constants**

`ExportWorkstationScreen` extends `AbstractContainerScreen<ExportWorkstationMenu>`, sets `imageWidth=384`, `imageHeight=216`, hides inventory labels, and creates six EditBox-backed coordinate controls plus explicit step, capture, overlay, export and cancel buttons.

- [ ] **Step 3: Implement exact interaction routing**

- Enter and focus loss call a single `commit(endpoint, axis)` method.
- Step buttons send `UpdateCoordinatePayload` with `±1`.
- Mouse wheel over an input commits current text then sends `±1` or `±10` when Shift is down.
- Capture buttons send `CaptureFeetPayload`.
- Export parses `ExportName`, sends `ExportRequestPayload`, and marks controller waiting.
- `onClose()` calls controller `screenClosed()` before `super.onClose()`.
- Exporting disables coordinate and name widgets.
- Completed/failed state renders `ExportSummary`, not `report.json` contents.

- [ ] **Step 4: Register the screen client-side**

Use `RegisterMenuScreensEvent` on the mod bus and register `McGltfContent.EXPORT_WORKSTATION_MENU` with a factory that obtains the client runtime controller and overlay state from `McGltfClient`.

- [ ] **Step 5: Run layout and all client unit tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.client.workstation.*"
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/client src/test/java/com/onecuber/mcgltf/client
git commit -m "feat: add export workstation screen"
```

### Task 10: Add Persistent Depth-Tested Selection Rendering

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/client/workstation/OverlayKey.java`
- Create: `src/main/java/com/onecuber/mcgltf/client/workstation/SelectionOverlayState.java`
- Create: `src/main/java/com/onecuber/mcgltf/client/workstation/SelectionOverlayRenderer.java`
- Modify: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- Test: `src/test/java/com/onecuber/mcgltf/client/workstation/SelectionOverlayStateTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

Prove toggle persistence after Screen close, coordinate refresh, station removal, dimension change and logout clearing.

```java
state.toggle(key, coordinates);
state.screenClosed(key);
assertTrue(state.visible(key));
state.dimensionChanged(otherDimension);
assertFalse(state.visible(key));
```

- [ ] **Step 2: Implement overlay state and pass tests**

Store a `LinkedHashMap<OverlayKey, WorkstationCoordinates>`. No disk persistence and no network broadcast of visibility.

- [ ] **Step 3: Implement renderer geometry**

Subscribe to `RenderLevelStageEvent.AfterTranslucentBlocks`. For each visible current-dimension entry, confirm the client block entity still exists, transform normalized inclusive block bounds to an AABB ending at `max + 1`, and emit only six quads and twelve lines relative to camera position.

Use depth testing, orange face alpha, blue line color, no `NO_DEPTH_TEST` render state, and restore all RenderSystem state in `finally`.

- [ ] **Step 4: Register and run tests**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.client.workstation.SelectionOverlayStateTest compileJava
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/client src/test/java/com/onecuber/mcgltf/client/workstation/SelectionOverlayStateTest.java
git commit -m "feat: render persistent workstation selections"
```

### Task 11: Produce Deterministic GUI and Block Assets

**Files:**
- Create all resource files listed under Resources
- Reference only: `docs/superpowers/design-assets/minetomesh-0.3.0/*`
- Test: `src/test/java/com/onecuber/mcgltf/content/WorkstationResourceTest.java`
- Create: `tools/process-workstation-assets.py`
- Create: `tools/workstation-asset-crops.json`
- Create: `tools/requirements-assets.txt`

- [ ] **Step 1: Write failing resource integrity tests**

Assert all five block textures are exactly 16×16, GUI atlas has alpha, no production PNG contains exact green-key pixels, blockstate exposes north/east/south/west variants, recipe ingredients match the approved pattern, and loot table drops the workstation.

- [ ] **Step 2: Run red**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.WorkstationResourceTest
```

- [ ] **Step 3: Implement deterministic green-key processing**

Pin the processing dependency in `tools/requirements-assets.txt` as `Pillow==11.3.0`. `tools/workstation-asset-crops.json` stores named integer rectangles as `{ "sprite_name": [x, y, width, height] }` for the approved green-key sources.

`tools/process-workstation-assets.py` must:

```python
GREEN = (0, 255, 0)

def key_to_alpha(image):
    rgba = image.convert("RGBA")
    pixels = []
    for red, green, blue, alpha in rgba.getdata():
        pixels.append((0, 0, 0, 0) if (red, green, blue) == GREEN
                      else (red, green, blue, 255))
    rgba.putdata(pixels)
    return rgba
```

The script takes explicit crop rectangles stored in a JSON manifest, rejects crops containing disconnected foreign components, and writes with nearest-neighbor sampling. It must fail if the source background contains near-green pixels outside exact `#00FF00`, because those require manual cleanup.

- [ ] **Step 4: Clean and redraw production assets**

Use the approved images as visual references. Manually reduce block faces to true 16×16 logical pixels; do not downsample text or labels. Build a compact transparent GUI atlas containing only used states. Record every GUI sprite rectangle in `WorkstationTextures` and the crop manifest.

- [ ] **Step 5: Add models, recipe, loot, tags and language**

Recipe pattern is `IGI/RCR/III` with iron ingot, glass pane, redstone and cartography table. The block model maps front/side/back/top/bottom according to horizontal facing. Add complete English and Simplified Chinese keys for block name, menu labels, errors, stages and summary fields.

- [ ] **Step 6: Run resource tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.WorkstationResourceTest
git add src/main/resources src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationTextures.java src/test/java/com/onecuber/mcgltf/content/WorkstationResourceTest.java tools/process-workstation-assets.py tools/workstation-asset-crops.json tools/requirements-assets.txt
git commit -m "feat: add workstation visual assets"
```

### Task 12: Add Common-Side and Dedicated-Server Safety Tests

**Files:**
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/WorkstationGameTests.java`
- Modify: `src/testmod/java/com/onecuber/mcgltf/testmod/McGltfTestMod.java`
- Create: `src/test/java/com/onecuber/mcgltf/ServerClassIsolationTest.java`
- Modify: `build.gradle`

- [ ] **Step 1: Write failing registration and persistence game tests**

Game tests place each facing, assert a block entity exists, mutate coordinates, save/reload, and assert the workstation drops itself when broken with the correct tool.

- [ ] **Step 2: Write server isolation test**

Scan common entry-point bytecode with `jdeps` or constant-pool inspection and fail if `McGltf`, content, workstation or network classes reference `net.minecraft.client`, `com.mojang.blaze3d`, or `com.onecuber.mcgltf.client`.

- [ ] **Step 3: Add a noninteractive server smoke task**

Configure a NeoForge run named `serverSmoke` that starts with `--nogui`, loads MineToMesh 0.3.0, writes a known ready marker, and exits through a generated `stop` command. Configure the generated `runServerSmoke` task with a 90-second timeout and treat client-class linkage as failure.

- [ ] **Step 4: Run all safety checks**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.ServerClassIsolationTest runGameTestServer runServerSmoke
```

Expected: all game tests pass and no client classes load on server.

- [ ] **Step 5: Commit**

```powershell
git add build.gradle src/testmod src/test/java/com/onecuber/mcgltf/ServerClassIsolationTest.java
git commit -m "test: verify workstation server safety"
```

### Task 13: Update Documentation and Manual Acceptance Matrix

**Files:**
- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`
- Test: `src/test/java/com/onecuber/mcgltf/DocumentationPolicyTest.java`

- [ ] **Step 1: Write failing documentation assertions**

Require README to contain `mcgltf-0.3.0.jar`, `客户端和服务端`, `区域导出工作台`, `关闭 GUI 会取消`, the recipe ingredients, and the retained `/mcgltf` command.

- [ ] **Step 2: Update README**

Document dual-side installation, creative acquisition, recipe, coordinate editing, overlay behavior, export lifecycle, summary/report distinction, no volume limit, and command fallback.

- [ ] **Step 3: Expand manual matrix**

Add four-facing texture checks, GUI Scale 2/3/4, two-player shared edits, depth occlusion, block removal, close-to-cancel, dedicated server, Create/Touhou Little Maid regression and Blender alpha-node regression.

- [ ] **Step 4: Run documentation tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.DocumentationPolicyTest
git add README.md docs/testing/manual-client-matrix.md src/test/java/com/onecuber/mcgltf/DocumentationPolicyTest.java
git commit -m "docs: document MineToMesh 0.3.0 workstation"
```

### Task 14: Final Verification, Packaging and Push

**Files:**
- Modify only files required by verification findings.
- Deliver: `build/libs/mcgltf-0.3.0.jar`

- [ ] **Step 1: Run focused workstation tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.workstation.*" --tests "com.onecuber.mcgltf.network.*" --tests "com.onecuber.mcgltf.client.workstation.*" --tests "com.onecuber.mcgltf.content.*"
```

Expected: zero failures, errors and skipped tests.

- [ ] **Step 2: Run clean full build**

```powershell
.\gradlew.bat clean test build
```

Expected: `BUILD SUCCESSFUL` and every pre-0.3.0 regression test remains green.

- [ ] **Step 3: Verify production JAR isolation and resources**

```powershell
jar tf build\libs\mcgltf-0.3.0.jar | Select-String "mcgltf_test|docs/superpowers|\.superpowers|gui-greenkey|workstation-block-greenkey"
```

Expected: no matches.

Then require matches for common workstation classes, client screen classes, five 16×16 block textures, GUI atlas, recipe, loot table and both language files.

- [ ] **Step 4: Run static checks**

```powershell
git diff --check
jdeps -q build\libs\mcgltf-0.3.0.jar | Select-String "Create|flywheel"
```

Expected: no whitespace errors and no hard Create/Flywheel bytecode dependency.

- [ ] **Step 5: Run development client and manual matrix**

```powershell
.\gradlew.bat runClient
```

The user performs real-world GUI, two-player, Create, maid and Blender checks. Record only observed results; do not mark unperformed rows as passed.

- [ ] **Step 6: Commit verification evidence**

```powershell
git add docs/testing/manual-client-matrix.md
git commit -m "test: verify MineToMesh 0.3.0 workstation"
```

- [ ] **Step 7: Push and deliver**

Push the complete commit range to `MineToMesh/main`, verify `git ls-remote` equals local HEAD, and stage `build/libs/mcgltf-0.3.0.jar` for the user.
