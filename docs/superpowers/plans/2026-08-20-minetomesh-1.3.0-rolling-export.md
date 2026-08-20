# MineToMesh 1.3.0 Rolling Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. For this repository, use `executing-plans` because the project owner requires the main Agent to work independently without subagents.

**Goal:** Ship MineToMesh 1.3.0 with server-coordinated rolling chunk loading, global random-tick freezing, cancellable export sessions, configurable bounded CPU workers, and accurate full-lifecycle GUI progress on NeoForge 1.21.1 and Fabric 26.2.

**Architecture:** A loader-free common state machine owns lazy chunk batching, progress arithmetic, cancellation, worker ordering, and recovery-file semantics. Each platform supplies server tickets, game-rule access, tracking-center redirection, lifecycle events, and payload registration. Client rendering remains on the Minecraft Render Thread under the existing 6 ms tick budget; immutable batches move through a bounded worker pool and a deterministic single writer.

**Tech Stack:** Java 21 common/NeoForge, Java 25 Fabric, Gradle 9.5, NeoForge 21.1.244, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, JUnit 5, Gson, Sponge Mixin, glTF 2.0, OpenUSD USDA.

---

## File Structure

### Common files to create

- `common/src/main/java/com/nebysse/minetomesh/world/ChunkCoordinate.java` — loader-free horizontal chunk coordinate.
- `common/src/main/java/com/nebysse/minetomesh/world/ChunkRange.java` — normalized lazy horizontal range and checked totals.
- `common/src/main/java/com/nebysse/minetomesh/world/ChunkBatchCursor.java` — deterministic compact `4×4` macro-window cursor.
- `common/src/main/java/com/nebysse/minetomesh/job/ExportExecutionPolicy.java` — batch/CPU worker validation and effective worker count.
- `common/src/main/java/com/nebysse/minetomesh/job/ExportStage.java` — stable full-lifecycle stages and percentage bands.
- `common/src/main/java/com/nebysse/minetomesh/job/ExportProgressSnapshot.java` — one immutable snapshot for GUI, command, and logs.
- `common/src/main/java/com/nebysse/minetomesh/job/RawPrimitiveStream.java` — immutable material/mode/vertex stream with a coplanar-layer group.
- `common/src/main/java/com/nebysse/minetomesh/job/RawCapturedObject.java` — immutable object containing raw streams and node metadata.
- `common/src/main/java/com/nebysse/minetomesh/job/RawChunkBatch.java` — immutable chunk data detached from Minecraft objects.
- `common/src/main/java/com/nebysse/minetomesh/job/OrderedBatchExecutor.java` — bounded worker submission and sequence-ordered completion.
- `common/src/main/java/com/nebysse/minetomesh/session/ExportSessionState.java` — server session states.
- `common/src/main/java/com/nebysse/minetomesh/session/ServerExportSession.java` — pure session data and legal transitions.
- `common/src/main/java/com/nebysse/minetomesh/session/ServerExportSessionCoordinator.java` — global lock, timeouts, batch protocol, and cleanup orchestration.
- `common/src/main/java/com/nebysse/minetomesh/session/RandomTickRecoveryStore.java` — atomic JSON crash-recovery journal.

### Common files to modify

- `common/src/main/java/com/nebysse/minetomesh/job/ExportJob.java`
- `common/src/main/java/com/nebysse/minetomesh/job/ExportTelemetry.java`
- `common/src/main/java/com/nebysse/minetomesh/job/ExportProgress.java`
- `common/src/main/java/com/nebysse/minetomesh/job/StreamingBatchSink.java`
- `common/src/main/java/com/nebysse/minetomesh/report/ExportReport.java`
- `common/src/main/java/com/nebysse/minetomesh/world/ExportPlan.java`
- `common/src/main/java/com/nebysse/minetomesh/MineToMeshInfo.java`

### Platform files to create in both modules

Use the same package and class names under each platform source root:

- `network/ExportSessionAcceptedPayload.java`
- `network/ExportSessionRejectedPayload.java`
- `network/BatchLoadStartedPayload.java`
- `network/BatchReadyPayload.java`
- `network/BatchClientReadablePayload.java`
- `network/BatchCaptureCompletedPayload.java`
- `network/ExportProgressHeartbeatPayload.java`
- `network/CancelExportRequestPayload.java`
- `network/ExportCancelAcknowledgedPayload.java`
- `network/ExportClientCompletedPayload.java`
- `network/ExportSessionFinishedPayload.java`
- `network/ExportSessionFailedPayload.java`
- `server/PlatformExportRuntime.java`
- `server/ServerExportSessions.java`
- `mixin/ChunkMapTrackingCenterMixin.java`

### Platform files to modify in both modules

- `content/MineToMeshContent.java`
- `wand/ExportWandSelection.java`
- `wand/ExportWandService.java`
- `network/ExportWandRequestPayload.java`
- `network/WandPayloads.java`
- `network/WandClientReceiver.java`
- `client/MineToMeshClient.java`
- `client/wand/ExportWandController.java`
- `client/wand/ExportWandScreen.java`
- `job/DefaultExportPipeline.java`
- `world/WorldPlanner.java`
- language JSON resources
- platform metadata and Mixin configuration

Fabric-only client configuration:

- `fabric-26.2/src/client/java/com/nebysse/minetomesh/client/config/ClientExportSettings.java`
- `fabric-26.2/src/client/java/com/nebysse/minetomesh/client/config/ClientExportSettingsStore.java`

NeoForge uses the same two classes under `neoforge-1.21.1/src/main/java/.../client/config/`.

---

### Task 1: Raise the 1.3.0 version contract

