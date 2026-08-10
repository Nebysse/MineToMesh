# MineToMesh 0.3.0 Permission and Border Revision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This repository has an explicit user constraint forbidding subagents, so execution must use superpowers:executing-plans inline.

**Goal:** Restrict workstation export grants to command-level administrators on servers while allowing all local singleplayer exports, and render every resizable workstation skin with an approximately eight-physical-pixel fixed border.

**Architecture:** Keep permission authority in the server-side `ExportRequestPayload` handler and expose a pure boolean policy for deterministic tests. Keep GUI scaling math in a pure client-side `WorkstationBorderPolicy`; `ExportWorkstationScreen` supplies the current Minecraft GUI scale and applies the returned logical border to the existing bounded nine-slice renderer.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.244, JUnit 5, Gradle ModDev

---

## File Map

- Modify `src/main/java/com/onecuber/mcgltf/network/WorkstationRequestPolicy.java`: pure export permission decision and stable rejection key.
- Modify `src/main/java/com/onecuber/mcgltf/network/WorkstationPayloads.java`: server-authoritative permission gate before name and bounds validation.
- Modify `src/test/java/com/onecuber/mcgltf/network/WorkstationRequestPolicyTest.java`: permission matrix.
- Modify `src/main/resources/assets/mcgltf/lang/zh_cn.json`: Chinese permission rejection text.
- Modify `src/main/resources/assets/mcgltf/lang/en_us.json`: English permission rejection text.
- Create `src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationBorderPolicy.java`: physical-to-logical border conversion and safe-center clamp.
- Create `src/test/java/com/onecuber/mcgltf/client/workstation/WorkstationBorderPolicyTest.java`: GUI Scale and small-texture matrix.
- Modify `src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java`: use eight physical pixels for panel, field, button, title and progress skins; leave separator/fill lines borderless.
- Modify `src/test/java/com/onecuber/mcgltf/client/workstation/WorkstationNineSliceTest.java`: enforce runtime border policy rather than fixed logical constants.

### Task 1: Server-authoritative export permission

- [ ] **Step 1: Add failing permission policy tests**

Extend `WorkstationRequestPolicyTest` with:

```java
@Test
void localSingleplayerBypassesExportPermission() {
    assertTrue(WorkstationRequestPolicy
            .validateExportPermission(true, false).accepted());
}

@Test
void dedicatedServerRequiresLevelTwoCommandPermission() {
    WorkstationRequestPolicy.Validation denied = WorkstationRequestPolicy
            .validateExportPermission(false, false);
    assertFalse(denied.accepted());
    assertEquals("mcgltf.error.workstation.no_export_permission", denied.reasonKey());
    assertTrue(WorkstationRequestPolicy
            .validateExportPermission(false, true).accepted());
}
```

- [ ] **Step 2: Run the policy tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.network.WorkstationRequestPolicyTest
```

Expected: compilation failure because `validateExportPermission` does not exist.

- [ ] **Step 3: Implement the pure policy**

Add to `WorkstationRequestPolicy`:

```java
public static Validation validateExportPermission(
        boolean localSingleplayer, boolean hasLevelTwoCommandPermission) {
    return localSingleplayer || hasLevelTwoCommandPermission
            ? Validation.accept()
            : Validation.reject("mcgltf.error.workstation.no_export_permission");
}
```

- [ ] **Step 4: Verify the policy tests turn GREEN**

Run the same focused Gradle command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Add a failing integration policy test**

Create or extend a source integration assertion to require `handleExportRequest` to call:

```java
WorkstationRequestPolicy.validateExportPermission(
        player.getServer().isSingleplayer(),
        player.createCommandSourceStack().hasPermission(2))
```

and to reject before `validateExportName`.

Run the test and verify it fails because the handler lacks the permission gate.

- [ ] **Step 6: Wire the permission gate into the Payload handler**

Immediately after `validStation` succeeds:

```java
WorkstationRequestPolicy.Validation permissionValidation =
        WorkstationRequestPolicy.validateExportPermission(
                player.getServer().isSingleplayer(),
                player.createCommandSourceStack().hasPermission(2));
