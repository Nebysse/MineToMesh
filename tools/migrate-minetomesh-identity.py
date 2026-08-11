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
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
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
LEGACY_TOKENS = (
    "com.onecuber.mcgltf",
    "com/onecuber/mcgltf",
    "mcgltf:",
    "mcgltf_test",
    "/mcgltf",
    "mcgltf-exports",
    "mcgltf.serverSmoke",
)


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
        if not path.exists():
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
    lines: list[str] = []
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
    build_text = build_text.replace("mcgltf.serverSmoke", "minetomesh.serverSmoke")
    build.write_text(build_text, encoding="utf-8", newline="\n")

    settings = ROOT / "settings.gradle"
    settings_text = settings.read_text(encoding="utf-8")
    expected_project = "rootProject.name = 'mcgltf'"
    if expected_project not in settings_text:
        raise RuntimeError("Unexpected settings.gradle project name")
    settings.write_text(
        settings_text.replace(expected_project, "rootProject.name = 'MineToMesh'"),
        encoding="utf-8",
        newline="\n",
    )


def configure_testmod_metadata() -> None:
    path = ROOT / "src/testmod/resources/META-INF/neoforge.mods.toml"
    text = path.read_text(encoding="utf-8")
    text = text.replace('displayName="MC glTF Compatibility Fixtures"',
                        'displayName="MineToMesh Compatibility Fixtures"')
    text = text.replace("Development-only deterministic rendering fixtures for MC glTF Exporter.",
                        "Development-only deterministic rendering fixtures for MineToMesh.")
    text = text.replace('versionRange="[0.1.0,)"', 'versionRange="[0.5.1,)"')
    path.write_text(text, encoding="utf-8", newline="\n")


def legacy_residue() -> list[str]:
    failures: list[str] = []
    for file in text_files():
        text = file.read_text(encoding="utf-8")
        for token in LEGACY_TOKENS:
            if token in text:
                failures.append(f"{file.relative_to(ROOT)}: {token}")
    old_paths = [source for source, _ in DIRECTORY_MOVES] + [source for source, _ in FILE_MOVES]
    for relative in old_paths:
        if (ROOT / relative).exists():
            failures.append(f"legacy path still exists: {relative}")
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
        configure_build()
        configure_testmod_metadata()
        for file in text_files():
            rewrite(file)

    failures = legacy_residue()
    if failures:
        raise SystemExit("Legacy runtime identity remains:\n" + "\n".join(failures))


if __name__ == "__main__":
    main()