**Files:**
- Modify: `gradle.properties:20-21`
- Modify: `common/src/main/java/com/nebysse/minetomesh/MineToMeshInfo.java`
- Modify: `neoforge-1.21.1/src/main/java/com/nebysse/minetomesh/MineToMesh.java`
- Modify: `fabric-26.2/src/main/java/com/nebysse/minetomesh/fabric/MineToMeshFabric.java`
- Modify: `fabric-26.2/src/test/java/com/nebysse/minetomesh/fabric/FabricJarContractTest.java`
- Test: `neoforge-1.21.1/src/test/java/com/nebysse/minetomesh/Version130ContractTest.java`
- Test: `fabric-26.2/src/test/java/com/nebysse/minetomesh/fabric/FabricMetadataTest.java`

- [ ] **Step 1: Write failing version and artifact-name tests**

Add assertions equivalent to:

```java
assertEquals("1.3.0", MineToMesh.VERSION);
assertEquals("1.3.0", MineToMeshInfo.CORE_VERSION);
assertEquals("1.3.0", property("neoforge_mod_version"));
assertEquals("1.3.0-fabric-alpha.1", property("fabric_mod_version"));
assertTrue(buildScript.contains("MineToMesh-${version}-neoforge-1.21.1.jar"));
```

Update the Fabric JAR regex to:

```java
"MineToMesh-1\\.3\\.0-fabric-alpha\\.1\\+mc26\\.2\\.jar"
```

- [ ] **Step 2: Run the tests and observe the expected red state**

Run:

```powershell
./gradlew.bat :neoforge-1.21.1:test --tests "*Version130ContractTest" `
  :fabric-26.2:test --tests "*FabricMetadataTest" --no-configuration-cache
```

Expected: failure showing the old `1.2.0` and `1.2.0-fabric-alpha.1` values.

- [ ] **Step 3: Update production versions**

Set:

```properties
neoforge_mod_version=1.3.0
fabric_mod_version=1.3.0-fabric-alpha.1
```

Set constants to the matching platform versions. Do not change Mod ID or Java packages.

- [ ] **Step 4: Re-run focused tests and build both JAR tasks**

```powershell
./gradlew.bat :neoforge-1.21.1:test :neoforge-1.21.1:jar `
  :fabric-26.2:test :fabric-26.2:jar --no-configuration-cache
```

Expected: PASS and both approved artifact names exist.

- [ ] **Step 5: Commit**

```powershell
git add gradle.properties common neoforge-1.21.1 fabric-26.2
git commit -m "build: begin MineToMesh 1.3.0"
```

---

### Task 2: Add lazy horizontal chunk batching

**Files:**
- Create: `common/src/main/java/com/nebysse/minetomesh/world/ChunkCoordinate.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/world/ChunkRange.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/world/ChunkBatchCursor.java`
- Test: `common/src/test/java/com/nebysse/minetomesh/world/ChunkBatchCursorTest.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/world/ExportPlan.java`

- [ ] **Step 1: Write failing cursor tests**

Cover normalized negative coordinates, `1/4/16` batch sizes, short edge batches, stable macro-window/X/Z order, checked totals, reset independence, and no prebuilt list. Assert every emitted batch fits inside one `4×4` bounding area, including batch size `16`. Example:

```java
@Test
void emitsAtMostFourChunksInStableOrder() {
    ChunkRange range = new ChunkRange(-1, 1, 2, 4);
    ChunkBatchCursor cursor = range.cursor();

    assertEquals(List.of(
            new ChunkCoordinate(-1, 2),
            new ChunkCoordinate(-1, 3),
            new ChunkCoordinate(-1, 4),
            new ChunkCoordinate(0, 2)), cursor.next(4));
    assertEquals(5, cursor.remaining());
    assertTrue(cursor.currentBatchBounds().width() <= 4);
    assertTrue(cursor.currentBatchBounds().depth() <= 4);
}

@Test
void selectionUsesFloorDivisionForNegativeBlocks() {
    Selection selection = Selection.of(
            new BlockPoint("minecraft:overworld", -17, 0, -1),
            new BlockPoint("minecraft:overworld", 16, 10, 16));
    assertEquals(new ChunkRange(-2, 1, -1, 1), ChunkRange.from(selection));
}
```

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :common:test --tests "*ChunkBatchCursorTest" --no-configuration-cache
```

Expected: compile failure because the three classes do not exist.

- [ ] **Step 3: Implement immutable range and cursor**

Use these public contracts:

```java
public record ChunkCoordinate(int x, int z) implements Comparable<ChunkCoordinate> {}

public record ChunkRange(int minX, int maxX, int minZ, int maxZ) {
    public static ChunkRange from(Selection selection);
    public long totalChunks();
    public long totalBatches(int batchSize);
    public ChunkBatchCursor cursor();
}

public final class ChunkBatchCursor {
    public List<ChunkCoordinate> next(int batchSize);
    public BatchBounds currentBatchBounds();
    public long emitted();
    public long remaining();
    public boolean exhausted();

    public record BatchBounds(
            int minX, int maxX, int minZ, int maxZ,
            ChunkCoordinate center) {
        public int width();
        public int depth();
    }
}
```

`next()` validates `1..16`, constructs only the returned list, and advances through `4×4` macro windows. It fully consumes one macro window before moving to the next, so no batch spans two macro windows. `currentBatchBounds()` returns the emitted batch bounds and center. `totalBatches(batchSize)` sums `ceil(windowChunkCount / batchSize)` for full 4×4 windows, X/Z edge windows, and the corner window; it must not use global `ceil(totalChunks / batchSize)`. Use `Math.addExact`, `Math.multiplyExact`, and `Math.floorDiv`.

Replace `ExportPlan`'s eager `sections` list with selection, `ChunkRange`, build-height section bounds, and final diagnostics. A platform `WorldPlanner` must plan only the currently authorized chunk list.

- [ ] **Step 4: Run common world tests**

```powershell
./gradlew.bat :common:test --tests "*ChunkBatchCursorTest" `
  --tests "*ExportPlanTest" --tests "*SelectionTest" --no-configuration-cache
```

Expected: PASS with no list proportional to the entire selection.

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/java/com/nebysse/minetomesh/world `
  common/src/test/java/com/nebysse/minetomesh/world
git commit -m "feat: plan exports with lazy chunk windows"
```

