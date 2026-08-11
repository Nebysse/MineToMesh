# MineToMesh 0.5.1 Hard Identity Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. The project owner disabled subagent use. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Release MineToMesh 0.5.1 with runtime ID `minetomesh`, Java root package `com.nebysse.minetomesh`, new resource/test namespaces, `/minetomesh`, `minetomesh-exports`, and a GitHub Release containing `MineToMesh-0.5.1.jar`.

**Architecture:** Apply the migration as one deterministic, auditable namespace rewrite after first establishing failing filesystem and behavior contracts. Keep glTF format class names intact, rename only brand-bearing `McGltf*` types, and provide no compatibility aliases or missing mappings. Complete all local and merged-main verification before creating the immutable `v0.5.1` tag and GitHub Release.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.244, Gradle 9.2.1, JUnit 5, Python 3 migration utility, Git, GitHub CLI.

**Execution policy:** Work inline without subagents. Create an isolated release worktree from `main`; do not edit or tag directly from an unverified working tree. Visual and old-world behavior remain owner-reviewed; automated tests verify identity, structure, buildability, server isolation, archive contents, and remote release integrity.

---

## File Structure

### New files

- `tools/migrate-minetomesh-identity.py` — deterministic one-time migration utility with explicit path moves, brand-class renames, text allowlist, and legacy residue audit.
- `src/test/java/com/nebysse/minetomesh/IdentityMigrationContractTest.java` — target-namespace contract for build identity, source/resource roots, old-runtime residue, command source, output root, and service registration.
- `docs/releases/0.5.1.md` — exact GitHub Release Notes, including hard-migration warning.

### Moved source roots

- `src/main/java/com/onecuber/mcgltf/` → `src/main/java/com/nebysse/minetomesh/`
- `src/test/java/com/onecuber/mcgltf/` → `src/test/java/com/nebysse/minetomesh/`
- `src/testmod/java/com/onecuber/mcgltf/` → `src/testmod/java/com/nebysse/minetomesh/`

### Renamed brand-bearing Java files

- `src/main/java/com/nebysse/minetomesh/McGltf.java` → `src/main/java/com/nebysse/minetomesh/MineToMesh.java`
- `src/main/java/com/nebysse/minetomesh/client/McGltfClient.java` → `src/main/java/com/nebysse/minetomesh/client/MineToMeshClient.java`
- `src/main/java/com/nebysse/minetomesh/command/McGltfCommands.java` → `src/main/java/com/nebysse/minetomesh/command/MineToMeshCommands.java`
- `src/main/java/com/nebysse/minetomesh/content/McGltfContent.java` → `src/main/java/com/nebysse/minetomesh/content/MineToMeshContent.java`
- `src/test/java/com/nebysse/minetomesh/McGltfMetadataTest.java` → `src/test/java/com/nebysse/minetomesh/MineToMeshMetadataTest.java`
- `src/testmod/java/com/nebysse/minetomesh/testmod/McGltfTestMod.java` → `src/testmod/java/com/nebysse/minetomesh/testmod/MineToMeshTestMod.java`
- `src/testmod/java/com/nebysse/minetomesh/testmod/client/McGltfTestClient.java` → `src/testmod/java/com/nebysse/minetomesh/testmod/client/MineToMeshTestClient.java`

### Moved resource roots

- `src/main/resources/assets/mcgltf/` → `src/main/resources/assets/minetomesh/`
- `src/main/resources/data/mcgltf/` → `src/main/resources/data/minetomesh/`
- `src/testmod/resources/assets/mcgltf_test/` → `src/testmod/resources/assets/minetomesh_test/`
- `src/testmod/resources/META-INF/services/com.onecuber.mcgltf.backend.RenderBackendAdapter` → `src/testmod/resources/META-INF/services/com.nebysse.minetomesh.backend.RenderBackendAdapter`

### Modified build, runtime, tests, and documentation

- `gradle.properties` — version, Mod ID, Maven group.
- `build.gradle` — archive name and renamed NeoForge mod/testmod bindings.
- `settings.gradle` — Gradle project name.
- `src/main/templates/META-INF/neoforge.mods.toml` — generated identity text remains property-driven; description becomes new-brand-only.
- `src/testmod/resources/META-INF/neoforge.mods.toml` — `minetomesh_test` and dependency on `minetomesh`.
- `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java` — target export-root helper and `minetomesh-exports`.
- `src/main/java/com/nebysse/minetomesh/command/MineToMeshCommands.java` — `/minetomesh` root and confirmation command.
- `src/test/java/com/nebysse/minetomesh/job/DefaultExportPipelinePolicyTest.java` — output-root behavior.
- `README.md` — 0.5.1 install, command, output path, hard migration warning.
- `docs/testing/manual-client-matrix.md` — 0.5.1 package and manual hard-migration checks.
- all Java and testmod resource files returned by the migration utility — package/import/namespace literal rewrite.

