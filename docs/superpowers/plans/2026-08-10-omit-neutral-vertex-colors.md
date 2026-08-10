# Omit Neutral Vertex Colors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Omit glTF `COLOR_0` and its binary segment when every vertex in a Primitive is opaque white, while retaining any real RGB or Alpha vertex color.

**Architecture:** `StreamingGltfSession` decides whether a Primitive has meaningful vertex color before writing binary data. `WrittenPrimitive` carries an optional color segment, and `GltfDocumentBuilder` creates the color accessor and `COLOR_0` attribute only when that segment exists.

**Tech Stack:** Java 21, Gson, JUnit 5, NeoForge 21.1.244, Gradle.

---

### Task 1: Conditionally emit glTF vertex colors

**Files:**
- Modify: `src/test/java/com/onecuber/mcgltf/gltf/StreamingGltfSessionTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/gltf/StreamingGltfSession.java`
- Modify: `src/main/java/com/onecuber/mcgltf/gltf/WrittenPrimitive.java`
- Modify: `src/main/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilder.java`
- Modify: `src/test/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilderTest.java`

- [ ] **Step 1: Write failing integration tests**

Add tests that export three one-triangle scenes:

```java
@Test
void omitsColorAttributeForOpaqueWhiteVertices() throws Exception {
    JsonObject primitive = exportPrimitive("white", triangle(ColorRgba.WHITE));
    assertFalse(primitive.getAsJsonObject("attributes").has("COLOR_0"));
}

@Test
void retainsColorAttributeForRgbTintOrVertexAlpha() throws Exception {
    JsonObject tinted = exportPrimitive("tinted", triangle(new ColorRgba(254, 255, 255, 255)));
    JsonObject alpha = exportPrimitive("alpha", triangle(new ColorRgba(255, 255, 255, 254)));
    assertTrue(tinted.getAsJsonObject("attributes").has("COLOR_0"));
    assertTrue(alpha.getAsJsonObject("attributes").has("COLOR_0"));
}
```

The white case must also assert that the document has four accessors instead of five, proving no unused color accessor was emitted.

- [ ] **Step 2: Run the focused test and confirm red**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.gltf.StreamingGltfSessionTest
```

Expected: `omitsColorAttributeForOpaqueWhiteVertices` fails because `COLOR_0` is present and the accessor count is five.

- [ ] **Step 3: Make the color segment optional**

Change `WrittenPrimitive.colors` to `Optional<BinaryBufferWriter.Segment>` and validate the optional itself:

```java
Optional<BinaryBufferWriter.Segment> colors
```

In `StreamingGltfSession.write`, use:

```java
Optional<BinaryBufferWriter.Segment> colors = primitive.vertices().stream()
        .allMatch(vertex -> vertex.color().equals(ColorRgba.WHITE))
        ? Optional.empty()
        : Optional.of(binaryWriter.writeColors(primitive.vertices()));
```

In `GltfDocumentBuilder.addPrimitive`, only call `addAccessor` and add `attributes.COLOR_0` inside `primitive.colors().ifPresent(...)`.

- [ ] **Step 4: Update direct builder fixture construction**

Wrap the existing explicit color segment in `Optional.of(...)` so its normalized accessor assertion remains a regression test for meaningful color data.

- [ ] **Step 5: Run focused and complete glTF tests**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.gltf.*"
```

Expected: all glTF tests pass; white primitives have four accessors and no `COLOR_0`, while tinted/alpha primitives retain normalized `VEC4 COLOR_0`.

- [ ] **Step 6: Run release verification**

```powershell
.\gradlew.bat clean test build
git diff --check
```

Expected: build succeeds with zero failed tests and diff check produces no output.

- [ ] **Step 7: Commit and push**

```powershell
git add src/main/java/com/onecuber/mcgltf/gltf src/test/java/com/onecuber/mcgltf/gltf docs/superpowers/plans/2026-08-10-omit-neutral-vertex-colors.md
git commit -m "fix: omit neutral glTF vertex colors"
git push minetomesh HEAD:main
```