---

### Task 3: Add batch and worker policies plus persistence contracts

**Files:**
- Create: `common/src/main/java/com/nebysse/minetomesh/job/ExportExecutionPolicy.java`
- Test: `common/src/test/java/com/nebysse/minetomesh/job/ExportExecutionPolicyTest.java`
- Modify: both `wand/ExportWandSelection.java`
- Modify: both `wand/ExportWandService.java`
- Create: both `network/UpdateWandBatchSizePayload.java`
- Modify: both `network/WandPayloads.java`
- Test: both `wand/ExportWandSelectionTest.java`
- Test: both `network/WandPayloadCodecTest.java`

- [ ] **Step 1: Write failing policy and backward-compatibility tests**

```java
@Test
void reservesTwoCpuThreadsAndCapsAtSixteen() {
    assertEquals(1, ExportExecutionPolicy.maxWorkers(2));
    assertEquals(2, ExportExecutionPolicy.maxWorkers(4));
    assertEquals(6, ExportExecutionPolicy.maxWorkers(8));
    assertEquals(16, ExportExecutionPolicy.maxWorkers(64));
}

@Test
void effectiveWorkersCannotExceedBatch() {
    assertEquals(4, ExportExecutionPolicy.effectiveWorkers(14, 4, 32));
}
```

For each platform, decode legacy JSON without `batch_chunk_count` and assert `4`; reject values outside `1..16` from constructors and network handlers.

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :common:test --tests "*ExportExecutionPolicyTest" `
  :neoforge-1.21.1:test --tests "*ExportWandSelectionTest" `
  :fabric-26.2:test --tests "*ExportWandSelectionTest" --no-configuration-cache
```

Expected: missing policy and old seven-field wand record.

- [ ] **Step 3: Implement the common policy**

```java
public final class ExportExecutionPolicy {
    public static final int DEFAULT_BATCH_CHUNKS = 4;
    public static final int MIN_BATCH_CHUNKS = 1;
    public static final int MAX_BATCH_CHUNKS = 16;
    public static int validateBatchChunks(int value);
    public static int maxWorkers(int availableProcessors);
    public static int defaultWorkers(int availableProcessors);
    public static int clampWorkers(int requested, int availableProcessors);
    public static int effectiveWorkers(int requested, int batchChunks, int availableProcessors);
}
```

Use `max(1, min(16, processors - 2))` and `min(clampedWorkers, batchChunks)`.

- [ ] **Step 4: Extend wand data and payloads on both platforms**

Add `int batchChunkCount` to the record after `includePlayers`. Add:

```java
Codec.INT.optionalFieldOf("batch_chunk_count", 4)
```

Encode/decode the integer in the stream codec, validate in the compact constructor, and preserve it in every `with*` method. Add:

```java
public ExportWandSelection withBatchChunkCount(int value)
```

`UpdateWandBatchSizePayload(UUID wandId, int batchChunkCount)` mutates only the currently bound wand through `ExportWandService.setBatchChunkCount`. The server revalidates the value.

- [ ] **Step 5: Run both codec and mutation suites**

```powershell
./gradlew.bat :common:test --tests "*ExportExecutionPolicyTest" `
  :neoforge-1.21.1:test --tests "*ExportWandSelectionTest" --tests "*WandPayloadCodecTest" `
  :fabric-26.2:test --tests "*ExportWandSelectionTest" --tests "*WandPayloadCodecTest" `
  --no-configuration-cache
```

Expected: PASS including legacy default `4`.

- [ ] **Step 6: Commit**

```powershell
git add common neoforge-1.21.1 fabric-26.2
git commit -m "feat: persist rolling export batch size"
```

---

### Task 4: Replace coarse telemetry with composable full-lifecycle progress

**Files:**
- Create: `common/src/main/java/com/nebysse/minetomesh/job/ExportStage.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/job/ExportProgressSnapshot.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/job/ExportTelemetry.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/job/ExportProgress.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/command/CommandPolicy.java`
- Test: `common/src/test/java/com/nebysse/minetomesh/job/ExportTelemetryTest.java`
- Test: `common/src/test/java/com/nebysse/minetomesh/command/CommandPolicyTest.java`

- [ ] **Step 1: Write failing weighted-progress tests**

Test every band, overlapping counter updates, monotonicity, divide-by-zero, queue/thread fields, and the requirement that `100` requires published output plus restored server state:

```java
telemetry.initialize(100, 25, 1_000, 4, 6);
telemetry.serverPrepared();
telemetry.chunksSynchronized(50);
telemetry.positionsCaptured(500);
telemetry.chunksProcessed(25);
telemetry.batchesPersisted(12);
assertTrue(telemetry.snapshot().percent() < 100);
telemetry.finalizationStep(ExportTelemetry.FinalizationStep.PUBLISHED);
telemetry.finalizationStep(ExportTelemetry.FinalizationStep.SERVER_RESTORED);
telemetry.finalizationStep(ExportTelemetry.FinalizationStep.TRACKING_RESTORED);
assertEquals(100, telemetry.snapshot().percent());
```

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :common:test --tests "*ExportTelemetryTest" --no-configuration-cache
```

Expected: old telemetry exposes only `capture()` and writer floors.

- [ ] **Step 3: Implement stages and counters**

`ExportStage` uses stable translation keys and the agreed bands. `ExportTelemetry` declares nested enum `FinalizationStep { PUBLISHED, SERVER_RESTORED, TRACKING_RESTORED }`. `ExportProgressSnapshot` contains:

```java
ExportStage stage,
int percent,
long batchSequence,
long totalBatches,
long synchronizedChunks,
long totalChunks,
long capturedPositions,
long totalPositions,
long processedChunks,
long persistedBatches,
int configuredWorkers,
int effectiveWorkers,
int processingQueueDepth,
int writingQueueDepth,
String currentObjectId,
Duration elapsed
```

Keep a single `AtomicReference<State>` and update with immutable copies. Percentage is derived from independent ratios, clamped to `0..99` until all three finalization flags are true.

- [ ] **Step 4: Make commands consume the same snapshot**

Delete independent percentage arithmetic in `CommandPolicy`; format the snapshot's exact percent, stage translation key, chunk counters, worker usage, and queues.

- [ ] **Step 5: Run progress tests**

```powershell
./gradlew.bat :common:test --tests "*ExportTelemetryTest" `
  --tests "*CommandPolicyTest" --no-configuration-cache
```

