# MineToMesh Coordinate Handedness Fix Design

**Date:** 2026-08-11
**Status:** Approved design, pending written-spec review
**Scope:** Correct intrinsic mirror reflection in both glTF and OBJ exports

## Problem

MineToMesh 0.5.1 reflects every captured position and normal across the export Z axis:

```text
position: (x, y, z) -> (x - ox, y - oy, -(z - oz))
normal:   (x, y, z) -> (x, y, -z)
```

Both topology writers then reverse triangle and polygon winding to compensate for that reflection. After Blender imports the glTF and converts its Y-up coordinate system to Blender's Z-up system, the resulting scene has opposite handedness. In an asymmetric Minecraft selection this is observed as an X-axis left/right mirror.

The existing unit tests encode this reflected behavior as intentional, so they pass while the exported spatial relationship is wrong.

## Root Cause

Minecraft world coordinates use X and Z as the horizontal axes and Y as the vertical axis. glTF 2.0 uses a right-handed, Y-up coordinate system. Relative Minecraft coordinates can therefore be stored directly in glTF as `(dx, dy, dz)` without a reflection.

The current export transform has matrix:

```text
diag(1, 1, -1)
```

Its determinant is `-1`, which changes handedness. Reversing winding repairs front-face classification, but it cannot undo the mirrored spatial arrangement of vertices.

Blender's glTF importer performs the required axis rotation from glTF Y-up to Blender Z-up. With reflection removed, the effective Blender mapping is:

```text
Minecraft +X -> Blender +X
Minecraft +Y -> Blender +Z
Minecraft +Z -> Blender -Y
```

This mapping has determinant `+1`; it is a rotation rather than a mirror.

Reference: Khronos glTF 2.0 Specification, coordinate system and mesh primitive winding requirements: <https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html>

## Selected Approach

Correct the shared captured-scene coordinate contract and restore source winding in both output formats.

### Coordinate transform

`CoordinateTransform.position` shall subtract the origin without negating an axis:

```text
(dx, dy, dz) = (x - ox, y - oy, z - oz)
```

`CoordinateTransform.normal` shall preserve all three components and normalize the result. A zero vector shall retain the existing `UP` fallback.

### glTF topology

`TopologyConverter` shall preserve source front-face orientation:

- Triangle: `0, 1, 2`
- Quad triangles: `0, 1, 2` and `0, 2, 3`
- Triangle fan: center followed by outer vertices in source order
- Triangle strip: source order and parity, without reflection-specific reversal or duplicate parity vertices
- Lines and line strips: unchanged

Incomplete primitive diagnostics remain unchanged.

### OBJ topology

`ObjTopologyConverter` shall emit source polygon orientation:

- Triangle: `0, 1, 2`
- Quad: `0, 1, 2, 3`
- Fan and strip faces: source orientation
- Line output: unchanged

OBJ and glTF will therefore describe the same right-handed captured scene. Blender's importer settings may rotate OBJ axes, but MineToMesh will no longer introduce an intrinsic reflection into the file.

## Data Flow

```text
Minecraft renderer vertices
  -> relative position and normalized normal, no reflection
  -> shared PrimitiveData
  -> glTF source-order triangle indices
  -> OBJ source-order polygon indices
  -> Blender import axis rotation
  -> right-handed, non-mirrored scene
```

UVs, vertex colors, materials, hierarchy, origin selection, unit scale, texture extraction, entity filtering, and export directory behavior are outside this change.

## TDD Contract

The existing reflected-behavior tests will be changed before production code so that they fail against 0.5.1 behavior.

### Coordinate tests

- An offset point preserves positive X and positive Z after origin subtraction.
- A positive-Z normal remains positive Z and is normalized.
- Zero normal still falls back to positive Y.

### glTF topology tests

- Quads and triangles preserve source winding.
- Fans preserve source outer-vertex order.
- Strips preserve source parity without reflection-only duplicate indices.
- Incomplete-tail diagnostics remain unchanged.

### OBJ topology tests

- Quads preserve four-vertex source order.
- Fans and strips preserve source orientation across separate streams.
- Lines remain unchanged.

The red phase must fail through assertion differences, not compilation errors.

## Verification

Automated verification:

1. Targeted coordinate and topology tests.
2. Full `clean test build`.
3. Dedicated-server `runServerSmoke` to guard client/server isolation.
4. Production JAR audit for required resources and forbidden test content.

Manual owner verification:

1. Export an asymmetric selection with an unmistakable east/west marker.
2. Import both glTF and OBJ into Blender.
3. Confirm Minecraft +X corresponds to Blender +X.
4. Confirm texturing, front faces, normals, and object hierarchy remain correct.
5. Compare glTF and OBJ spatial orientation side by side.

Automated tests establish mathematical handedness and index order; they do not replace the Blender visual decision.

## Compatibility

Previously exported files remain unchanged on disk and retain their mirrored geometry. Correcting an old result requires re-exporting it with the fixed build.

The fix changes output geometry semantics and does not add a legacy-coordinate toggle. Runtime Mod ID, Java package, command, saved wand data, network protocol, and export root remain unchanged.

## Release Boundary

This design covers implementation and verification only. Version bump, tag creation, and GitHub Release publication require a separate explicit release decision after the fix passes automated and manual acceptance.