---

## Task 0: Create the Release Worktree and Establish the Baseline

**Files:**

- No source modifications.
- Worktree: `D:\data\code\mcgltf\.worktrees\minetomesh-0.5.1`
- Branch: `release/minetomesh-0.5.1`

- [ ] **Step 1: Confirm main and remote release preconditions**

Run from `D:\data\code\mcgltf`:

```powershell
git status --short --branch
git log -3 --oneline
git fetch minetomesh main --tags
git ls-remote --tags minetomesh refs/tags/v0.5.1
gh auth status
gh release view v0.5.1 --repo Nebysse/MineToMesh 2>$null
if ($LASTEXITCODE -eq 0) { throw 'GitHub Release v0.5.1 already exists' }
```

Expected:

- Main has no tracked or untracked release changes.
- Local main contains specification commit `a9303f2`.
- `git ls-remote` prints no `v0.5.1` tag.
- `gh auth status` reports an authenticated account with repository write access.
- The explicit release-existence guard does not throw. Any existing tag or release is a blocker; do not overwrite it.

- [ ] **Step 2: Create an isolated worktree**

Use the `using-git-worktrees` flow. If no native worktree tool exists, run:

```powershell
git check-ignore -q .worktrees
if ($LASTEXITCODE -ne 0) { throw '.worktrees must be ignored' }
git worktree add .worktrees/minetomesh-0.5.1 -b release/minetomesh-0.5.1 main
```

Expected: branch and worktree are created without changing `main`.

- [ ] **Step 3: Run the clean baseline**

Run in the new worktree:

```powershell
.\gradlew.bat clean test build
```

Expected: BUILD SUCCESSFUL and current artifact `build/libs/mcgltf-0.5.0.jar` exists before migration.

---

## Task 1: Establish Failing 0.5.1 Identity and Output Contracts

**Files:**

- Create: `src/test/java/com/nebysse/minetomesh/IdentityMigrationContractTest.java`
- Modify: `src/test/java/com/onecuber/mcgltf/job/DefaultExportPipelinePolicyTest.java`

- [ ] **Step 1: Write the target identity contract test**

Create `IdentityMigrationContractTest.java` in the target package before moving production sources:

```java
package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class IdentityMigrationContractTest {
    private static final String LEGACY_ID = "mc" + "gltf";
    private static final String LEGACY_PACKAGE =
            "com.onecuber." + LEGACY_ID;

    @Test
    void buildIdentityDeclaresMineToMesh051() throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(
                projectRoot().resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertEquals("minetomesh", properties.getProperty("mod_id"));
        assertEquals("MineToMesh", properties.getProperty("mod_name"));
        assertEquals("0.5.1", properties.getProperty("mod_version"));
        assertEquals("com.nebysse.minetomesh",
                properties.getProperty("mod_group_id"));
        assertTrue(read("build.gradle").contains("archivesName = mod_name"));
        assertTrue(read("settings.gradle").contains(
                "rootProject.name = 'MineToMesh'"));
    }

    @Test
    void sourceAndResourceRootsUseOnlyNewNamespaces() throws Exception {
        Path root = projectRoot();
        assertTrue(Files.isDirectory(root.resolve(
                "src/main/java/com/nebysse/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve(
                "src/test/java/com/nebysse/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve(
                "src/testmod/java/com/nebysse/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve(
                "src/main/resources/assets/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve(
                "src/main/resources/data/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve(
                "src/testmod/resources/assets/minetomesh_test")));

        assertFalse(Files.exists(root.resolve(
                "src/main/java/com/onecuber/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve(
                "src/test/java/com/onecuber/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve(
                "src/testmod/java/com/onecuber/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve(
                "src/main/resources/assets/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve(
                "src/main/resources/data/" + LEGACY_ID)));
    }

    @Test
    void activeRuntimeTreesContainNoLegacyIdentity() throws Exception {
        for (String relative : List.of(
                "src/main/java",
                "src/main/resources",
                "src/test/java",
                "src/testmod/java",
                "src/testmod/resources")) {
            try (var files = Files.walk(projectRoot().resolve(relative))) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(IdentityMigrationContractTest::isText).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    assertFalse(text.contains(LEGACY_PACKAGE), file.toString());
                    assertFalse(text.contains(LEGACY_ID + ":"), file.toString());
                    assertFalse(text.contains(LEGACY_ID + "_test"), file.toString());
                    assertFalse(text.contains("/" + LEGACY_ID), file.toString());
                    assertFalse(text.contains(LEGACY_ID + "-exports"), file.toString());
                    assertFalse(text.contains(LEGACY_ID + ".serverSmoke"), file.toString());
                }
            }
        }
    }

    @Test
    void serviceProviderFileUsesTargetPackageName() {
        Path service = projectRoot().resolve(
                "src/testmod/resources/META-INF/services/"
                        + "com.nebysse.minetomesh.backend.RenderBackendAdapter");
        assertTrue(Files.isRegularFile(service));
        assertFalse(Files.exists(projectRoot().resolve(
                "src/testmod/resources/META-INF/services/" + LEGACY_PACKAGE
                        + ".backend.RenderBackendAdapter")));
    }

    private static boolean isText(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".json")
                || name.endsWith(".toml") || name.endsWith(".properties")
                || path.toString().replace('\\', '/').contains("/META-INF/services/");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
```