Expected: PASS and no initial jump to 80%.

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/com/nebysse/minetomesh/job `
  common/src/main/java/com/nebysse/minetomesh/command `
  common/src/test/java/com/nebysse/minetomesh
git commit -m "feat: report complete export progress"
```

---

### Task 5: Add bounded deterministic CPU workers

**Files:**
- Create: `common/src/main/java/com/nebysse/minetomesh/job/RawPrimitiveStream.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/job/RawCapturedObject.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/job/RawChunkBatch.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/job/OrderedBatchExecutor.java`
- Test: `common/src/test/java/com/nebysse/minetomesh/job/OrderedBatchExecutorTest.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/job/ExportJob.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/job/StreamingBatchSink.java`

- [ ] **Step 1: Write failing concurrency tests with controlled completion order**

Use latches so sequence `2` finishes before `0` and `1`, then assert delivery remains `0,1,2`. Also test queue backpressure, cancellation, worker shutdown, task failure propagation, and effective worker count.

```java
OrderedBatchExecutor executor = new OrderedBatchExecutor(2, 2, processor);
executor.submit(raw(0));
executor.submit(raw(1));
completeWorkerFor(1);
assertTrue(executor.pollOrdered().isEmpty());
completeWorkerFor(0);
assertEquals(List.of(0L, 1L), drainSequences(executor));
```

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :common:test --tests "*OrderedBatchExecutorTest" --no-configuration-cache
```

Expected: missing worker classes.

- [ ] **Step 3: Implement immutable raw batches and ordered completion**

Use:

```java
public record RawPrimitiveStream(
        String layerGroupId,
        MaterialKey material,
        PrimitiveMode mode,
        List<Vertex> vertices) {}

public record RawCapturedObject(
        String objectId,
        CapturedNode.Kind kind,
        List<RawPrimitiveStream> streams,
        Map<String, Object> extras) {}

public record RawChunkBatch(
        long sequence,
        ChunkCoordinate chunk,
        List<RawCapturedObject> objects,
        List<Diagnostic> diagnostics,
        BatchCounters counters) {}

public final class OrderedBatchExecutor implements AutoCloseable {
    @FunctionalInterface
    public interface BatchProcessor {
        ChunkBatch process(RawChunkBatch raw, CancellationToken token) throws Exception;
    }
}
```

`OrderedBatchExecutor` owns one fixed `ExecutorService`, a bounded submission semaphore, a `ConcurrentSkipListMap<Long, Result>`, `nextSequence`, and a shared `CancellationToken`; `BatchProcessor` is a nested functional interface. It never invokes Minecraft APIs. The production processor groups QUAD streams by `layerGroupId`, applies `CoplanarQuadLayering`, appends the resulting streams to one `PrimitiveAccumulator` per object/material/mode, seals them, and emits final `CapturedNode` values. Non-QUAD streams bypass layering but still seal through the same accumulator.

- [ ] **Step 4: Refactor ExportJob into nonblocking pipeline polling**

The client tick method may:

1. capture under 6 ms budget;
2. submit finished immutable raw chunks if capacity exists;
3. poll ordered processed batches and offer them to the writer;
4. poll writer result;
5. never call blocking `Future.get()`.

Extend `BatchSink` with persisted-batch telemetry. Remove the old writer thread's immediate `DRAINING=80` update.

- [ ] **Step 5: Run concurrency and existing job suites**

```powershell
./gradlew.bat :common:test --tests "*OrderedBatchExecutorTest" `
  --tests "*ExportJobTest" --tests "*StreamingBatchSinkTest" `
  --tests "*ExportJobManagerTest" --no-configuration-cache
```

Expected: PASS, bounded queues, deterministic order, prompt cancellation.

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/com/nebysse/minetomesh/job `
  common/src/test/java/com/nebysse/minetomesh/job
git commit -m "feat: process export batches with bounded workers"
```

---

### Task 6: Implement the loader-free server session state machine and recovery journal

**Files:**
- Create: `common/src/main/java/com/nebysse/minetomesh/session/ExportSessionState.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/session/ServerExportSession.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/session/ServerExportSessionCoordinator.java`
- Create: `common/src/main/java/com/nebysse/minetomesh/session/RandomTickRecoveryStore.java`
- Test: `common/src/test/java/com/nebysse/minetomesh/session/ServerExportSessionCoordinatorTest.java`
- Test: `common/src/test/java/com/nebysse/minetomesh/session/RandomTickRecoveryStoreTest.java`

- [ ] **Step 1: Write failing state, timeout, and cleanup tests**

Cover legal transitions, global busy rejection, batches of `1/4/16`, final short batch, wrong player/session/sequence rejection, duplicate idempotent acks, load/sync/heartbeat/finalization timeouts, and cleanup continuing after one recovery action throws.

Use fake ports. `SessionRuntime` and `SessionMessenger` are nested interfaces of `ServerExportSessionCoordinator`, so platform modules implement one explicit boundary instead of importing platform types into common:

```java
interface SessionRuntime {
    int readRandomTickSpeed();
    void writeRecovery(RecoveryRecord record) throws Exception;
    void setRandomTickSpeed(int value) throws Exception;
    CompletionStage<Void> loadChunks(UUID sessionId, List<ChunkCoordinate> chunks);
    void setTrackingCenter(UUID playerId, ChunkCoordinate center) throws Exception;
    void releaseChunks(UUID sessionId, List<ChunkCoordinate> chunks) throws Exception;
    void restoreTrackingCenter(UUID playerId) throws Exception;
    void deleteRecovery() throws Exception;
}
```

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :common:test --tests "*ServerExportSessionCoordinatorTest" `
  --tests "*RandomTickRecoveryStoreTest" --no-configuration-cache
```

