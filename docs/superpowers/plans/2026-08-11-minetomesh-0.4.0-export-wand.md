# MineToMesh 0.4.0 Export Wand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the export workstation with a craftable, item-owned export wand that selects blocks with WorldEdit-style clicks and opens the existing export GUI from the held item.

**Architecture:** A typed ItemStack Data Component owns each wand's identity, dimension, endpoints, overlay preference, and export name. Server-side services validate every mutation and export request; the client only captures input, renders the held wand's overlay, and runs the existing glTF/OBJ pipeline after a server grant. The workstation registry and resources are hard-deleted in the same breaking 0.4.0 release.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.244, Gradle 9.2.1, JUnit 5.11.4, NeoForge custom payloads, DataComponentType Codec/StreamCodec, Pillow 11.3.0.

---

## File and Responsibility Map

### New production units

- `src/main/java/com/onecuber/mcgltf/wand/Axis.java` — X/Y/Z value type moved out of workstation scope.
- `src/main/java/com/onecuber/mcgltf/wand/Endpoint.java` — POS1/POS2 value type.
- `src/main/java/com/onecuber/mcgltf/wand/ExportWandSelection.java` — immutable Data Component value and codecs.
- `src/main/java/com/onecuber/mcgltf/wand/WandBinding.java` — hand, slot, and wand UUID identity binding.
- `src/main/java/com/onecuber/mcgltf/wand/ExportWandService.java` — server-authoritative item mutation and validation.
- `src/main/java/com/onecuber/mcgltf/wand/ExportWandItem.java` — right-click block/air behavior and menu opening.
- `src/main/java/com/onecuber/mcgltf/wand/ExportWandMenu.java` — item-bound menu validity and current selection access.
- `src/main/java/com/onecuber/mcgltf/wand/WandInteractionHandler.java` — left-click block cancellation and POS1 assignment.
- `src/main/java/com/onecuber/mcgltf/client/wand/WandClientInput.java` — Shift+left-click-air detection and clear request.
- `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java` — item-backed coordinate editor and export dashboard.
- `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandController.java` — grant/export lifecycle bound to wand UUID.
- `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandTextures.java` — 77 approved GUI slice descriptors.
- `src/main/java/com/onecuber/mcgltf/client/wand/HeldWandOverlaySource.java` — resolves one renderable held-wand selection.
- `src/main/java/com/onecuber/mcgltf/client/wand/SelectionOverlayRenderer.java` — orange volume and blue depth-tested lines.
- `src/main/java/com/onecuber/mcgltf/network/ClearWandSelectionPayload.java` — client-to-server air-clear request.
- `src/main/java/com/onecuber/mcgltf/network/UpdateWandEndpointPayload.java` — menu coordinate update.
- `src/main/java/com/onecuber/mcgltf/network/ToggleWandOverlayPayload.java` — menu overlay preference update.
- `src/main/java/com/onecuber/mcgltf/network/UpdateWandExportNamePayload.java` — validated persistent export-name update.
- `src/main/java/com/onecuber/mcgltf/network/ExportWandRequestPayload.java` — menu export request.
- `src/main/java/com/onecuber/mcgltf/network/ExportWandGrantedPayload.java` — immutable server-approved snapshot.
- `src/main/java/com/onecuber/mcgltf/network/ExportWandRejectedPayload.java` — localized rejection.
- `src/main/java/com/onecuber/mcgltf/network/WandClientReceiver.java` — process-lifetime client callbacks.
- `src/main/java/com/onecuber/mcgltf/network/WandPayloads.java` — payload registration and handlers.
- `tools/generate-export-wand-texture.py` — deterministic 16×16 item texture generator.

### Reused and renamed units

- `CoordinateEditorModel`, `WorkstationBorderPolicy`, `WorkstationTextures`, `WorkstationExportController`, and workstation visual tests move to `client/wand` and receive `ExportWand*` names.
- Existing export jobs, telemetry, glTF, OBJ, texture capture, and report code remain unchanged.

### Hard-deleted units

- Entire `src/main/java/com/onecuber/mcgltf/workstation/` package after reusable enums/value logic has moved.
- `ExportWorkstationScreen`, station overlay state/key, station controller, station textures.
- Workstation payloads, block/item/block-entity/menu registrations, block resources, loot, recipe, pickaxe tag, and old GUI paths.

---

