# Persistent Selection Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not dispatch subagents for this project. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independent client-only “锁定选区” option that persists one complete selection per single-player world or multiplayer server and renders it without requiring the wand to remain held.

**Architecture:** `WorldProfileKey` isolates contexts using SHA-256, `LockedSelectionStore` owns deterministic JSON and atomic disk replacement, and `LockedSelectionService` provides toggle/resolve behavior without changing wand components or network payloads. The renderer merges held and locked snapshots with equality deduplication; the screen injects the service and exposes three independent toggles.

**Tech Stack:** Java 21, Gson 2.10.1, Minecraft 1.21.1 client APIs, NeoForge client events, JUnit 5, Gradle.

---

## File map

- Create `src/main/java/com/nebysse/minetomesh/client/selection/LockedSelection.java`: validated persisted dimension and endpoints.
- Create `src/main/java/com/nebysse/minetomesh/client/selection/WorldProfileKey.java`: stable normalized single-player/server context hashes.
- Create `src/main/java/com/nebysse/minetomesh/client/selection/LockedSelectionStore.java`: schema-1 JSON load, atomic update, corruption isolation.
- Create `src/main/java/com/nebysse/minetomesh/client/selection/LockedSelectionService.java`: current-profile toggle, state and dimension-filtered resolution.
- Create tests under `src/test/java/com/nebysse/minetomesh/client/selection/`.
- Create `src/main/java/com/nebysse/minetomesh/client/wand/OverlaySnapshotPolicy.java`: combine and deduplicate held/locked snapshots.
- Modify `HeldWandOverlaySource.java` only if needed to expose a shared immutable Snapshot contract.
- Modify `SelectionOverlayRenderer.java`: resolve both sources and draw each unique snapshot.
- Modify `ExportWandScreen.java`: three equal toggles and local lock status.
- Modify `MineToMeshClient.java`: construct one store/service, resolve context, inject renderer and screen.
- Modify layout, renderer and integration tests.
- Modify `README.md` and `docs/testing/manual-client-matrix.md` with persistence lifecycle.

### Task 1: Define locked selections and stable profile hashes

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/client/selection/LockedSelection.java`
- Create: `src/main/java/com/nebysse/minetomesh/client/selection/WorldProfileKey.java`
- Test: `src/test/java/com/nebysse/minetomesh/client/selection/WorldProfileKeyTest.java`
- Test: `src/test/java/com/nebysse/minetomesh/client/selection/LockedSelectionTest.java`

- [ ] **Step 1: Write failing value and hash tests**

```java
@Test
void equivalentSingleplayerPathsAndServerAddressesHashIdentically() {
    assertEquals(
            WorldProfileKey.singleplayer(Path.of("world", ".", "region", "..")),
            WorldProfileKey.singleplayer(Path.of("world")));
    assertEquals(
            WorldProfileKey.multiplayer("Example.COM"),
            WorldProfileKey.multiplayer("example.com:25565"));
    assertTrue(WorldProfileKey.multiplayer("example.com:25565")
            .value().matches("[0-9a-f]{64}"));
}

@Test
void lockedSelectionConvertsToNormalizedSelectionAndFiltersDimension() {
    LockedSelection locked = new LockedSelection(
            ResourceLocation.parse("minecraft:overworld"),
            new BlockPos(4, 70, 8), new BlockPos(-2, 64, 1));
    assertEquals("minecraft:overworld", locked.toSelection().min().dimension());
    assertTrue(locked.snapshot(ResourceLocation.parse("minecraft:overworld")).isPresent());
    assertTrue(locked.snapshot(ResourceLocation.parse("minecraft:the_nether")).isEmpty());
}
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests "com.nebysse.minetomesh.client.selection.*"
```

Expected: compilation failure because the package is absent.

- [ ] **Step 3: Implement the two immutable values**

`LockedSelection` must be a record with non-null `ResourceLocation dimension`, `BlockPos pos1`, and `BlockPos pos2`. It exposes:

```java
public Selection toSelection() {
    return Selection.of(
            new BlockPoint(dimension.toString(), pos1.getX(), pos1.getY(), pos1.getZ()),
            new BlockPoint(dimension.toString(), pos2.getX(), pos2.getY(), pos2.getZ()));
}