- [ ] **Step 2: Add a failing output-root behavior test**

Add to the existing `DefaultExportPipelinePolicyTest`:

```java
@Test
void resolvesExportsUnderMineToMeshDirectory() {
    Path gameDirectory = Path.of("game");

    assertEquals(
            gameDirectory.resolve("minetomesh-exports"),
            DefaultExportPipeline.exportRoot(gameDirectory));
}
```

Add imports for `java.nio.file.Path` and the existing `assertEquals` static import.

- [ ] **Step 3: Run the tests and verify RED**

```powershell
.\gradlew.bat test `
  --tests com.nebysse.minetomesh.IdentityMigrationContractTest `
  --tests com.onecuber.mcgltf.job.DefaultExportPipelinePolicyTest
```

Expected failure causes:

- `mod_id` remains `mcgltf` and version remains `0.5.0`.
- target Java/resource roots do not exist.
- legacy runtime identity is still present.
- `DefaultExportPipeline.exportRoot(Path)` does not exist.

Do not proceed unless failures are caused by these missing migration behaviors.

- [ ] **Step 4: Commit only the red contracts**

```powershell
git add -- src/test/java/com/nebysse/minetomesh/IdentityMigrationContractTest.java src/test/java/com/onecuber/mcgltf/job/DefaultExportPipelinePolicyTest.java
git commit -m "test: define MineToMesh 0.5.1 identity contract"
```

---

## Task 2: Apply the Complete Java, Resource, Build, and Runtime Migration

**Files:**

- Create: `tools/migrate-minetomesh-identity.py`
- Move: all roots and brand-bearing files listed in “File Structure”.
- Modify: every tracked text file under `src/main`, `src/test`, and `src/testmod` selected by the migration utility.
- Modify: `build.gradle`
- Modify: `gradle.properties`
- Modify: `settings.gradle`
- Modify: `tools/generate-export-wand-texture.py`
- Modify: `tools/process-workstation-assets.py`
- Modify: `src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java`

- [ ] **Step 1: Create the deterministic migration utility**

Create `tools/migrate-minetomesh-identity.py`:

