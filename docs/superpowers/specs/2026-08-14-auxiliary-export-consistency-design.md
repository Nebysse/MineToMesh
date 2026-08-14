# MineToMesh Auxiliary Export Consistency Design

**Date:** 2026-08-14
**Status:** Approved
**Scope:** Remove residual reflected coordinates from auxiliary capture paths and globally batch dynamic block-entity geometry by exact material in glTF and OBJ

## Problem 1: Residual Reflection in Auxiliary Geometry

MineToMesh 1.0.0 removed the shared `Z -> -Z` reflection from `CoordinateTransform` and restored source winding in glTF and OBJ. Static baked block models and fluids now preserve right-handed file-space coordinates.

Three auxiliary paths retained the old reflection logic:

- `BlockEntityCapture.blockEntityPose`
- `EntityCapture.entityPose` and `EntityCapture.localPosition`
- block/entity placeholder bound construction

The block-entity path currently applies:

```text
translation Z = -(world Z - selection minimum Z)
scale = (1, 1, -1)
```

The scale matrix has determinant `-1`. Create dynamic components are rendered through block-entity renderers such as `KineticBlockEntityRenderer`, which ultimately emit transformed vertices through a `PoseStack` and `VertexConsumer`. MineToMesh therefore reflects those vertices before writing them.

Blender's glTF importer rotates glTF Y-up coordinates into Blender Z-up coordinates. Under the established mapping:

```text
Minecraft +X -> Blender +X
Minecraft +Y -> Blender +Z
Minecraft +Z -> Blender -Y
```

A file-space Z reflection appears as a Blender Y reflection. Because 1.0.0 no longer reverses topology, the residual reflection also disagrees with source winding and produces inward-facing normals/front faces on affected geometry such as Create belts.

## Coordinate Decision

All capture channels shall use one right-handed relative coordinate contract:

```text
local = (world X - origin X, world Y - origin Y, world Z - origin Z)
```

Base renderer replay poses shall contain translation only. MineToMesh shall not inject a negative scale. Renderer-owned rotations and scales remain untouched.

The fix covers:

- dynamic block entities
- ordinary entities, including optional players
- entity fallback bounds
- block fallback bounds
- `extras.localPosition`

Static blocks, fluids, UVs, materials, animation snapshot time, entity filtering, and renderer selection are unchanged.

## Problem 2: One Blender Object per Dynamic Block Entity

Each block entity currently creates a position-specific `CapturedNode`:

```text
<registry id>/<x>,<y>,<z>
```

`GltfDocumentBuilder` creates one glTF mesh and node for every non-overlay captured node. `StreamingObjSession` writes one `o` declaration for every node. Ten connected belts therefore import as ten Blender objects even when all primitives share the same `MaterialKey`.

Material de-duplication does not merge objects. In glTF a material is referenced by mesh primitives, while Blender object boundaries follow imported nodes/meshes. In OBJ, repeated object declarations create separate object groups regardless of shared `usemtl`.

## Batching Decision

Dynamic block-entity geometry shall be globally batched by exact `MaterialKey` across the full export selection.

The batch key includes all fields already present in `MaterialKey`:

- texture identity and output path
- alpha mode and cutoff
- double-sided state
- emissive state
- blend semantic
- sampler mode

Two primitives merge only when their complete keys compare equal. Texture-only similarity is insufficient.

The policy applies only to `CapturedNode.Kind.BLOCK_ENTITY`. Static chunk meshes, ordinary entities, placeholders, and global overlays preserve their current object boundaries.

Consequences intentionally accepted by the owner:

- belts, gears, shafts, mechanical arms, and other dynamic block-entity geometry may share one Blender object when their exact material keys match
- source block-entity identity is flattened at the output-object layer
- individual world-position extras are not copied onto the merged material object
- geometry remains a snapshot with world-relative positions baked into vertices

## glTF Architecture

`GltfDocumentBuilder` shall maintain one merged mesh/node per encountered block-entity `MaterialKey`.

For every block-entity primitive:

1. resolve its exact material key
2. find or create the global material mesh/node
3. append the primitive JSON to that mesh
4. count a node only when a new material bucket is created

Stable generated names shall follow first-encounter order:

```text
BlockEntities/material_0000
BlockEntities/material_0001
```

Each generated node shall include compact extras describing the merge policy and material source identifier. Per-position source extras are intentionally omitted to keep streaming memory bounded.

A captured block entity containing multiple materials may contribute to multiple output objects. Multiple captured block entities with one shared material contribute to one output object.

## OBJ Architecture

OBJ requires all fragments for one material object to be emitted under one `o` declaration. Since batches arrive incrementally, `StreamingObjSession` shall spool block-entity fragments per `MaterialKey`.

Each spool shall use relative negative indices so it remains self-contained and can be copied after ordinary streamed geometry. At `finish()`:

1. close all material spool writers
2. emit one object declaration per material in first-encounter order
3. emit `g` and `usemtl` once for that material object
4. copy all corresponding vertex and topology fragments
5. delete every spool file

Cancellation and failure shall also close and delete all block-entity spool files. Existing global overlay spooling behavior remains unchanged.

Stable OBJ object names shall use the corresponding material name:

```text
BlockEntities_m0000
BlockEntities_m0001
```

## Counters

For both formats:

- `primitiveCount` remains the number of serialized source primitives
- `nodeCount` becomes the number of actual output objects/nodes
- several block entities sharing one material increase `nodeCount` by one
- one block entity using two distinct materials increases `nodeCount` by two

Face and line counters remain topology counts.

## TDD Contract

### Coordinate tests

- positive local Z remains positive in block-entity renderer replay translation
- base pose determinant remains positive and normals preserve positive Z
- entity local positions preserve positive Z
- entity fallback bounds retain ascending min/max Z
- block fallback bounds use `[z, z + 1]`
- source-policy test rejects residual MineToMesh-owned `scale(1, 1, -1)` and explicit local Z negation in auxiliary capture paths

### glTF batching tests

- two block entities with the same exact material produce one mesh/node containing two primitives
- two block-entity materials produce two mesh/nodes
- ordinary entities with the same material remain separate
- generated node extras identify `GLOBAL_MATERIAL`
- output statistics report actual merged node count

### OBJ batching tests

- two block entities with the same exact material produce one `o BlockEntities_m0000`
- both fragments remain present under the object
- distinct materials produce distinct block-entity objects
- ordinary nodes remain separate
- all block-entity spool files are deleted after finish and close-without-finish
- output statistics report merged object count

The red phase must fail through behavior assertions against the current implementation, not through compilation mistakes.

## Verification

Automated:

1. focused coordinate-contract tests
2. focused glTF material-batching tests
3. focused OBJ material-batching and spool-cleanup tests
4. full `clean test build`
5. dedicated-server `runServerSmoke`
6. production JAR metadata/resource audit

Manual Blender matrix:

1. export at least ten connected Create belts plus an asymmetric marker
2. import glTF and OBJ separately
3. confirm belt geometry is not mirrored on Blender Y
4. confirm belt front faces and normals point outward
5. confirm all belt fragments sharing an exact material import as one object
6. include a dynamic component using another material and confirm it imports as a separate object
7. include an ordinary entity and confirm entity object boundaries remain independent
8. compare glTF and OBJ orientation and material-object partitioning

## Release Boundary

This design produces a tested candidate build on a feature branch. Merging to `main`, changing the published version, moving tags, or replacing GitHub Release assets requires a separate explicit release instruction.