public Optional<HeldWandOverlaySource.Snapshot> snapshot(ResourceLocation currentDimension) {
    return dimension.equals(currentDimension)
            ? Optional.of(new HeldWandOverlaySource.Snapshot(
                    Optional.of(pos1), Optional.of(pos2),
                    Optional.of(toSelection()), dimension))
            : Optional.empty();
}
```

`WorldProfileKey` must hash UTF-8 bytes of `singleplayer\0<normalized-absolute-path>` or `multiplayer\0<normalized-host>:<port>` with SHA-256 and expose only the 64-character lowercase hex `value`. Server normalization trims whitespace, lowercases the host, treats a missing port as `25565`, and preserves bracketed IPv6 host boundaries. Reject blank hosts and ports outside `1..65535`. The raw path/address must not be stored in the record or returned by `toString()`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all new tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/client/selection/LockedSelection.java src/main/java/com/nebysse/minetomesh/client/selection/WorldProfileKey.java src/test/java/com/nebysse/minetomesh/client/selection/LockedSelectionTest.java src/test/java/com/nebysse/minetomesh/client/selection/WorldProfileKeyTest.java
git commit -m "feat: define persistent selection profiles"
```

### Task 2: Implement deterministic atomic JSON storage

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/client/selection/LockedSelectionStore.java`
- Test: `src/test/java/com/nebysse/minetomesh/client/selection/LockedSelectionStoreTest.java`

- [ ] **Step 1: Write failing round-trip, isolation and failure tests**

```java
@Test
void persistsOneSelectionPerProfileAndReloads() throws Exception {
    Path file = tempDir.resolve("locked-selections.json");
    LockedSelectionStore store = LockedSelectionStore.open(file);
    store.put(profile("a"), selection("minecraft:overworld", 0));
    store.put(profile("b"), selection("minecraft:the_nether", 20));

    LockedSelectionStore reloaded = LockedSelectionStore.open(file);
    assertEquals(selection("minecraft:overworld", 0), reloaded.get(profile("a")).orElseThrow());
    assertEquals(selection("minecraft:the_nether", 20), reloaded.get(profile("b")).orElseThrow());
}

@Test
void failedReplacementKeepsOldMemoryAndFile() throws Exception {
    Path file = tempDir.resolve("locked-selections.json");
    LockedSelectionStore initial = LockedSelectionStore.open(file);
    initial.put(profile("a"), selection("minecraft:overworld", 0));
    LockedSelectionStore failing = LockedSelectionStore.open(
            file, (source, target) -> { throw new IOException("denied"); });

    assertThrows(IOException.class,
            () -> failing.put(profile("a"), selection("minecraft:overworld", 30)));
    assertEquals(selection("minecraft:overworld", 0),
            failing.get(profile("a")).orElseThrow());
    assertEquals(selection("minecraft:overworld", 0),
            LockedSelectionStore.open(file).get(profile("a")).orElseThrow());
}
```

Define a package-private `FileReplacer` functional interface and a package-private `open(Path, FileReplacer)` overload. Production `open(Path)` supplies the atomic replacement implementation; tests inject a replacer that throws without adding test-only methods to production objects.

Add a corrupt JSON test that writes invalid text, opens the store, asserts it is empty, and asserts one sibling file matches `locked-selections.json.corrupt-*`.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.client.selection.LockedSelectionStoreTest
```

Expected: compilation failure because the store is absent.

- [ ] **Step 3: Implement schema-1 load and transactional updates**

Public API:

```java
public final class LockedSelectionStore {
    public static LockedSelectionStore open(Path file) throws IOException;
    public static LockedSelectionStore empty(Path file);
    static LockedSelectionStore open(Path file, FileReplacer replacer) throws IOException;
    public Optional<LockedSelection> get(WorldProfileKey profile);
    public boolean matches(WorldProfileKey profile, LockedSelection selection);
    public void put(WorldProfileKey profile, LockedSelection selection) throws IOException;
    public void remove(WorldProfileKey profile) throws IOException;
}
```

Serialize with `GsonBuilder().setPrettyPrinting().disableHtmlEscaping()`. Write profiles in a `TreeMap` so output is stable. Validate `schemaVersion == 1`, dimension resource locations, exactly three integers per endpoint, and complete records.

For `put` and `remove`:

1. Copy the current map.
2. Apply the change to the copy.
3. Create parent directories.
4. Write `<filename>.tmp-<UUID>` in the same directory.
5. Close the writer.
6. Move with `ATOMIC_MOVE` and `REPLACE_EXISTING`; retry with `REPLACE_EXISTING` when atomic move is unsupported.
7. Assign the copied map to memory only after the move succeeds.
8. Delete the temporary file in `finally`.

When existing JSON is malformed, move it to `<filename>.corrupt-<epochMillis>`, return an empty store, and never overwrite the quarantined file during open.