Expected: missing session package.

- [ ] **Step 3: Implement session records and coordinator**

`begin()` atomically owns the sole session, writes recovery before changing the rule, sets the rule to zero, and starts the first batch. `tick(now)` checks async load completion and fixed deadlines. Public acknowledgements require matching player, session, dimension, and sequence.

`cleanup()` runs release, tracking restore, random-tick restore, recovery deletion, and lock release as separate guarded actions. It returns a list of cleanup diagnostics rather than aborting after the first exception.

- [ ] **Step 4: Implement atomic JSON recovery storage**

Write UTF-8 Gson JSON to a sibling temporary file, `FileChannel.force(true)`, then atomic move with non-atomic fallback. Record:

```java
public record RecoveryRecord(
        UUID sessionId,
        UUID playerId,
        String dimension,
        int randomTickSpeed,
        Instant createdAt) {}
```

Malformed files return an explicit corrupt result and are not deleted automatically.

- [ ] **Step 5: Run session tests**

```powershell
./gradlew.bat :common:test --tests "*session*" --no-configuration-cache
```

Expected: PASS for normal, cancellation, timeout, duplicate, and partial cleanup failure paths.

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/com/nebysse/minetomesh/session `
  common/src/test/java/com/nebysse/minetomesh/session
git commit -m "feat: coordinate authoritative export sessions"
```

---

### Task 7: Define and register the rolling-session network contract

**Files:**
- Create: the twelve payload classes listed in File Structure in both modules
- Modify: both `network/ExportWandRequestPayload.java`
- Modify: both `network/WandPayloads.java`
- Modify: both `network/WandClientReceiver.java`
- Test: both `network/WandPayloadCodecTest.java`
- Test: both `network/WandRequestPolicyTest.java`
- Test: both `network/WandClientReceiverLifecycleTest.java`

- [ ] **Step 1: Write failing codec and direction tests**

Use exact schemas:

| Payload | Direction | Fields after common session identity |
|---|---|---|
| `ExportSessionAccepted` | S→C | selection, exportName, includePlayers, batchSize, totalChunks, totalBatches |
| `ExportSessionRejected` | S→C | reasonKey |
| `BatchLoadStarted` | S→C | sequence, chunk list |
| `BatchReady` | S→C | sequence, chunk list |
| `BatchClientReadable` | C→S | sequence |
| `BatchCaptureCompleted` | C→S | sequence, capturedPositions, processedChunks |
| `ExportProgressHeartbeat` | C→S | sequence, stageKey, completedUnits |
| `CancelExportRequest` | C→S | reasonKey |
| `ExportCancelAcknowledged` | S→C | terminal cleanup diagnostics count |
| `ExportClientCompleted` | C→S | persistedBatches, status |
| `ExportSessionFinished` | S→C | status |
| `ExportSessionFailed` | S→C | reasonKey, sequence, optional chunk |

Common identity is `sessionId`, `wandId`, and dimension; batch payloads also contain `batchSequence`. Assert serverbound/clientbound registration direction.

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :neoforge-1.21.1:test --tests "*WandPayloadCodecTest" `
  :fabric-26.2:test --tests "*WandPayloadCodecTest" --no-configuration-cache
```

Expected: missing payload classes and old immediate-grant flow.

- [ ] **Step 3: Implement payload records and codecs on NeoForge**

Use `CustomPacketPayload.Type`, `StreamCodec.composite` where practical, and the existing `PayloadRegistrar`. All server handlers call `context.enqueueWork`; all client handlers delegate to `WandClientReceiver`.

- [ ] **Step 4: Implement equivalent Fabric 26.2 payloads**

Use `Identifier`, `PayloadTypeRegistry.serverboundPlay/clientboundPlay`, `ServerPlayNetworking.registerGlobalReceiver`, and client receivers in `MineToMeshFabricClient`. Ensure all handlers execute session mutation on the correct game thread.

- [ ] **Step 5: Change the export request semantics**

The existing request carries export name only. The server re-reads `batchChunkCount` from the bound wand and creates `sessionId`; clients never choose the authoritative batch size independently. Keep permission and selection validation before acquiring the global session.

- [ ] **Step 6: Run network suites**

```powershell
./gradlew.bat :neoforge-1.21.1:test --tests "*network*" `
  :fabric-26.2:test --tests "*network*" --no-configuration-cache
```

Expected: PASS for codecs, directions, duplicate/late rejection, and receiver lifecycle.

- [ ] **Step 7: Commit**

```powershell
git add neoforge-1.21.1/src/main/java/com/nebysse/minetomesh/network `
  fabric-26.2/src/main/java/com/nebysse/minetomesh/network `
  fabric-26.2/src/client/java/com/nebysse/minetomesh/network `
  neoforge-1.21.1/src/test fabric-26.2/src/test
git commit -m "feat: add rolling export session protocol"
```

---

### Task 8: Implement NeoForge server tickets, tracking center, and random-tick recovery

**Files:**
- Create: `neoforge-1.21.1/src/main/java/com/nebysse/minetomesh/server/PlatformExportRuntime.java`
- Create: `neoforge-1.21.1/src/main/java/com/nebysse/minetomesh/server/ServerExportSessions.java`
- Create: `neoforge-1.21.1/src/main/java/com/nebysse/minetomesh/mixin/ChunkMapTrackingCenterMixin.java`
- Create: `neoforge-1.21.1/src/main/resources/minetomesh.mixins.json`
- Modify: `neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml`
- Modify: `neoforge-1.21.1/src/main/java/com/nebysse/minetomesh/MineToMesh.java`
- Test: `neoforge-1.21.1/src/test/java/com/nebysse/minetomesh/server/NeoForgeExportRuntimePolicyTest.java`
- Test: `neoforge-1.21.1/src/test/java/com/nebysse/minetomesh/server/ServerExportSessionsTest.java`