### Task 1: Establish the 0.4.0 Wand Domain and Typed Data Component

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/wand/Axis.java`
- Create: `src/main/java/com/onecuber/mcgltf/wand/Endpoint.java`
- Create: `src/main/java/com/onecuber/mcgltf/wand/ExportWandSelection.java`
- Create: `src/test/java/com/onecuber/mcgltf/wand/ExportWandSelectionTest.java`
- Modify: `.gitignore`

- [ ] **Step 1: Ignore persistent visual-companion artifacts**

Append this exact line to `.gitignore`:

```gitignore
.superpowers/
```

- [ ] **Step 2: Write failing immutable-selection tests**

Create tests covering empty defaults, first endpoint dimension binding, same-dimension second endpoint, cross-dimension rejection, clear semantics, complete selection conversion, and Codec round-trip:

```java
@Test
void firstEndpointBindsDimensionAndClearPreservesPreferences() {
    ExportWandSelection initial = ExportWandSelection.empty()
            .ensureIdentity(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            .withOverlayEnabled(false)
            .withExportName("flower_factory");
    ExportWandSelection selected = initial.setEndpoint(
            ResourceLocation.parse("minecraft:overworld"), Endpoint.POS1,
            new BlockPos(1, 64, 2));
    assertEquals(Optional.of(ResourceLocation.parse("minecraft:overworld")),
            selected.selectionDimension());
    assertEquals(Optional.of(new BlockPos(1, 64, 2)), selected.pos1());
    ExportWandSelection cleared = selected.clearSelection();
    assertTrue(cleared.wandId().isPresent());
    assertTrue(cleared.selectionDimension().isEmpty());
    assertTrue(cleared.pos1().isEmpty());
    assertFalse(cleared.overlayEnabled());
    assertEquals("flower_factory", cleared.exportName());
}

@Test
void crossDimensionEndpointIsRejectedWithoutMutation() {
    ExportWandSelection selected = ExportWandSelection.empty()
            .ensureIdentity(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            .setEndpoint(ResourceLocation.parse("minecraft:overworld"),
                    Endpoint.POS1, BlockPos.ZERO);
    assertThrows(IllegalArgumentException.class, () -> selected.setEndpoint(
            ResourceLocation.parse("minecraft:the_nether"), Endpoint.POS2,
            new BlockPos(2, 70, 2)));
    assertTrue(selected.pos2().isEmpty());
}
```

- [ ] **Step 3: Run the domain test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.ExportWandSelectionTest
```

Expected: compilation fails because `ExportWandSelection`, `Axis`, and `Endpoint` do not exist.

- [ ] **Step 4: Implement the value type and codecs**

Use this record shape and defaults:

```java
public record ExportWandSelection(
        Optional<UUID> wandId,
        Optional<ResourceLocation> selectionDimension,
        Optional<BlockPos> pos1,
        Optional<BlockPos> pos2,
        boolean overlayEnabled,
        String exportName) {
    public static final String DEFAULT_EXPORT_NAME = "export";
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
            UUID::fromString, UUID::toString);
    public static final Codec<ExportWandSelection> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.optionalFieldOf("wand_id").forGetter(ExportWandSelection::wandId),
                    ResourceLocation.CODEC.optionalFieldOf("dimension")
                            .forGetter(ExportWandSelection::selectionDimension),
                    BlockPos.CODEC.optionalFieldOf("pos1").forGetter(ExportWandSelection::pos1),
                    BlockPos.CODEC.optionalFieldOf("pos2").forGetter(ExportWandSelection::pos2),
                    Codec.BOOL.optionalFieldOf("overlay_enabled", true)
                            .forGetter(ExportWandSelection::overlayEnabled),
                    Codec.STRING.optionalFieldOf("export_name", DEFAULT_EXPORT_NAME)
                            .forGetter(ExportWandSelection::exportName))
                    .apply(instance, ExportWandSelection::new));

    public static ExportWandSelection empty() {
        return new ExportWandSelection(Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), true, DEFAULT_EXPORT_NAME);
    }
}
```

Implement `ensureIdentity(UUID)`, `setEndpoint(ResourceLocation, Endpoint, BlockPos)`, `clearSelection()`, `withOverlayEnabled(boolean)`, `withExportName(String)`, `isComplete()`, and `toSelection()` as pure copy-returning operations. `clearSelection()` must preserve `wandId`, `overlayEnabled`, and `exportName`.

Define `STREAM_CODEC` with `StreamCodec.composite`, `ByteBufCodecs.optional`, `ResourceLocation.STREAM_CODEC`, `BlockPos.STREAM_CODEC`, `ByteBufCodecs.BOOL`, and `ByteBufCodecs.STRING_UTF8` in the same field order as `CODEC`.

- [ ] **Step 5: Run tests and commit**

Run:

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.ExportWandSelectionTest
```

Expected: all `ExportWandSelectionTest` tests pass.

Commit:

```powershell
git add .gitignore src/main/java/com/onecuber/mcgltf/wand src/test/java/com/onecuber/mcgltf/wand
git commit -m "feat: define export wand selection data"
```

---

### Task 2: Register the Data Component, Wand Item, Menu Type, and Creative Entry

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/wand/ExportWandItem.java`
- Create: `src/main/java/com/onecuber/mcgltf/wand/WandBinding.java`
- Create: `src/main/java/com/onecuber/mcgltf/wand/ExportWandMenu.java`
- Create: `src/test/java/com/onecuber/mcgltf/content/ExportWandContentTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/content/McGltfContent.java`

- [ ] **Step 1: Write failing registration tests**

Assert all four registry objects and item policy:

```java
@Test
void registersOnlyTheExportWandAsPlayableContent() {
    assertEquals("mcgltf:export_wand",
            BuiltInRegistries.ITEM.getKey(McGltfContent.EXPORT_WAND_ITEM.get()).toString());
    assertEquals(1, McGltfContent.EXPORT_WAND_ITEM.get().getDefaultMaxStackSize());
    assertNotNull(McGltfContent.EXPORT_WAND_SELECTION.get());
    assertNotNull(McGltfContent.EXPORT_WAND_MENU.get());
}
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.ExportWandContentTest
```

Expected: compilation fails on missing wand holders.

- [ ] **Step 3: Add typed component and item registrations**

Add to `McGltfContent`:

```java
private static final DeferredRegister.DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, McGltf.MOD_ID);

public static final DeferredHolder<DataComponentType<?>, DataComponentType<ExportWandSelection>>
        EXPORT_WAND_SELECTION = DATA_COMPONENTS.registerComponentType(
                "export_wand_selection",
                builder -> builder.persistent(ExportWandSelection.CODEC)
                        .networkSynchronized(ExportWandSelection.STREAM_CODEC));

public static final DeferredItem<ExportWandItem> EXPORT_WAND_ITEM =
        ITEMS.register("export_wand", () -> new ExportWandItem(
                new Item.Properties().stacksTo(1)));
```

Register `DATA_COMPONENTS` on the mod bus. Register `EXPORT_WAND_MENU` with `IMenuTypeExtension.create(ExportWandMenu.FACTORY)`. Change the creative tab icon and output to `EXPORT_WAND_ITEM` only.

- [ ] **Step 4: Implement the minimum compilable item and menu shells**

`WandBinding` must contain `InteractionHand hand`, `int inventorySlot`, and `UUID wandId`. Its `resolve(Player)` returns the bound stack only when item type and UUID match.

`ExportWandMenu` must extend `AbstractContainerMenu`, expose client/server constructors through `IContainerFactory`, return `ItemStack.EMPTY` from `quickMoveStack`, and delegate `stillValid` to `WandBinding.resolve(player).isPresent()`.

`ExportWandItem` may return `InteractionResult.PASS` until interaction behavior is added in Task 5.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.ExportWandContentTest
```

Expected: PASS.

```powershell
git add src/main/java/com/onecuber/mcgltf/content/McGltfContent.java src/main/java/com/onecuber/mcgltf/wand src/test/java/com/onecuber/mcgltf/content/ExportWandContentTest.java
git commit -m "feat: register export wand content"
```

---

### Task 3: Add the Crafting Recipe, Item Model, Languages, and Deterministic 16×16 Texture

**Files:**
- Create: `tools/generate-export-wand-texture.py`
- Create: `src/main/resources/assets/mcgltf/models/item/export_wand.json`
- Create: `src/main/resources/assets/mcgltf/textures/item/export_wand.png`
- Create: `src/main/resources/data/mcgltf/recipe/export_wand.json`
- Create: `src/test/java/com/onecuber/mcgltf/content/ExportWandResourceTest.java`
- Modify: `src/main/resources/assets/mcgltf/lang/zh_cn.json`
- Modify: `src/main/resources/assets/mcgltf/lang/en_us.json`

- [ ] **Step 1: Write failing resource tests**

Require exact recipe pattern, ingredients, model parent, texture path, 16×16 RGBA dimensions, nontransparent pixel count, and language keys:

```java
assertEquals(List.of("  A", " RC", "S  "), pattern);
assertEquals("minecraft:amethyst_shard", key.getAsJsonObject("A").get("item").getAsString());
assertEquals("minecraft:redstone", key.getAsJsonObject("R").get("item").getAsString());
assertEquals("minecraft:copper_ingot", key.getAsJsonObject("C").get("item").getAsString());
assertEquals("minecraft:stick", key.getAsJsonObject("S").get("item").getAsString());
assertEquals(16, image.getWidth());
assertEquals(16, image.getHeight());
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.ExportWandResourceTest
```

Expected: missing resource failures.

- [ ] **Step 3: Create exact JSON resources**

Item model:

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "mcgltf:item/export_wand" }
}
```

Recipe:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["  A", " RC", "S  "],
  "key": {
    "A": { "item": "minecraft:amethyst_shard" },
    "R": { "item": "minecraft:redstone" },
    "C": { "item": "minecraft:copper_ingot" },
    "S": { "item": "minecraft:stick" }
  },
  "result": { "id": "mcgltf:export_wand", "count": 1 }
}
```

Add `item.mcgltf.export_wand` as `导出魔杖` and `Export Wand`; retain `itemGroup.mcgltf`.

- [ ] **Step 4: Generate the engineering survey-rod texture**

Before producing the image, load the `canvas-design` skill and apply its original-design and pixel-clarity rules. Then implement a Pillow script that creates a transparent 16×16 RGBA image and paints these crisp rectangles:

```python
RECTS = [
    (3, 13, 5, 14, "#151a22"), (4, 11, 6, 13, "#3e4854"),
    (5, 8, 6, 11, "#ccd2d7"), (6, 6, 7, 8, "#687583"),
    (7, 4, 8, 6, "#d9dee2"), (8, 2, 9, 4, "#566371"),
    (9, 1, 10, 2, "#f08a33"), (10, 1, 12, 2, "#efefef"),
    (11, 2, 13, 3, "#3c9bec"), (9, 5, 11, 5, "#ed741c"),
    (10, 6, 12, 6, "#3488d8"), (5, 9, 5, 9, "#f0a25e"),
    (6, 7, 6, 7, "#85c8ff")
]
```

Treat rectangle endpoints as inclusive. Save to `src/main/resources/assets/mcgltf/textures/item/export_wand.png` with no scaling or antialiasing.

Run:

```powershell
python tools\generate-export-wand-texture.py
```

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.ExportWandResourceTest
```

Expected: PASS.

```powershell
git add tools/generate-export-wand-texture.py src/main/resources/assets/mcgltf src/main/resources/data/mcgltf/recipe/export_wand.json src/test/java/com/onecuber/mcgltf/content/ExportWandResourceTest.java
git commit -m "feat: add export wand assets and recipe"
```

---

### Task 4: Implement Server-Authoritative Wand Mutation and Sounds

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/wand/ExportWandService.java`
- Create: `src/test/java/com/onecuber/mcgltf/wand/ExportWandServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Cover identity initialization, endpoint write, cross-dimension rollback, clear preference preservation, Y-range validation, and binding mismatch.

```java
@Test
void crossDimensionRequestLeavesStackUnchanged() {
    ItemStack wand = new ItemStack(McGltfContent.EXPORT_WAND_ITEM.get());
    service.setEndpoint(wand, OVERWORLD, Endpoint.POS1, BlockPos.ZERO);
    ExportWandSelection before = service.selection(wand);
    assertEquals(ExportWandService.Result.WRONG_DIMENSION,
            service.setEndpoint(wand, NETHER, Endpoint.POS2, new BlockPos(1, 64, 1)));
    assertEquals(before, service.selection(wand));
}
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.ExportWandServiceTest
```

Expected: missing service.

- [ ] **Step 3: Implement atomic mutation**

The service must read with:

```java
stack.getOrDefault(McGltfContent.EXPORT_WAND_SELECTION.get(),
        ExportWandSelection.empty())
```

It must generate UUIDs only on the logical server, compute the complete replacement value before `stack.set(...)`, and return an enum result:

```java
public enum Result { SUCCESS_POS1, SUCCESS_POS2, CLEARED, WRONG_DIMENSION,
    OUT_OF_BUILD_HEIGHT, INVALID_WAND, INVALID_BINDING }
```

- [ ] **Step 4: Fix sound mapping at one boundary**

Map successful/failed results to exactly:

```text
POS1  SoundEvents.NOTE_BLOCK_HAT.value(), volume 0.6, pitch 0.75
POS2  SoundEvents.NOTE_BLOCK_HAT.value(), volume 0.6, pitch 1.25
CLEAR SoundEvents.BEACON_DEACTIVATE, volume 0.6, pitch 1.0
REJECT SoundEvents.VILLAGER_NO, volume 0.6, pitch 1.0
OPEN  SoundEvents.BOOK_PAGE_TURN, volume 0.6, pitch 1.1
```

Use `ServerPlayer.playNotifySound` so only the acting player hears feedback.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.ExportWandServiceTest
```

Expected: PASS.

```powershell
git add src/main/java/com/onecuber/mcgltf/wand/ExportWandService.java src/test/java/com/onecuber/mcgltf/wand/ExportWandServiceTest.java
git commit -m "feat: add server-authoritative wand selection service"
```

---

### Task 5: Implement Left/Right Click Semantics and Air-Clear Input

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/wand/WandInteractionHandler.java`
- Create: `src/main/java/com/onecuber/mcgltf/client/wand/WandClientInput.java`
- Create: `src/main/java/com/onecuber/mcgltf/network/ClearWandSelectionPayload.java`
- Create: `src/main/java/com/onecuber/mcgltf/network/WandPayloads.java`
- Create: `src/test/java/com/onecuber/mcgltf/network/WandPayloadCodecTest.java`
- Create: `src/test/java/com/onecuber/mcgltf/wand/WandInteractionPolicyTest.java`
- Create: `src/test/java/com/onecuber/mcgltf/wand/WandInteractionIntegrationTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/wand/ExportWandItem.java`
- Modify: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`
- Modify: `src/main/java/com/onecuber/mcgltf/McGltf.java`

- [ ] **Step 1: Write failing precedence tests**

Test this complete table through a pure `WandInteractionPolicy.decide(shift, target, button)` helper:

```java
assertEquals(SET_POS1, decide(false, BLOCK, LEFT));
assertEquals(SET_POS1, decide(true, BLOCK, LEFT));
assertEquals(CLEAR, decide(true, AIR, LEFT));
assertEquals(SET_POS2, decide(false, BLOCK, RIGHT));
assertEquals(OPEN_GUI, decide(true, BLOCK, RIGHT));
assertEquals(OPEN_GUI, decide(true, AIR, RIGHT));
assertEquals(PASS, decide(false, AIR, RIGHT));
```

`WandInteractionIntegrationTest` must read `WandInteractionHandler.java` and assert that it contains `event.setCanceled(true)`, checks `LeftClickBlock.Action.START` before mutation, and guards server writes with `!event.getLevel().isClientSide()`.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.WandInteractionPolicyTest
```

Expected: missing policy/handlers.

- [ ] **Step 3: Implement left-click cancellation correctly**

Register `WandInteractionHandler.onLeftClickBlock(PlayerInteractEvent.LeftClickBlock)` on `NeoForge.EVENT_BUS` from the common mod constructor.

Rules:

```java
if (!isWandInEitherHand(player)) return;
event.setCanceled(true);
if (!event.getLevel().isClientSide()
        && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
    service.setEndpoint(serverPlayer, resolvedHand, Endpoint.POS1, event.getPos());
}
```

Cancel every repeated client event to suppress cracks, but mutate and play sound only on server `Action.START`.

- [ ] **Step 4: Implement right-click and Shift priority**

In `ExportWandItem.useOn`, check `player.isSecondaryUseActive()` first. Shift opens the menu; otherwise set POS2 and return `InteractionResult.sidedSuccess(level.isClientSide)`. In `use`, only Shift opens the menu; normal right-click air returns `InteractionResultHolder.pass(stack)`.

- [ ] **Step 5: Implement Shift+left-click-air clear**

In `WandClientInput`, handle `InputEvent.InteractionKeyMappingTriggered` only when:

```java
event.isAttack()
&& player.isShiftKeyDown()
&& minecraft.hitResult != null
&& minecraft.hitResult.getType() == HitResult.Type.MISS
&& resolvedHandContainsWand
```

Then call `event.setCanceled(true)` and `event.setSwingHand(false)`. `WandClientInput` owns a `clearChordHeld` latch: send `ClearWandSelectionPayload(hand)` only when the latch is false, set it true after sending, and reset it from the client tick listener as soon as the attack key or Shift is released. The server handler re-resolves the hand and invokes `ExportWandService.clearSelection`.

- [ ] **Step 6: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.WandInteractionPolicyTest --tests com.onecuber.mcgltf.wand.WandInteractionIntegrationTest --tests com.onecuber.mcgltf.network.WandPayloadCodecTest
```

Expected: wand policy and updated payload codec tests pass.

```powershell
git add src/main/java/com/onecuber/mcgltf/wand src/main/java/com/onecuber/mcgltf/client/wand/WandClientInput.java src/main/java/com/onecuber/mcgltf/network src/main/java/com/onecuber/mcgltf/McGltf.java src/main/java/com/onecuber/mcgltf/client/McGltfClient.java src/test/java/com/onecuber/mcgltf/wand
git commit -m "feat: add export wand world interactions"
```

---

### Task 6: Finish Item-Bound Menu Identity and Coordinate Mutation Payloads

**Files:**
- Modify: `src/main/java/com/onecuber/mcgltf/wand/WandBinding.java`
- Modify: `src/main/java/com/onecuber/mcgltf/wand/ExportWandMenu.java`
- Create: `src/main/java/com/onecuber/mcgltf/network/UpdateWandEndpointPayload.java`
- Create: `src/main/java/com/onecuber/mcgltf/network/ToggleWandOverlayPayload.java`
- Create: `src/main/java/com/onecuber/mcgltf/network/UpdateWandExportNamePayload.java`
- Create: `src/test/java/com/onecuber/mcgltf/wand/ExportWandMenuTest.java`
- Modify: `src/test/java/com/onecuber/mcgltf/network/WandPayloadCodecTest.java`

- [ ] **Step 1: Write failing menu identity tests**

Test main-hand slot, offhand slot 40, valid same stack, invalid moved stack, invalid replacement wand, and invalid removed wand:

```java
assertTrue(menu.stillValid(player));
player.getInventory().setItem(binding.inventorySlot(), ItemStack.EMPTY);
assertFalse(menu.stillValid(player));
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.ExportWandMenuTest --tests com.onecuber.mcgltf.network.WandPayloadCodecTest
```

Expected: missing payloads and incomplete validity behavior.

- [ ] **Step 3: Serialize the binding in menu open data**

Write/read exactly:

```text
hand enum
inventorySlot varint
wandId UUID
```

Before `ServerPlayer.openMenu`, call `ensureIdentity`, compute the binding, and write it to the menu buffer. The client menu constructor reads the same order.

- [ ] **Step 4: Implement menu-scoped mutations**

`UpdateWandEndpointPayload` contains `Endpoint` and complete `BlockPos`; `ToggleWandOverlayPayload` contains one boolean; `UpdateWandExportNamePayload` contains one UTF-8 string capped at 64 characters. Server handlers require `player.containerMenu instanceof ExportWandMenu`, call `menu.resolveBoundStack(player)`, and reject all mismatches without mutation. Export-name updates must pass `ExportName.parse` before `ExportWandService.setExportName` writes the component.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.wand.ExportWandMenuTest --tests com.onecuber.mcgltf.network.WandPayloadCodecTest
```

Expected: PASS.

```powershell
git add src/main/java/com/onecuber/mcgltf/wand src/main/java/com/onecuber/mcgltf/network src/test/java/com/onecuber/mcgltf/wand src/test/java/com/onecuber/mcgltf/network/WandPayloadCodecTest.java
git commit -m "feat: bind wand menu to item identity"
```

---

### Task 7: Migrate the 77 GUI Slices and Refactor the Screen

**Files:**
- Move: `src/main/resources/assets/mcgltf/textures/gui/workstation/gui_001.png` through `gui_077.png` to `src/main/resources/assets/mcgltf/textures/gui/export_wand/`
- Move: `src/main/java/com/onecuber/mcgltf/client/workstation/CoordinateEditorModel.java` to `src/main/java/com/onecuber/mcgltf/client/wand/CoordinateEditorModel.java`
- Move: `src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationBorderPolicy.java` to `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandBorderPolicy.java`
- Move: `src/main/java/com/onecuber/mcgltf/client/workstation/WorkstationTextures.java` to `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandTextures.java`
- Move/refactor: `src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java` to `src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java`
- Move/refactor related tests from `src/test/java/com/onecuber/mcgltf/client/workstation/` to `src/test/java/com/onecuber/mcgltf/client/wand/`

- [ ] **Step 1: Change visual tests first**

Update tests to require resource prefix:

```text
textures/gui/export_wand/gui_%03d.png
```

Require no `feetButton` layout method, coordinate field width `116`, no intersection among all six fields and twelve step buttons, and title composition from `McGltf.DISPLAY_NAME + " " + McGltf.VERSION`.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.client.wand.*"
```

Expected: new package/classes/resources absent.

- [ ] **Step 3: Move resources and pure UI helpers**

Preserve the bytes of all 77 PNG files. Rename the texture class and change only the resource path builder:

```java
String path = "textures/gui/export_wand/gui_%03d.png".formatted(index);
```

Retain the physical 8px nine-slice policy.

- [ ] **Step 4: Refactor the Screen to item data**

Change menu type to `ExportWandMenu`; remove `CaptureFeetPayload`, `feetButton`, `captureFeet`, and both feet widgets. Use:

```java
new Rect(LEFT.x() + 32,
        LEFT.y() + 22 + group * 66 + row * 16,
        116, 14)
```

Place step-up at `LEFT.x() + 154` and step-down at `LEFT.x() + 172`, both 16×16. Keep the overlay button at the bottom. Empty optional endpoints render as blank strings; all three coordinate drafts must parse before sending `UpdateWandEndpointPayload`. Commit the export-name field on Enter or focus loss through `UpdateWandExportNamePayload`, so closing and reopening the same wand preserves the name.

- [ ] **Step 5: Register the new Screen and run tests**

Update `McGltfClient.onRegisterMenuScreens` to construct `ExportWandScreen` for `EXPORT_WAND_MENU`.

Run:

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.client.wand.*"
```

Expected: all migrated visual/layout tests pass.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/onecuber/mcgltf/client src/main/resources/assets/mcgltf/textures/gui src/test/java/com/onecuber/mcgltf/client
git commit -m "feat: migrate export GUI to the wand"
```

---

### Task 8: Refactor Export Authorization and Controller from Station Position to Wand UUID

**Files:**
- Move/refactor: `WorkstationExportController.java` to `client/wand/ExportWandController.java`
- Move/refactor: `WorkstationClientReceiver.java` to `network/WandClientReceiver.java`
- Replace: `ExportRequestPayload.java` with `ExportWandRequestPayload.java`
- Replace: `ExportGrantedPayload.java` with `ExportWandGrantedPayload.java`
- Replace: `ExportRejectedPayload.java` with `ExportWandRejectedPayload.java`
- Replace/refactor: `WorkstationRequestPolicy.java` to `WandRequestPolicy.java`
- Replace/refactor related controller, payload, permission, and lifecycle tests.

- [ ] **Step 1: Write failing wand-bound controller tests**

Use a fixed UUID and require grants to match it and the bound dimension:

```java
controller.bind(WAND_ID, "minecraft:overworld");
controller.requested("flower_factory");
assertTrue(controller.accept(grant(WAND_ID, "flower_factory")));
assertEquals(ExportWandController.State.EXPORTING, controller.state());
```

Also assert wrong UUID, wrong dimension, late grants, concurrent jobs, screen close cancellation, and process-lifetime receiver behavior.

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.client.wand.ExportWandControllerTest --tests com.onecuber.mcgltf.network.WandClientReceiverLifecycleTest
```

Expected: wand controller and receiver missing.

- [ ] **Step 3: Replace station identity with wand identity**

Grant payload fields must be exactly:

```text
UUID wandId
String exportName
BlockPos pos1
BlockPos pos2
String dimension
```

Reject payload fields must be `UUID wandId` and `String reasonKey`. The request contains only `String exportName`; the server reads selection and UUID from `ExportWandMenu.resolveBoundStack`.

- [ ] **Step 4: Preserve receiver lifetime and permission policy**

`WandClientReceiver` installs once in `McGltfClient` and exposes no reset method. Logout clears controller world state but leaves callbacks installed. `WandRequestPolicy.validateExportPermission` keeps integrated singleplayer bypass and dedicated `hasPermission(2)`.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.client.wand.ExportWandControllerTest --tests com.onecuber.mcgltf.network.WandClientReceiverLifecycleTest --tests com.onecuber.mcgltf.network.WandRequestPolicyTest
```

Expected: PASS.

```powershell
git add src/main/java/com/onecuber/mcgltf/client src/main/java/com/onecuber/mcgltf/network src/test/java/com/onecuber/mcgltf/client src/test/java/com/onecuber/mcgltf/network
git commit -m "feat: authorize exports from bound wands"
```

---

### Task 9: Drive the Overlay from the Currently Held Wand

**Files:**
- Create: `src/main/java/com/onecuber/mcgltf/client/wand/HeldWandOverlaySource.java`
- Move/refactor: `SelectionOverlayRenderer.java` to `client/wand/SelectionOverlayRenderer.java`
- Delete after migration: station `OverlayKey.java` and `SelectionOverlayState.java`
- Create: `src/test/java/com/onecuber/mcgltf/client/wand/HeldWandOverlaySourceTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/client/McGltfClient.java`

- [ ] **Step 1: Write failing overlay-source tests**

Cover main-hand priority, offhand fallback, hidden preference, incomplete selection, wrong dimension, item switch hide, and restoring the same wand.

```java
assertEquals(Optional.of(mainSelection),
        source.resolve(mainWand, offhandWand, OVERWORLD));
assertTrue(source.resolve(new ItemStack(Items.STICK), offhandWand, NETHER).isEmpty());
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.client.wand.HeldWandOverlaySourceTest
```

Expected: missing source.

- [ ] **Step 3: Implement pure held-stack resolution**

Resolve main hand first, then offhand. A stack is renderable only when it is `EXPORT_WAND_ITEM`, its component has `overlayEnabled=true`, both endpoints, and a dimension equal to the current client dimension.

- [ ] **Step 4: Refactor renderer input without changing geometry**

Keep `RenderType.debugQuads()` for the orange volume and existing depth-tested blue line rendering. Remove station keys and station block-presence checks. On every render event, ask `HeldWandOverlaySource` for the current selection.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat test --tests "com.onecuber.mcgltf.client.wand.*Overlay*"
```

Expected: PASS.

```powershell
git add src/main/java/com/onecuber/mcgltf/client src/test/java/com/onecuber/mcgltf/client
git commit -m "feat: render selection from the held export wand"
```

---

### Task 10: Hard-Delete the Export Workstation and Prove Its Absence

**Files:**
- Delete: `src/main/java/com/onecuber/mcgltf/workstation/`
- Delete migrated originals under `src/main/java/com/onecuber/mcgltf/client/workstation/`
- Delete: `CaptureFeetPayload.java`, `UpdateCoordinatePayload.java`, `WorkstationPayloads.java`, `WorkstationRequestPolicy.java`, `WorkstationClientReceiver.java`
- Delete: workstation blockstate, block/item models, five block textures, old full GUI texture, loot table, recipe, and pickaxe tag.
- Delete/refactor: workstation-specific tests.
- Delete: `src/testmod/java/com/onecuber/mcgltf/testmod/WorkstationGameTests.java`
- Create: `src/testmod/java/com/onecuber/mcgltf/testmod/WandGameTests.java`
- Create: `src/test/java/com/onecuber/mcgltf/content/WorkstationRemovalTest.java`
- Modify: `src/main/java/com/onecuber/mcgltf/content/McGltfContent.java`
- Modify: `src/testmod/java/com/onecuber/mcgltf/testmod/McGltfTestMod.java`

- [ ] **Step 1: Write the failing absence test before deletion**

Assert source/resource paths do not exist and JAR-facing registries expose no workstation holders:

```java
assertFalse(Files.exists(root.resolve(
        "src/main/resources/assets/mcgltf/blockstates/export_workstation.json")));
assertFalse(Files.exists(root.resolve(
        "src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlock.java")));
assertFalse(Files.readString(root.resolve(
        "src/main/java/com/onecuber/mcgltf/content/McGltfContent.java"))
        .contains("EXPORT_WORKSTATION"));
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.WorkstationRemovalTest
```

Expected: failures list the existing workstation files.

- [ ] **Step 3: Remove registrations and resources**

Remove deferred block, block item, block entity, workstation menu, and their register imports. Delete the exact old resources. Remove workstation keys from `zh_cn.json` and `en_us.json`. Preserve only the new wand recipe and item resources.

- [ ] **Step 4: Delete obsolete code and repair package imports**

Delete the workstation package only after `Axis`, `Endpoint`, coordinate editing, overlay geometry, border policy, textures, Screen, and controller have migrated. Replace all remaining `workstation` imports with `wand` imports. Replace `WorkstationGameTests` with `WandGameTests`: one GameTest must round-trip `ExportWandSelection` through an ItemStack component, and `onServerStarted` must continue printing `MINETOMESH_SERVER_READY` before halting. Update `McGltfTestMod` to register `WandGameTests::onServerStarted`.

- [ ] **Step 5: Run removal and full compile tests**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.content.WorkstationRemovalTest --tests com.onecuber.mcgltf.ServerClassIsolationTest
```

Expected: PASS and no workstation class linkage.

- [ ] **Step 6: Commit**

```powershell
git add -A src/main src/test src/testmod
git commit -m "refactor: remove the export workstation"
```

---

### Task 11: Update 0.4.0 Identity, Documentation, and Manual Matrix

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/java/com/onecuber/mcgltf/McGltf.java`
- Modify: `README.md`
- Modify: `docs/testing/manual-client-matrix.md`
- Modify: `src/test/java/com/onecuber/mcgltf/McGltfMetadataTest.java`
- Modify: `src/test/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilderTest.java`
- Modify: `src/test/java/com/onecuber/mcgltf/DocumentationPolicyTest.java`

- [ ] **Step 1: Change release contract tests to 0.4.0**

Require:

```text
McGltf.VERSION == 0.4.0
metadata version="0.4.0"
glTF generator == MineToMesh 0.4.0
README mentions mcgltf-0.4.0.jar and 导出魔杖
README does not instruct users to craft or place 区域导出工作台
```

- [ ] **Step 2: Run and verify RED**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.McGltfMetadataTest --tests com.onecuber.mcgltf.gltf.GltfDocumentBuilderTest --tests com.onecuber.mcgltf.DocumentationPolicyTest
```

Expected: old 0.3.2 identity/documentation failures.

- [ ] **Step 3: Update production identity and docs**

Set `mod_version=0.4.0` and `McGltf.VERSION="0.4.0"`. Rewrite installation and usage around the wand interaction table, recipe, item-owned selections, Shift gestures, permissions, and hard-delete migration warning.

- [ ] **Step 4: Add the exact manual matrix**

Document these passes: no-crack left click, container-safe right click, both Shift+right targets, Shift+left clear, cross-dimension reject, two-wand isolation, overlay hide/restore, menu invalidation after moving the wand, reconnect grant lifecycle, ordinary/admin/singleplayer permissions, and real glTF/OBJ Blender import.

- [ ] **Step 5: Run tests and commit**

```powershell
.\gradlew.bat test --tests com.onecuber.mcgltf.McGltfMetadataTest --tests com.onecuber.mcgltf.gltf.GltfDocumentBuilderTest --tests com.onecuber.mcgltf.DocumentationPolicyTest
```

Expected: PASS.

```powershell
git add gradle.properties src/main/java/com/onecuber/mcgltf/McGltf.java README.md docs/testing/manual-client-matrix.md src/test/java/com/onecuber/mcgltf/McGltfMetadataTest.java src/test/java/com/onecuber/mcgltf/gltf/GltfDocumentBuilderTest.java src/test/java/com/onecuber/mcgltf/DocumentationPolicyTest.java
git commit -m "build: release MineToMesh 0.4.0"
```

---

### Task 12: Full Verification, Real-Client Closure, and JAR Delivery

**Files:**
- No planned source modification; if verification exposes a failure, stop, add a focused failing test beside the affected component, then make one root-cause correction in that component.
- Deliver: `build/libs/mcgltf-0.4.0.jar`

- [ ] **Step 1: Run the complete clean build**

```powershell
.\gradlew.bat clean test build
```

Expected: `BUILD SUCCESSFUL`, zero failed/error/skipped tests.

- [ ] **Step 2: Run dedicated-server verification**

```powershell
.\gradlew.bat runServerSmoke
```

Expected: mod list contains `MineToMesh 0.4.0`, output contains `MINETOMESH_SERVER_READY`, and no client-class linkage error occurs.

- [ ] **Step 3: Inspect the production JAR**

```powershell
jar tf build\libs\mcgltf-0.4.0.jar
```

Require:

```text
assets/mcgltf/models/item/export_wand.json
assets/mcgltf/textures/item/export_wand.png
data/mcgltf/recipe/export_wand.json
assets/mcgltf/textures/gui/export_wand/gui_001.png through gui_077.png
```

Reject any entry matching:

```text
export_workstation
/client/workstation/
/workstation/
mcgltf_test
docs/superpowers
.superpowers
```

Extract `META-INF/neoforge.mods.toml` and verify `version="0.4.0"` and both dependency sides are `BOTH`.

- [ ] **Step 4: Install into the known PCL instance reversibly**

With Minecraft stopped, move the active older JAR to:

```text
D:\data\.minecraft\versions\1.21.1-NeoForge_21.1.244\mods\.minetomesh-backup\
```

Copy `mcgltf-0.4.0.jar` into the instance `mods` directory and verify its SHA-256 equals the build artifact.

- [ ] **Step 5: Execute the real-client closure**

Perform in order:

```text
enter world A
left-click a block with wand: no crack, POS1 sound
right-click a chest with wand: chest stays closed, POS2 sound
Shift+right-click: GUI opens
export a small selection: glTF and OBJ complete
leave world A
enter world B
repeat selection and export
move wand while GUI is open: GUI closes and task cancels
```

Expected: the second-world export does not remain at “等待服务端授权”. Import both output formats into Blender and confirm geometry/material presence.

- [ ] **Step 6: Final evidence and delivery**

Run:

```powershell
git diff --check
git status --short
Get-FileHash build\libs\mcgltf-0.4.0.jar -Algorithm SHA256
```

Expected: no tracked changes, no whitespace errors, and one final SHA-256. Stage `mcgltf-0.4.0.jar` for delivery. Do not push remote unless the user explicitly chooses a push/PR option.