- [ ] **Step 4: Run store tests and verify GREEN**

Expected: all storage, ordering, replacement and corruption tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/client/selection/LockedSelectionStore.java src/test/java/com/nebysse/minetomesh/client/selection/LockedSelectionStoreTest.java
git commit -m "feat: persist locked selections atomically"
```

### Task 3: Add profile-aware lock behavior

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/client/selection/LockedSelectionService.java`
- Test: `src/test/java/com/nebysse/minetomesh/client/selection/LockedSelectionServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Use a temporary real store and a mutable `Supplier<Optional<WorldProfileKey>>` to test:

```java
@Test
void togglesCurrentSelectionAndOverwritesDifferentSelection() throws Exception {
    LockedSelectionService service = serviceFor(profile("server"));
    LockedSelection first = selection("minecraft:overworld", 0);
    LockedSelection second = selection("minecraft:overworld", 20);

    assertEquals(ToggleResult.LOCKED, service.toggle(Optional.of(first)));
    assertTrue(service.isCurrent(first));
    assertEquals(ToggleResult.REPLACED, service.toggle(Optional.of(second)));
    assertTrue(service.isCurrent(second));
    assertEquals(ToggleResult.UNLOCKED, service.toggle(Optional.of(second)));
    assertTrue(service.resolve(ResourceLocation.parse("minecraft:overworld")).isEmpty());
}

@Test
void rejectsIncompleteOrMissingProfileWithoutChangingStore() throws Exception {
    assertEquals(ToggleResult.INCOMPLETE, service.toggle(Optional.empty()));
    profileSupplier.set(Optional.empty());
    assertEquals(ToggleResult.NO_PROFILE,
            service.toggle(Optional.of(selection("minecraft:overworld", 0))));
}
```

Add a dimension test proving a locked Overworld snapshot disappears in Nether and returns in Overworld.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.client.selection.LockedSelectionServiceTest
```

Expected: compilation failure because the service is absent.

- [ ] **Step 3: Implement the service contract**

```java
public final class LockedSelectionService {
    public enum ToggleResult {
        LOCKED, REPLACED, UNLOCKED, INCOMPLETE, NO_PROFILE, WRITE_FAILED
    }

    public ToggleResult toggle(Optional<LockedSelection> candidate);
    public boolean isCurrent(LockedSelection candidate);
    public Optional<HeldWandOverlaySource.Snapshot> resolve(ResourceLocation dimension);
    public Optional<String> lastError();
}
```

The constructor accepts a `LockedSelectionStore` and `Supplier<Optional<WorldProfileKey>>`. If the candidate equals the stored record, remove it. Otherwise put or replace it. Catch `IOException`, retain the Store's old state, set `lastError`, and return `WRITE_FAILED`. Clear `lastError` after the next successful operation.

- [ ] **Step 4: Run focused tests and verify GREEN**

Expected: all toggle, profile and dimension tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/client/selection/LockedSelectionService.java src/test/java/com/nebysse/minetomesh/client/selection/LockedSelectionServiceTest.java
git commit -m "feat: add locked selection service"
```

### Task 4: Merge held and locked overlay snapshots

**Files:**
- Create: `src/main/java/com/nebysse/minetomesh/client/wand/OverlaySnapshotPolicy.java`
- Create: `src/test/java/com/nebysse/minetomesh/client/wand/OverlaySnapshotPolicyTest.java`
- Modify: `src/main/java/com/nebysse/minetomesh/client/wand/SelectionOverlayRenderer.java`
- Modify: `src/test/java/com/nebysse/minetomesh/client/wand/SelectionOverlayRendererTest.java`

- [ ] **Step 1: Write failing deduplication tests**

```java
@Test
void deduplicatesEqualSnapshotsAndPreservesDifferentOrder() {
    Snapshot held = snapshot(0);
    Snapshot locked = snapshot(20);
    assertEquals(List.of(held), OverlaySnapshotPolicy.merge(
            Optional.of(held), Optional.of(held)));
    assertEquals(List.of(held, locked), OverlaySnapshotPolicy.merge(
            Optional.of(held), Optional.of(locked)));
    assertEquals(List.of(locked), OverlaySnapshotPolicy.merge(
            Optional.empty(), Optional.of(locked)));
}
```

Update the renderer policy test to require calls to both `heldSource.resolveSnapshot` and `lockedService.resolve`, and to reject any inventory scan.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.client.wand.OverlaySnapshotPolicyTest --tests com.nebysse.minetomesh.client.wand.SelectionOverlayRendererTest
```