- [ ] **Step 1: Write failing platform-policy tests**

Test ticket identity symmetry, batch peak count, game-rule get/set, recovery path, tracking-center lookup, lifecycle cleanup, and no client imports in server classes. Use a fake runtime around the common coordinator for behavior tests.

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :neoforge-1.21.1:test --tests "*server*" --no-configuration-cache
```

Expected: missing server runtime classes.

- [ ] **Step 3: Implement NeoForge chunk tickets and readiness**

Define one non-expiring custom ticket type with a stable session/chunk key. For each current chunk:

```java
source.addRegionTicket(MINETOMESH_TICKET, pos, 2,
        new ExportTicketKey(sessionId, pos));
CompletableFuture<ChunkResult<ChunkAccess>> future =
        source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
```

Remove with exactly the same type, level, and key. Aggregate the current batch futures without blocking the server thread. `forceTicks` remains false because random ticking is globally frozen and the export needs loaded data, not simulated chunks.

- [ ] **Step 4: Implement NeoForge game-rule recovery**

Use:

```java
int old = server.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
server.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
```

Restore with the saved integer. Place recovery JSON under the server config directory at `config/minetomesh/export-session-recovery.json`.

- [ ] **Step 5: Redirect the tracking center through Mixin**

Inject only into `ChunkMap.updateChunkTracking(ServerPlayer)` and redirect its `ServerPlayer.chunkPosition()` call:

```java
@Redirect(
    method = "updateChunkTracking",
    at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
private ChunkPos minetomesh$trackingCenter(ServerPlayer player) {
    return ServerExportSessions.trackingCenter(player.getUUID())
            .map(value -> new ChunkPos(value.x(), value.z()))
            .orElseGet(player::chunkPosition);
}
```

This lets vanilla `applyChunkTrackingView` send center, watch, and unwatch packets while the session is active. On cleanup remove the override; the next `ChunkMap.tick()` restores the real center.

- [ ] **Step 6: Register lifecycle hooks**

Wire server tick, logout, dimension change, server stopping, and startup recovery events. Startup with a corrupt recovery file logs a stable error and leaves session creation disabled. Register payload handlers against the singleton coordinator.

- [ ] **Step 7: Run NeoForge tests and server smoke**

```powershell
./gradlew.bat :neoforge-1.21.1:test `
  :neoforge-1.21.1:runServerSmoke --no-configuration-cache
```

Expected: tests pass, `MINETOMESH_SERVER_READY`, no client-class link error, and no lingering recovery file after controlled shutdown.

- [ ] **Step 8: Commit**

```powershell
git add neoforge-1.21.1
git commit -m "feat(neoforge): coordinate rolling chunk sessions"
```

---

### Task 9: Implement Fabric 26.2 server tickets, tracking center, and random-tick recovery

**Files:**
- Create: `fabric-26.2/src/main/java/com/nebysse/minetomesh/server/PlatformExportRuntime.java`
- Create: `fabric-26.2/src/main/java/com/nebysse/minetomesh/server/ServerExportSessions.java`
- Create: `fabric-26.2/src/main/java/com/nebysse/minetomesh/mixin/ChunkMapTrackingCenterMixin.java`
- Create: `fabric-26.2/src/main/resources/minetomesh.mixins.json`
- Modify: `fabric-26.2/src/main/resources/fabric.mod.json`
- Modify: `fabric-26.2/src/main/java/com/nebysse/minetomesh/fabric/MineToMeshFabric.java`
- Test: `fabric-26.2/src/test/java/com/nebysse/minetomesh/server/FabricExportRuntimePolicyTest.java`
- Test: `fabric-26.2/src/test/java/com/nebysse/minetomesh/server/ServerExportSessionsTest.java`

- [ ] **Step 1: Write the Fabric platform tests before production code**

Mirror the NeoForge behavior contract, but assert 26.2 APIs and class isolation. Include a Mixin metadata test that checks `fabric.mod.json` names `minetomesh.mixins.json`.

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :fabric-26.2:test --tests "*server*" `
  --tests "*FabricMetadataTest" --no-configuration-cache
```

Expected: missing runtime and Mixin metadata.

- [ ] **Step 3: Implement Fabric 26.2 ticket loading**

Use one MineToMesh ticket type:

```java
private static final TicketType MINETOMESH_TICKET =
        new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
```

For every current chunk call:

```java
source.addTicketAndLoadWithRadius(MINETO_TICKET, pos, 0);
```

Aggregate returned futures; release with:

```java
source.removeTicketWithRadius(MINETO_TICKET, pos, 0);
```

Never use persistent `updateChunkForced`, because MineToMesh tickets must not become permanent `/forceload` state.

- [ ] **Step 4: Implement Fabric 26.2 game rules**

Use:

```java
int old = server.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
```

Restore through the same typed API and the common recovery store.

- [ ] **Step 5: Add the equivalent tracking-center redirect**

Use the same logical redirect target against the 26.2 mapped `ChunkMap.updateChunkTracking`. Return the 26.2 `ChunkPos` from `ServerExportSessions.trackingCenter`. Register the Mixin in `fabric.mod.json`:

```json
"mixins": ["minetomesh.mixins.json"]
```

- [ ] **Step 6: Register Fabric lifecycle hooks**

Use `ServerTickEvents.END_SERVER_TICK`, `ServerPlayConnectionEvents.DISCONNECT`, and `ServerLifecycleEvents.SERVER_STARTING/SERVER_STOPPING`. Keep the existing server-smoke property behavior.

- [ ] **Step 7: Run Fabric tests and smoke**

```powershell
./gradlew.bat :fabric-26.2:test `
  :fabric-26.2:fabricServerSmoke --no-configuration-cache
```

Expected: Minecraft 26.2 server reaches ready, stops cleanly, and logs no Mixin target failure.

- [ ] **Step 8: Commit**

```powershell
git add fabric-26.2
git commit -m "feat(fabric): coordinate rolling chunk sessions"
```

---

### Task 10: Convert both client capture pipelines to authorized rolling batches

**Files:**
- Modify: both `job/DefaultExportPipeline.java`
- Modify: both `world/WorldPlanner.java`
- Modify: both `client/MineToMeshClient.java`
- Modify: both `client/wand/ExportWandController.java`
- Modify: both `network/WandClientReceiver.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/job/ExportJob.java`
- Test: both `job/DefaultExportPipelinePolicyTest.java`
- Test: both `client/wand/ExportWandControllerTest.java`
- Test: both `network/WandClientReceiverLifecycleTest.java`

- [ ] **Step 1: Write failing rolling-client tests**

Test that no capture starts before `BatchReady`; readability acknowledgement requires all authorized chunks; the planner emits sections only for current chunks; entities belong to exactly one horizontal chunk; `BatchCaptureCompleted` is sent only after worker results are ordered and accepted by the writer; stale sequence is ignored; cancellation remains in GUI until server acknowledgement.

- [ ] **Step 2: Verify red on both platforms**

```powershell
./gradlew.bat :neoforge-1.21.1:test --tests "*ExportWandControllerTest" `
  --tests "*DefaultExportPipelinePolicyTest" `
  :fabric-26.2:test --tests "*ExportWandControllerTest" `
  --tests "*DefaultExportPipelinePolicyTest" --no-configuration-cache
```

Expected: existing controller starts a full-world client job immediately after grant.

- [ ] **Step 3: Introduce client rolling-session states**

Extend controller states with:

```text
WAITING_FOR_SESSION
LOADING_BATCH
WAITING_FOR_CHUNKS
CAPTURING
PROCESSING
WRITING
FINALIZING
CANCELLING
CLEANING_UP
```

`ExportSessionAccepted` initializes totals and the transaction but does not capture. `BatchReady` installs current coordinates. Client Tick polls `level.hasChunk` for every coordinate, sends `BatchClientReadable`, then begins capture.

- [ ] **Step 4: Restrict planning and entities to the current window**

`WorldPlanner.planBatch(level, selection, List<ChunkCoordinate>)` creates vertical `SectionWork` only for authorized chunks and fails if any becomes unreadable. Entity capture filters by entity `chunkPosition()` membership in the current batch so no entity is duplicated.

- [ ] **Step 5: Split renderer capture from pure processing**

On Render Thread capture model/renderer/texture output into immutable raw object streams. Move accumulator sealing, topology conversion, coordinate-only transformations, per-chunk grouping, and diagnostics aggregation into the common `BatchProcessor`. Do not move TextureManager, atlas, GPU, block entity renderer, or entity renderer calls.

- [ ] **Step 6: Wire worker and writer acknowledgements**

Use the session's locked worker count. After all chunks in a batch have been captured and their sequences accepted by ordered writer input, send `BatchCaptureCompleted`. Do not request the next batch until the server confirms release and sends the next `BatchLoadStarted`.

- [ ] **Step 7: Complete cancellation and terminal handshakes**

The local stop action cancels workers/writer and sends `CancelExportRequest`; the controller stays `CANCELLING/CLEANING_UP` until `ExportCancelAcknowledged`. Normal completion sends `ExportClientCompleted`; GUI reaches 100 only after `ExportSessionFinished` and tracking restoration.

- [ ] **Step 8: Run client policy and core job tests**

```powershell
./gradlew.bat :common:test --tests "*ExportJobTest" `
  :neoforge-1.21.1:test --tests "*DefaultExportPipelinePolicyTest" `
  --tests "*ExportWandControllerTest" --tests "*WandClientReceiverLifecycleTest" `
  :fabric-26.2:test --tests "*DefaultExportPipelinePolicyTest" `
  --tests "*ExportWandControllerTest" --tests "*WandClientReceiverLifecycleTest" `
  --no-configuration-cache
```

Expected: PASS with no full-selection eager plan and no Minecraft access from Worker threads.

- [ ] **Step 9: Commit**

```powershell
git add common neoforge-1.21.1 fabric-26.2
git commit -m "feat: export authorized rolling chunk batches"
```

---

### Task 11: Add client worker settings and complete GUI controls

**Files:**
- Create: both `client/config/ClientExportSettings.java`
- Create: both `client/config/ClientExportSettingsStore.java`
- Modify: both `client/wand/ExportWandScreen.java`
- Modify: both `client/wand/ExportWandController.java`
- Modify: NeoForge and shared copied language JSON resources
- Test: both `client/config/ClientExportSettingsStoreTest.java`
- Test: both `client/wand/ExportWandLayoutTest.java`
- Test: both `client/wand/ExportWandScreenBindingTest.java`

- [ ] **Step 1: Write failing settings and GUI binding tests**

Test missing file default, CPU clamping, malformed-file quarantine, atomic save, batch field binding, worker field binding, input lock during sessions, stop-without-close, cleanup disable state, localized stage labels, and all progress metrics using the same snapshot.

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :neoforge-1.21.1:test --tests "*ClientExportSettingsStoreTest" `
  --tests "*ExportWandScreenBindingTest" `
  :fabric-26.2:test --tests "*ClientExportSettingsStoreTest" `
  --tests "*ExportWandScreenBindingTest" --no-configuration-cache
```

Expected: missing settings classes and controls.

- [ ] **Step 3: Implement local settings storage**

```java
public record ClientExportSettings(int workerThreads) {}
```

Store at `config/minetomesh/client-export-settings.json`. Load through `ExportExecutionPolicy.clampWorkers`. On malformed JSON, rename to `client-export-settings.json.corrupt-<timestamp>`, use default, and log one diagnostic. Save through temp file and atomic move.

- [ ] **Step 4: Add GUI controls**

Add numeric controls for `每批区块数` and `数据处理线程`, with text entry, `±1`, and wheel behavior. Show:

```text
CPU 逻辑线程 N / 最大工作线程 M
```

Batch mutations send `UpdateWandBatchSizePayload`; worker mutations update local settings only. Lock both controls during an active session.

- [ ] **Step 5: Implement stop and progress presentation**

During active export, the secondary button says “停止导出” and calls `controller.requestCancel("user_cancelled")` without closing. During cleanup both buttons are disabled. After terminal state the primary button says “再次导出”. Draw overall percent, localized stage, batch/chunk/position counts, configured/effective workers, both queue depths, current chunk, and elapsed time from one snapshot.

- [ ] **Step 6: Add translation keys**

Add Chinese and English keys for stages, counters, buttons, validation errors, server busy, timeouts, restoration failures, and corrupt recovery/config files. Keep internal enum names out of visible UI.

- [ ] **Step 7: Run GUI and settings tests**

```powershell
./gradlew.bat :neoforge-1.21.1:test --tests "*ClientExportSettingsStoreTest" `
  --tests "*ExportWandLayoutTest" --tests "*ExportWandScreenBindingTest" `
  :fabric-26.2:test --tests "*ClientExportSettingsStoreTest" `
  --tests "*ExportWandLayoutTest" --tests "*ExportWandScreenBindingTest" `
  --no-configuration-cache
```

Expected: PASS on both GUI APIs.

- [ ] **Step 8: Commit**

```powershell
git add neoforge-1.21.1 fabric-26.2
git commit -m "feat: configure rolling export from wand GUI"
```

---

### Task 12: Extend reports, documentation, package contracts, and final verification

**Files:**
- Modify: `common/src/main/java/com/nebysse/minetomesh/report/ExportReport.java`
- Modify: `common/src/main/java/com/nebysse/minetomesh/job/StreamingBatchSink.java`
- Modify: `common/src/test/java/com/nebysse/minetomesh/report/ReportWriterTest.java`
- Modify: `README.md`
- Create: `docs/releases/1.3.0.md`
- Modify: `docs/testing/manual-client-matrix.md`
- Create: `docs/testing/1.3.0-rolling-export-verification.md`
- Modify: both metadata/JAR contract tests

- [ ] **Step 1: Write failing report and final JAR tests**

Assert the report fields from the design: snapshot mode, batch/configured/effective workers, total/peak chunks, cancellation stage, original random tick speed, and seven timing categories. Assert successful reports have no missing chunks. Update both JAR name and version checks.

- [ ] **Step 2: Verify red**

```powershell
./gradlew.bat :common:test --tests "*ReportWriterTest" `
  :neoforge-1.21.1:test --tests "*Version130ContractTest" `
  :fabric-26.2:test --tests "*FabricJarContractTest" --no-configuration-cache
```

Expected: missing report fields and old docs/contracts.

- [ ] **Step 3: Implement report extensions**

Add a dedicated immutable execution-metrics record to `ExportReport`; serialize deterministic sorted timing keys. On success require `missingChunks.isEmpty()`. Capture peak forced chunks from server session telemetry and worker values from the locked client session.

- [ ] **Step 4: Update documentation**

Document the visible-world flicker caveat, full-server random-tick freeze, global one-session limit, batch `1..16`, CPU worker formula, cancellation semantics, recovery journal, and exact 1.3.0 artifact names. Release notes must distinguish automated coverage from manual client/Blender validation.

- [ ] **Step 5: Run all automated verification**

```powershell
./gradlew.bat clean :common:test --no-configuration-cache
./gradlew.bat :neoforge-1.21.1:test :neoforge-1.21.1:build --no-configuration-cache
./gradlew.bat :fabric-26.2:test :fabric-26.2:build --no-configuration-cache
./gradlew.bat :neoforge-1.21.1:runServerSmoke --no-configuration-cache
./gradlew.bat :fabric-26.2:fabricServerSmoke --no-configuration-cache
./gradlew.bat build --no-configuration-cache
```

Expected: every command succeeds; both approved JARs exist; both server smokes print `MINETOMESH_SERVER_READY`; no Mixin target, client-link, recipe, or recovery error appears.

- [ ] **Step 6: Inspect final JAR contents and hashes**

Verify metadata, Mixin configs, both entrypoints, common core, translations, wand resources, recipes, and exactly 77 GUI slices. Record byte sizes and SHA-256 in the verification document.

- [ ] **Step 7: Perform available real-client acceptance**

Run Fabric 26.2 client and NeoForge 1.21.1 client. Where GUI control is available, test batches `1/4/16`, workers `1/default/max`, a selection outside view distance, mid-batch cancellation, progress stages, random-tick restoration, and tracking restoration. Import glTF/USDA into Blender 5.2 and run Khronos Validator when available. Mark every unavailable visual/manual item explicitly “未执行”.

- [ ] **Step 8: Commit final release work**

```powershell
git add README.md docs common neoforge-1.21.1 fabric-26.2
git commit -m "docs: verify MineToMesh 1.3.0"
```

---

## Plan Self-Review

- **Spec coverage:** Tasks 2/8/9/10 cover rolling force loading and tracking; Tasks 5/10 cover safe multithreading; Tasks 4/11 cover full progress; Tasks 6/8/9 cover random ticks and crash recovery; Tasks 3/11 cover GUI persistence; Task 12 covers reports, packaging, and acceptance.
- **Thread boundary:** No task moves Minecraft world, renderer, atlas, TextureManager, NativeImage, or GPU access off the legal game/render thread.
- **Determinism:** Task 5 sequences all worker results before the single writer.
- **Resource bounds:** The current forced window is at most 16 and always lies inside one compact 4×4 macro window; worker and writer queues are bounded; the chunk plan is lazy.
- **Cleanup:** Common cleanup is idempotent and both platform lifecycle tasks wire disconnect, timeout, server stop, dimension change, GUI close, and explicit cancellation.
- **No placeholders:** All decisions, ranges, formulas, payload fields, timeouts, files, commands, and expected outcomes are specified.