if (!permissionValidation.accepted()) {
    sendReject(player, payload.stationPos(), permissionValidation.reasonKey());
    return;
}
```

Do not add this gate to `handleUpdateCoordinate` or `handleCaptureFeet`.

- [ ] **Step 7: Add localized rejection messages**

Add:

```json
"mcgltf.error.workstation.no_export_permission": "只有服务器管理员可以使用工作台导出"
```

and:

```json
"mcgltf.error.workstation.no_export_permission": "Only server administrators may export from this workstation"
```

- [ ] **Step 8: Run network and resource tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.network.*" --tests "com.onecuber.mcgltf.content.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit permission behavior**

```powershell
git add src/main/java/com/onecuber/mcgltf/network/WorkstationRequestPolicy.java src/main/java/com/onecuber/mcgltf/network/WorkstationPayloads.java src/test/java/com/onecuber/mcgltf/network src/main/resources/assets/mcgltf/lang
git commit -m "feat: restrict workstation exports to administrators"
```

### Task 2: Eight-physical-pixel nine-slice borders

- [ ] **Step 1: Write the failing border policy tests**

Create `WorkstationBorderPolicyTest`:

```java
@Test
void convertsEightPhysicalPixelsToNearestLogicalBorder() {
    assertEquals(8, WorkstationBorderPolicy.logicalBorder(8, 1.0, 64, 64, 64, 64));
    assertEquals(4, WorkstationBorderPolicy.logicalBorder(8, 2.0, 64, 64, 64, 64));
    assertEquals(3, WorkstationBorderPolicy.logicalBorder(8, 3.0, 64, 64, 64, 64));
    assertEquals(2, WorkstationBorderPolicy.logicalBorder(8, 4.0, 64, 64, 64, 64));
    assertEquals(1, WorkstationBorderPolicy.logicalBorder(8, 6.0, 64, 64, 64, 64));
}

@Test
void alwaysLeavesAtLeastOnePixelOfSourceAndDestinationCenter() {
    assertEquals(7, WorkstationBorderPolicy.logicalBorder(8, 1.0, 33, 16, 148, 16));
    assertEquals(2, WorkstationBorderPolicy.logicalBorder(8, 1.0, 5, 5, 5, 5));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.client.workstation.WorkstationBorderPolicyTest
```

Expected: compilation failure because `WorkstationBorderPolicy` does not exist.

- [ ] **Step 3: Implement the pure conversion policy**

Create:

```java
public final class WorkstationBorderPolicy {
    public static int logicalBorder(
            int physicalPixels, double guiScale,
            int sourceWidth, int sourceHeight,
            int destinationWidth, int destinationHeight) {
        if (physicalPixels < 0 || !Double.isFinite(guiScale) || guiScale <= 0.0) {
            throw new IllegalArgumentException("invalid border inputs");
        }
        if (physicalPixels == 0) {
            return 0;
        }
        int target = Math.max(1, (int) Math.round(physicalPixels / guiScale));
        int safeMaximum = Math.min(
                Math.min((sourceWidth - 1) / 2, (sourceHeight - 1) / 2),
                Math.min((destinationWidth - 1) / 2, (destinationHeight - 1) / 2));
        return Math.min(target, Math.max(0, safeMaximum));
    }

    private WorkstationBorderPolicy() {
    }
}
```

- [ ] **Step 4: Verify the policy test turns GREEN**

Run the same focused command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Add a failing Screen integration assertion**

Update `WorkstationNineSliceTest` to require:

```text
new NineSliceStyle(8)
WorkstationBorderPolicy.logicalBorder(
minecraft.getWindow().getGuiScale()
```

and reject per-role logical border constants `1`, `2`, `3`, or `4`.

Run the test and verify RED.

- [ ] **Step 6: Wire the runtime border policy into Screen rendering**

Change `NineSliceStyle` to store `physicalBorder`, return `new NineSliceStyle(8)` from panel/field/button/title/progress styles, and retain `new NineSliceStyle(0)` for borderless line/fill textures.

Inside `blitNineSlice`, replace direct border use with:

```java
int border = WorkstationBorderPolicy.logicalBorder(
        style.physicalBorder(),
        Minecraft.getInstance().getWindow().getGuiScale(),
        texture.width(), texture.height(), width, height);
int borderX = border;
int borderY = border;
```

The existing standard nine-slice source-region stretch remains unchanged.

- [ ] **Step 7: Run all workstation visual tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.client.workstation.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit border policy**

```powershell
git add src/main/java/com/onecuber/mcgltf/client/workstation src/test/java/com/onecuber/mcgltf/client/workstation
git commit -m "fix: keep workstation borders eight physical pixels"
```

### Task 3: Final verification and delivery

- [ ] **Step 1: Run full build**

```powershell
.\gradlew.bat clean test build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run server isolation and smoke sequentially**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.ServerClassIsolationTest
.\gradlew.bat runServerSmoke
```

Expected: both `BUILD SUCCESSFUL`; smoke output contains `MINETOMESH_SERVER_READY`.

- [ ] **Step 3: Inspect the production JAR**

Verify `build/libs/mcgltf-0.3.0.jar` contains all 77 workstation GUI slices, permission classes, Screen, Overlay renderer and both language files; verify it excludes testmod and `.superpowers` inputs.

- [ ] **Step 4: Run client visual matrix**

At GUI Scale 2, 3 and 4, inspect panel, input, export, cancel and selection toggle borders. Their physical border target is approximately 8 screen pixels; no UV wrapping, decorative tiling or whole-texture stretching may appear.

- [ ] **Step 5: Commit verification-only changes if any**

Use a focused commit message and do not amend the behavior commits.

- [ ] **Step 6: Push and stage the JAR**

Push the complete fast-forward commit range to `minetomesh/main`, verify remote HEAD, and deliver `build/libs/mcgltf-0.3.0.jar`.