```python
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

DIRECTORY_MOVES = (
    ("src/main/java/com/onecuber/mcgltf", "src/main/java/com/nebysse/minetomesh"),
    ("src/test/java/com/onecuber/mcgltf", "src/test/java/com/nebysse/minetomesh"),
    ("src/testmod/java/com/onecuber/mcgltf", "src/testmod/java/com/nebysse/minetomesh"),
    ("src/main/resources/assets/mcgltf", "src/main/resources/assets/minetomesh"),
    ("src/main/resources/data/mcgltf", "src/main/resources/data/minetomesh"),
    ("src/testmod/resources/assets/mcgltf_test", "src/testmod/resources/assets/minetomesh_test"),
)

FILE_MOVES = (
    ("src/main/java/com/nebysse/minetomesh/McGltf.java",
     "src/main/java/com/nebysse/minetomesh/MineToMesh.java"),
    ("src/main/java/com/nebysse/minetomesh/client/McGltfClient.java",
     "src/main/java/com/nebysse/minetomesh/client/MineToMeshClient.java"),
    ("src/main/java/com/nebysse/minetomesh/command/McGltfCommands.java",
     "src/main/java/com/nebysse/minetomesh/command/MineToMeshCommands.java"),
    ("src/main/java/com/nebysse/minetomesh/content/McGltfContent.java",
     "src/main/java/com/nebysse/minetomesh/content/MineToMeshContent.java"),
    ("src/test/java/com/nebysse/minetomesh/McGltfMetadataTest.java",
     "src/test/java/com/nebysse/minetomesh/MineToMeshMetadataTest.java"),
    ("src/testmod/java/com/nebysse/minetomesh/testmod/McGltfTestMod.java",
     "src/testmod/java/com/nebysse/minetomesh/testmod/MineToMeshTestMod.java"),
    ("src/testmod/java/com/nebysse/minetomesh/testmod/client/McGltfTestClient.java",
     "src/testmod/java/com/nebysse/minetomesh/testmod/client/MineToMeshTestClient.java"),
    ("src/testmod/resources/META-INF/services/com.onecuber.mcgltf.backend.RenderBackendAdapter",
     "src/testmod/resources/META-INF/services/com.nebysse.minetomesh.backend.RenderBackendAdapter"),
)

TEXT_ROOTS = (
    "src/main",
    "src/test",
    "src/testmod",
    "tools/generate-export-wand-texture.py",
    "tools/process-workstation-assets.py",
)

REPLACEMENTS = (
    ("com.onecuber.mcgltf", "com.nebysse.minetomesh"),
    ("com/onecuber/mcgltf", "com/nebysse/minetomesh"),
    ("McGltf", "MineToMesh"),
    ("mcgltf_test", "minetomesh_test"),
    ("mcgltf.serverSmoke", "minetomesh.serverSmoke"),
    ("mcgltf-exports", "minetomesh-exports"),
    ("mcgltf", "minetomesh"),
    ("0.5.0", "0.5.1"),
)

TEXT_SUFFIXES = {".java", ".json", ".toml", ".properties", ".py"}


def move(source: str, target: str) -> None:
    source_path = ROOT / source
    target_path = ROOT / target
    if target_path.exists() and not source_path.exists():
        return
    if not source_path.exists():
        raise RuntimeError(f"Missing migration source: {source}")
    target_path.parent.mkdir(parents=True, exist_ok=True)
    if source_path.is_dir() and target_path.is_dir():
        for child in source_path.iterdir():
            destination = target_path / child.name
            if destination.exists():
                raise RuntimeError(f"Migration target child already exists: {destination}")
            shutil.move(str(child), str(destination))
        source_path.rmdir()
        return
    if target_path.exists():
        raise RuntimeError(f"Migration target already exists: {target}")
    shutil.move(str(source_path), str(target_path))


def text_files() -> list[Path]:
    result: list[Path] = []
    for relative in TEXT_ROOTS:
        path = ROOT / relative
        if path.is_file():
            result.append(path)
            continue
        for file in path.rglob("*"):
            if not file.is_file():
                continue
            if file.suffix in TEXT_SUFFIXES or "/META-INF/services/" in file.as_posix():
                result.append(file)
    return sorted(set(result))


def rewrite(file: Path) -> None:
    text = file.read_text(encoding="utf-8")
    migrated = text
    for old, new in REPLACEMENTS:
        migrated = migrated.replace(old, new)
    if migrated != text:
        file.write_text(migrated, encoding="utf-8", newline="\n")


def configure_build() -> None:
    properties = ROOT / "gradle.properties"
    text = properties.read_text(encoding="utf-8")
    values = {
        "mod_id": "minetomesh",
        "mod_name": "MineToMesh",
        "mod_version": "0.5.1",
        "mod_group_id": "com.nebysse.minetomesh",
    }
    lines = []
    for line in text.splitlines():
        key = line.split("=", 1)[0]
        lines.append(f"{key}={values[key]}" if key in values else line)
    properties.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")

    build = ROOT / "build.gradle"
    build_text = build.read_text(encoding="utf-8")
    expected = "archivesName = mod_id"
    if expected not in build_text:
        raise RuntimeError("Unexpected build.gradle archive contract")
    build_text = build_text.replace(expected, "archivesName = mod_name")
    build_text = build_text.replace("mcgltf_test", "minetomesh_test")
    build.write_text(build_text, encoding="utf-8", newline="\n")

    settings = ROOT / "settings.gradle"
    settings_text = settings.read_text(encoding="utf-8")
    settings.write_text(
        settings_text.replace("rootProject.name = 'mcgltf'",
                              "rootProject.name = 'MineToMesh'"),
        encoding="utf-8", newline="\n")


def legacy_residue() -> list[str]:
    failures: list[str] = []
    for file in text_files():
        if file.name == Path(__file__).name:
            continue
        text = file.read_text(encoding="utf-8")
        for token in ("com.onecuber.mcgltf", "mcgltf:", "mcgltf_test",
                      "/mcgltf", "mcgltf-exports", "mcgltf.serverSmoke"):
            if token in text:
                failures.append(f"{file.relative_to(ROOT)}: {token}")
    return failures


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.apply == args.check:
        parser.error("choose exactly one of --apply or --check")

    if args.apply:
        for source, target in DIRECTORY_MOVES:
            move(source, target)
        for source, target in FILE_MOVES:
            move(source, target)
        for file in text_files():
            if file.resolve() != Path(__file__).resolve():
                rewrite(file)
        configure_build()

    failures = legacy_residue()
    if failures:
        raise SystemExit("Legacy runtime identity remains:\n" + "\n".join(failures))


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Apply the migration exactly once**

```powershell
python tools/migrate-minetomesh-identity.py --apply
python tools/migrate-minetomesh-identity.py --check
git status --short
```

Expected: Git reports moved Java/resource roots, renamed `MineToMesh*` files, and modifications throughout active source/test trees. `--check` exits 0.

If `--apply` reports an unexpected source/target state, diagnose the exact path instead of relaxing the guard.

- [ ] **Step 3: Implement the tested export-root seam**

In migrated `DefaultExportPipeline.java`, replace the inline root construction:

```java
Path exportRoot = exportRoot(minecraft.gameDirectory.toPath());
```

Add beside the existing policy helpers:

```java
static Path exportRoot(Path gameDirectory) {
    return Objects.requireNonNull(gameDirectory, "gameDirectory")
            .resolve("minetomesh-exports");
}
```

- [ ] **Step 4: Verify brand entry types and exact command literals**

Confirm migrated `MineToMesh.java` has:

```java
@Mod(MineToMesh.MOD_ID)
public final class MineToMesh {
    public static final String MOD_ID = "minetomesh";
    public static final String DISPLAY_NAME = "MineToMesh";
    public static final String VERSION = "0.5.1";