Expected: missing policy and locked-service call assertions fail.

- [ ] **Step 3: Implement merge and render every unique snapshot**

`OverlaySnapshotPolicy.merge` uses a `LinkedHashSet` and returns `List.copyOf`. `SelectionOverlayRenderer` constructor becomes:

```java
public SelectionOverlayRenderer(
        HeldWandOverlaySource heldSource,
        LockedSelectionService lockedService)
```

At `AFTER_TRANSLUCENT_BLOCKS`, resolve the held Snapshot, resolve the current-dimension locked Snapshot, merge them, and return early only when the list is empty. Reuse the existing draw functions inside a loop; create and flush one `BufferSource` for all snapshots in the frame.

- [ ] **Step 4: Run renderer tests and verify GREEN**

Expected: merge and source-policy tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/client/wand/OverlaySnapshotPolicy.java src/main/java/com/nebysse/minetomesh/client/wand/SelectionOverlayRenderer.java src/test/java/com/nebysse/minetomesh/client/wand/OverlaySnapshotPolicyTest.java src/test/java/com/nebysse/minetomesh/client/wand/SelectionOverlayRendererTest.java
git commit -m "feat: render persistent locked selections"
```

### Task 5: Add the independent screen toggle

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/client/wand/ExportWandScreen.java`
- Modify: `src/test/java/com/nebysse/minetomesh/client/wand/ExportWandLayoutTest.java`
- Modify: `src/test/java/com/nebysse/minetomesh/client/wand/ExportWandScreenBindingTest.java`

- [ ] **Step 1: Write failing three-toggle layout and binding tests**

Require these non-overlapping rects inside `Layout.LEFT`:

```java
assertEquals(new Rect(Layout.LEFT.x() + 12, Layout.LEFT.y() + 144, 60, 16),
        Layout.overlayButton());
assertEquals(new Rect(Layout.LEFT.x() + 74, Layout.LEFT.y() + 144, 60, 16),
        Layout.lockedSelectionButton());
assertEquals(new Rect(Layout.LEFT.x() + 136, Layout.LEFT.y() + 144, 60, 16),
        Layout.includePlayersButton());
```

Update binding tests to require a `LockedSelectionService` constructor parameter, `toggleLockedSelection()`, no network payload in that method, and labels `手持预览`, `锁定选区`, `导出玩家`.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.client.wand.ExportWandLayoutTest --tests com.nebysse.minetomesh.client.wand.ExportWandScreenBindingTest
```

Expected: layout and constructor assertions fail.

- [ ] **Step 3: Implement local lock input and button state**

Add `LockedSelectionService lockedSelectionService`, `Button lockedSelectionButton`, and `boolean selectionLocked` to the screen. Create three equal toggles using existing nine-slice textures.

Build the lock candidate from the six current coordinate models, not from a potentially delayed server round-trip:

```java
private Optional<LockedSelection> currentLockedSelection() {
    OptionalInt x1 = coordinateModels.get(0).commit();
    OptionalInt y1 = coordinateModels.get(1).commit();
    OptionalInt z1 = coordinateModels.get(2).commit();
    OptionalInt x2 = coordinateModels.get(3).commit();
    OptionalInt y2 = coordinateModels.get(4).commit();
    OptionalInt z2 = coordinateModels.get(5).commit();
    if (Stream.of(x1, y1, z1, x2, y2, z2).anyMatch(OptionalInt::isEmpty)) {
        return Optional.empty();
    }
    return Optional.of(new LockedSelection(
            ResourceLocation.parse(currentDimension()),
            new BlockPos(x1.getAsInt(), y1.getAsInt(), z1.getAsInt()),
            new BlockPos(x2.getAsInt(), y2.getAsInt(), z2.getAsInt())));
}
```

Because `Stream.of` cannot infer primitive optional method references cleanly in every compiler, an explicit six-condition check is acceptable and preferred if compilation is clearer.

`toggleLockedSelection()` calls the service and sets a local status message:

- `LOCKED`: `选区已锁定`
- `REPLACED`: `已替换锁定选区`
- `UNLOCKED`: `选区已解锁`
- `INCOMPLETE`: `需要完整选区`
- `NO_PROFILE`: `无法识别当前世界`
- `WRITE_FAILED`: `保存锁定选区失败`

Add a local status field with precedence over controller READY text until the user edits coordinates, toggles again, or starts export; otherwise the existing `containerTick()` would erase the result every frame.

- [ ] **Step 4: Run layout and screen tests**

Expected: all screen and layout tests pass without adding a new network payload.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/client/wand/ExportWandScreen.java src/test/java/com/nebysse/minetomesh/client/wand/ExportWandLayoutTest.java src/test/java/com/nebysse/minetomesh/client/wand/ExportWandScreenBindingTest.java
git commit -m "feat: add persistent selection toggle"
```