    public MineToMesh(IEventBus modBus) {
        MineToMeshContent.register(modBus);
        WandPayloads.register(modBus);
        NeoForge.EVENT_BUS.addListener(WandInteractionHandler::onLeftClickBlock);
    }
}
```

Confirm `MineToMeshCommands.register` starts with:

```java
dispatcher.register(Commands.literal("minetomesh")
```

and confirmation text starts with:

```java
String command = "/minetomesh export "
```

Do not register an `mcgltf` alias.

- [ ] **Step 5: Verify NeoForge main/testmod configuration**

`build.gradle` must contain:

```groovy
base {
    archivesName = mod_name
}
```

and dynamic references to `mod_id` now resolve to `minetomesh`. The testmod block must be:

```groovy
minetomesh_test {
    sourceSet(sourceSets.testmod)
}
```

Update `src/testmod/resources/META-INF/neoforge.mods.toml` to exact identities:

```toml
[[mods]]
modId="minetomesh_test"
version="0.1.0"
displayName="MineToMesh Compatibility Fixtures"
authors="OneCuber"
description='''Development-only deterministic rendering fixtures for MineToMesh.'''

[[dependencies.minetomesh_test]]
modId="minetomesh"
type="required"
versionRange="[0.5.1,)"
ordering="AFTER"
side="CLIENT"
```

Keep the existing Minecraft and NeoForge dependency values and `CLIENT` sides, but rename their table headers to `dependencies.minetomesh_test`.

- [ ] **Step 6: Run target contracts and compile all source sets**

```powershell
.\gradlew.bat test `
  --tests com.nebysse.minetomesh.IdentityMigrationContractTest `
  --tests com.nebysse.minetomesh.job.DefaultExportPipelinePolicyTest `
  --tests com.nebysse.minetomesh.MineToMeshMetadataTest
.\gradlew.bat compileJava compileTestJava compileTestmodJava
```

Expected: BUILD SUCCESSFUL. The old package test selectors must no longer resolve.

- [ ] **Step 7: Run active-source residue audit**

```powershell
rg -n "com\.onecuber\.mcgltf|mcgltf:|mcgltf_test|/mcgltf|mcgltf-exports|mcgltf\.serverSmoke" src build.gradle gradle.properties settings.gradle
```

Expected: no output. Historical `docs/superpowers` files are deliberately outside this scan.

- [ ] **Step 8: Commit the runtime migration**

```powershell
git add -- build.gradle gradle.properties settings.gradle src tools/migrate-minetomesh-identity.py tools/generate-export-wand-texture.py tools/process-workstation-assets.py
git diff --cached --check
git diff --cached --stat
git commit -m "refactor: migrate runtime identity to MineToMesh"
```

---

## Task 3: Update Current Documentation and Release Notes

**Files:**

- Modify: `src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java`
- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`
- Create: `docs/releases/0.5.1.md`

- [ ] **Step 1: Write failing documentation contracts**

Change `DocumentationPolicyTest.readmeDocumentsTheWandReleaseAndMigration` to require:

```java
for (String fragment : List.of(
        "MineToMesh-0.5.1.jar",
        "客户端和服务端",
        "导出魔杖",
        "/minetomesh",
        ".minecraft/minetomesh-exports/",
        "minetomesh:export_wand",
        "com.nebysse.minetomesh",
        "不提供 Missing Mapping",
        "升级前请备份世界")) {
    assertTrue(readme.contains(fragment),
            "README must mention: " + fragment);
}
assertFalse(readme.contains("mcgltf-0.5.0.jar"));
```

Change `manualMatrixCoversTheExactWandClosure` to additionally require:

```java
for (String fragment : List.of(
        "MineToMesh 0.5.1",
        "MineToMesh-0.5.1.jar",
        "/minetomesh",
        "minetomesh-exports",
        "旧魔杖缺失",
        "旧导出目录保留")) {
    assertTrue(matrix.contains(fragment),
            "manual matrix must cover: " + fragment);
}
```

- [ ] **Step 2: Run documentation tests and verify RED**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.DocumentationPolicyTest
```

Expected: FAIL because README and the manual matrix still describe 0.5.0/current old paths.

- [ ] **Step 3: Update README current-release identity**

Apply these exact current-release facts:

```markdown
MineToMesh 0.5.1 使用内部 Mod ID `minetomesh`，Java 包为 `com.nebysse.minetomesh`。
```

Installation must name `MineToMesh-0.5.1.jar`. Command examples must use:

```text
/minetomesh pos1
/minetomesh pos2
/minetomesh export <名称>
/minetomesh export <名称> confirm
/minetomesh status
/minetomesh cancel
```

Output root must be:

```text
.minecraft/minetomesh-exports/
```

Add a dedicated warning:

```markdown
## 0.5.1 硬迁移警告

0.5.1 将 Mod ID 从 `mcgltf` 改为 `minetomesh`，注册项同步变为 `minetomesh:*`，包括 `minetomesh:export_wand`。Java 包改为 `com.nebysse.minetomesh`。本版本不提供 Missing Mapping，不注册 `/mcgltf` 别名，也不迁移旧魔杖数据。旧世界中的 `mcgltf:*` 物品可能缺失，升级前请备份世界。

旧 `.minecraft/mcgltf-exports/` 目录不会被移动或删除；0.5.1 的新导出写入 `.minecraft/minetomesh-exports/`。
```

- [ ] **Step 4: Update the manual matrix**

Change heading, target JAR, and candidate section to 0.5.1. Add owner-only rows:

```markdown
| 新命令根 | 执行 `/minetomesh status`，再尝试 `/mcgltf status` | 新命令可用，旧命令未注册 |
| 新导出目录 | 执行一次新导出 | 结果只写入 `.minecraft/minetomesh-exports/` |
| 旧导出目录保留 | 升级前创建 `.minecraft/mcgltf-exports/` 并放入标记文件 | 启动与导出后旧目录、标记文件原样保留 |
| 旧魔杖缺失 | 备份后用含 `mcgltf:export_wand` 的旧世界启动 0.5.1 | 允许出现缺失注册项；世界文件不被 MineToMesh 主动迁移 |
```

Mark the old-world row destructive and require a copied world, never the only world save.

- [ ] **Step 5: Create exact GitHub Release Notes**

Create `docs/releases/0.5.1.md`:

```markdown
# MineToMesh 0.5.1

## Identity migration

- Mod ID is now `minetomesh`.
- Java package is now `com.nebysse.minetomesh`.
- The mod file is now `MineToMesh-0.5.1.jar`.
- Commands now start with `/minetomesh`.
- New exports are written to `.minecraft/minetomesh-exports/`.

## Export improvements included

- Resolves connected textures from actual atlas UVs, including Create 6.x connected sheets.
- Coalesces vanilla grass side overlays into `selection/grass_side_overlay`.
- Isolates wand text input from inventory, movement, hotbar, and mod shortcuts.

## Breaking migration warning

This is a hard identity migration. There is no Missing Mapping, `mcgltf:*` compatibility namespace, or `/mcgltf` command alias. Old wand items and data components may be missing after upgrading. Back up worlds before installing 0.5.1.

Existing `.minecraft/mcgltf-exports/` folders are not moved or deleted.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.244 or newer in the declared 1.21.1 range
- Install the same MineToMesh version on client and server
```

- [ ] **Step 6: Run documentation tests and commit**

```powershell
.\gradlew.bat test --tests com.nebysse.minetomesh.DocumentationPolicyTest --tests com.nebysse.minetomesh.MineToMeshMetadataTest
git add -- README.md docs/testing/manual-client-matrix.md docs/releases/0.5.1.md src/test/java/com/nebysse/minetomesh/DocumentationPolicyTest.java
git diff --cached --check
git commit -m "docs: prepare MineToMesh 0.5.1 release"
```

Expected: BUILD SUCCESSFUL and documentation commit created.

---

## Task 4: Full Mechanical Verification and Production JAR Audit

**Files:**

- No planned production changes.
- If a failure exposes a real migration omission, first add or tighten the closest failing test, then make the smallest correction and commit it separately.

- [ ] **Step 1: Run the migration utility audit and complete test suite**

```powershell
python tools/migrate-minetomesh-identity.py --check
.\gradlew.bat clean test build
```

Expected: BUILD SUCCESSFUL and `build/libs/MineToMesh-0.5.1.jar` exists.

- [ ] **Step 2: Run dedicated-server isolation smoke test**

```powershell
.\gradlew.bat runServerSmoke
```

Expected mod list:

```text
MineToMesh Compatibility Fixtures 0.1.0 (minetomesh_test)
MineToMesh 0.5.1 (minetomesh)
```

Expected output contains `MINETOMESH_SERVER_READY` and no client-class linkage error.

- [ ] **Step 3: Audit archive identity and forbidden residue**

```powershell
$jar = Resolve-Path build\libs\MineToMesh-0.5.1.jar
$entries = jar tf $jar
$entries | Select-String '^com/nebysse/minetomesh/' | Select-Object -First 5
$entries | Select-String '^assets/minetomesh/' | Select-Object -First 5
$entries | Select-String '^data/minetomesh/' | Select-Object -First 5
$forbidden = $entries | Select-String 'com/onecuber/mcgltf|assets/mcgltf/|data/mcgltf/|mcgltf_test|testmod|\.objpart$|superpowers'
if ($forbidden) { $forbidden; throw 'Forbidden legacy or test content in production JAR' }
```

Expected: required prefixes are present and forbidden scan is empty.

- [ ] **Step 4: Extract and verify metadata**

```powershell
Remove-Item -Recurse -Force build\tmp\release-audit -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build\tmp\release-audit | Out-Null
Push-Location build\tmp\release-audit
jar xf $jar META-INF/neoforge.mods.toml
Pop-Location
$metadata = Get-Content build\tmp\release-audit\META-INF\neoforge.mods.toml -Raw
foreach ($fragment in @('modId="minetomesh"', 'version="0.5.1"', 'displayName="MineToMesh"', 'side="BOTH"')) {
    if (-not $metadata.Contains($fragment)) { throw "Missing metadata: $fragment" }
}
```

Expected: all required metadata fragments are present.

- [ ] **Step 5: Audit bytecode dependencies and calculate hash**

```powershell
jdeps --multi-release 21 build\libs\MineToMesh-0.5.1.jar | Select-String 'com\.onecuber\.mcgltf|com\.simibubi\.create|net\.createmod\.catnip'
Get-FileHash build\libs\MineToMesh-0.5.1.jar -Algorithm SHA256
```

Expected: dependency scan prints no output. Record the SHA-256 for release verification.

- [ ] **Step 6: Verify release branch cleanliness**

```powershell
git diff --check
git status --short --branch
git log -5 --oneline
```

Expected: clean `release/minetomesh-0.5.1` worktree with migration and documentation commits.

---

## Task 5: Merge Into Main and Reverify the Exact Merge Result

**Files:**

- No source modifications planned.

- [ ] **Step 1: Confirm the release branch is based on current remote main**

From the main checkout:

```powershell
git fetch minetomesh main --tags
git merge-base --is-ancestor minetomesh/main release/minetomesh-0.5.1
```

Expected: exit 0. If remote main advanced beyond the branch base, merge remote main into the release branch, rerun Task 4, and only then continue.

- [ ] **Step 2: Merge with an explicit release commit**

```powershell
git checkout main
git merge --no-ff release/minetomesh-0.5.1 -m "merge: release MineToMesh 0.5.1"
```

Expected: conflict-free merge. Do not create the tag yet.

- [ ] **Step 3: Rebuild and rerun server smoke on merged main**

```powershell
.\gradlew.bat clean test build
.\gradlew.bat runServerSmoke
```

Expected: both commands succeed, the mod list reports 0.5.1 identities, and `MINETOMESH_SERVER_READY` appears.

- [ ] **Step 4: Recalculate canonical main artifact hash**

```powershell
$jar = Resolve-Path build\libs\MineToMesh-0.5.1.jar
$hash = (Get-FileHash $jar -Algorithm SHA256).Hash
Write-Output "RELEASE_SHA256=$hash"
git status --short --branch
```

Expected: clean main and one canonical hash. Use this main-built JAR for GitHub Release, not the earlier worktree copy.

---

## Task 6: Push Main, Tag v0.5.1, and Create GitHub Release

**Files:**

- Deliver: `build/libs/MineToMesh-0.5.1.jar`
- Release notes: `docs/releases/0.5.1.md`

- [ ] **Step 1: Push and verify main**

```powershell
$head = (git rev-parse HEAD).Trim()
git push minetomesh main:main
$remoteMain = git ls-remote minetomesh refs/heads/main
if (-not $remoteMain.StartsWith($head)) {
    throw 'Remote main does not match local release commit'
}
```

Expected: remote main equals local HEAD.

- [ ] **Step 2: Create and push the annotated tag**

```powershell
git tag -a v0.5.1 -m "MineToMesh 0.5.1"
git push minetomesh refs/tags/v0.5.1
$head = (git rev-parse HEAD).Trim()
$tagCommit = (git rev-list -n 1 v0.5.1).Trim()
if ($tagCommit -ne $head) { throw 'v0.5.1 does not point to release main' }
```

Expected: annotated tag exists remotely and resolves to the merged main commit.

- [ ] **Step 3: Create the GitHub Release and upload the canonical JAR**

```powershell
gh release create v0.5.1 `
  build\libs\MineToMesh-0.5.1.jar `
  --repo Nebysse/MineToMesh `
  --title "MineToMesh 0.5.1" `
  --notes-file docs\releases\0.5.1.md `
  --verify-tag
```

Expected: GitHub returns the new release URL. Do not use `--latest=false`; 0.5.1 becomes the current latest release.

- [ ] **Step 4: Verify remote release metadata and asset hash**

```powershell
$release = gh release view v0.5.1 --repo Nebysse/MineToMesh --json tagName,name,isDraft,isPrerelease,url,assets | ConvertFrom-Json
if ($release.tagName -ne 'v0.5.1' -or $release.name -ne 'MineToMesh 0.5.1'
        -or $release.isDraft -or $release.isPrerelease) {
    throw 'Unexpected GitHub Release metadata'
}
if (($release.assets | Where-Object name -eq 'MineToMesh-0.5.1.jar').Count -ne 1) {
    throw 'Release JAR asset is missing or duplicated'
}

Remove-Item -Recurse -Force build\tmp\release-download -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build\tmp\release-download | Out-Null
gh release download v0.5.1 --repo Nebysse/MineToMesh `
  --pattern MineToMesh-0.5.1.jar `
  --dir build\tmp\release-download
$localHash = (Get-FileHash build\libs\MineToMesh-0.5.1.jar -Algorithm SHA256).Hash
$remoteHash = (Get-FileHash build\tmp\release-download\MineToMesh-0.5.1.jar -Algorithm SHA256).Hash
if ($localHash -ne $remoteHash) { throw 'GitHub asset hash differs from local JAR' }
Write-Output "RELEASE_URL=$($release.url)"
Write-Output "SHA256=$localHash"
```

Expected: release is public, non-draft, non-prerelease, has exactly one JAR, and downloaded hash equals local hash.

- [ ] **Step 5: Stage the release artifact for the project owner**

Deliver `build/libs/MineToMesh-0.5.1.jar` through `stage_files` and report:

```text
Mechanical verification: passed
Remote main: verified
Tag v0.5.1: verified
GitHub Release: verified
Old-world hard-migration behavior: pending owner review
```

---

## Task 7: Clean Up the Release Branch and Preserve Owner Assets

**Files:**

- No source modifications.

- [ ] **Step 1: Confirm the release branch is merged**

```powershell
git merge-base --is-ancestor release/minetomesh-0.5.1 main
```

Expected: exit 0.

- [ ] **Step 2: Remove only the owned release worktree and branch**

From `D:\data\code\mcgltf`:

```powershell
git worktree remove D:\data\code\mcgltf\.worktrees\minetomesh-0.5.1
git worktree prune
git branch -d release/minetomesh-0.5.1
```

If Windows reports the directory is in use, inspect only processes whose command lines contain the release worktree path, stop leftover Gradle/smoke-test wrappers created by this execution, then remove the empty directory. Do not touch `.superpowers/` owner assets.

- [ ] **Step 3: Final local/remote state audit**

```powershell
git status --short --branch
git worktree list
git branch -vv
git ls-remote minetomesh refs/heads/main refs/tags/v0.5.1
gh release view v0.5.1 --repo Nebysse/MineToMesh --json url,tagName,name,assets
```

Expected:

- Main is clean.
- Release worktree and branch are gone.
- Remote main and `v0.5.1` resolve to the release commit.
- GitHub Release remains visible with `MineToMesh-0.5.1.jar`.