### Task 6: Wire client context resolution and persistence

**Files:**
- Modify: `src/main/java/com/nebysse/minetomesh/client/MineToMeshClient.java`
- Create: `src/test/java/com/nebysse/minetomesh/client/LockedSelectionClientPolicyTest.java`

- [ ] **Step 1: Write a failing client wiring policy test**

Assert `MineToMeshClient`:

- opens `config/minetomesh/locked-selections.json` under `gameDirectory`;
- creates one shared `LockedSelectionService`;
- injects it into both renderer and screen;
- resolves multiplayer context from the current server address;
- resolves single-player context from the integrated server world root;
- does not clear persistent records in `onLoggingOut` or dimension changes.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.client.LockedSelectionClientPolicyTest
```

Expected: assertions fail because no persistent service exists.

- [ ] **Step 3: Add production context resolution and dependency injection**

In the client constructor, open:

```java
Path lockFile = Minecraft.getInstance().gameDirectory.toPath()
        .resolve("config").resolve("minetomesh")
        .resolve("locked-selections.json");
```

Create one `LockedSelectionStore` and `LockedSelectionService`. The profile supplier follows this order:

1. If `minecraft.getCurrentServer()` is non-null, hash its `ip` as multiplayer.
2. Else if `minecraft.getSingleplayerServer()` is non-null, obtain `getWorldPath(LevelResource.ROOT)` and hash it as single-player.
3. Else return empty.

If initial store opening throws an unrecoverable `IOException`, log through a `private static final Logger LOGGER = LoggerFactory.getLogger(MineToMesh.MOD_ID)` in `MineToMeshClient`, call `LockedSelectionStore.empty(lockFile)`, and leave persistence available for later successful writes rather than crashing client startup.

Inject the service into `SelectionOverlayRenderer` and every new `ExportWandScreen`. Logout clears transient command selection and export jobs only; it must not delete Store records.

- [ ] **Step 4: Run client tests and dedicated-server smoke**

```powershell
.\gradlew.bat test --tests "com.nebysse.minetomesh.client.*"
.\gradlew.bat runServerSmoke
```

Expected: client tests pass and the dedicated server exits successfully without loading client-only persistence classes.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/nebysse/minetomesh/client/MineToMeshClient.java src/test/java/com/nebysse/minetomesh/client/LockedSelectionClientPolicyTest.java
git commit -m "feat: persist locked selections per world"
```

### Task 7: Document and manually verify persistence lifecycle

**Files:**
- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`
- Modify: `src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java`

- [ ] **Step 1: Add failing documentation assertions**

Require current docs to mention all of:

```text
手持预览
锁定选区
跨重启
按存档或服务器隔离
维度不匹配时隐藏
返回原维度后恢复
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.DocumentationPolicyTest
```

Expected: missing lifecycle text causes failure.

- [ ] **Step 3: Update user and manual-test documentation**

Document independent toggle semantics, replacement and unlock behavior, config path, world/server isolation, dimension hiding, corruption quarantine, and the fact that no server payload or wand component stores the lock.

Add manual cases:

1. Lock, switch hotbar slot, put wand in chest, and verify the frame remains.
2. Restart client and reconnect; verify restoration.
3. Enter Nether; verify hidden. Return; verify restoration.
4. Join another server or world; verify no cross-profile frame.
5. Lock a new selection; verify old record is replaced.
6. Unlock; verify only current Profile is removed.

- [ ] **Step 4: Run final verification**

```powershell
.\gradlew.bat test
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat runServerSmoke
```

Expected: all commands succeed.

- [ ] **Step 5: Commit**

```powershell
git add README.md docs/testing/manual-client-matrix.md src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java
git commit -m "docs: explain persistent selection locks"
```

## Manual client checkpoint

Do not mark this plan complete until a real client confirms:

- lock survives item switching, chest storage, disconnect and process restart;
- the lock is absent in another world/server with the same dimension ID;
- dimension changes hide rather than erase the record;
- returning to the original dimension restores it;
- equal held/locked snapshots render once;
- a different held selection and locked selection render together;
- malformed config is quarantined without a crash;
- the three toggles remain visually contained at all supported GUI scales.
