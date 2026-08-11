# MineToMesh 连接纹理、草方块 Overlay 与输入隔离设计

日期：2026-08-11  
状态：设计已确认，待用户书面复核  
基线分支：`feature/mcgltf-0.4.0-export-wand`  
目标版本：0.5.0
Minecraft：1.21.1  
NeoForge：21.1.244

## 1. 背景

当前候选版本在真实 Create 6.x 场景中暴露出三类问题：

1. Create 方块在游戏中使用连续纹理，导出后却退化为每个方块重复单块纹理。
2. 原版草方块侧面导出两层共面几何，Blender 中难以把透明草色覆盖层与主体分开编辑。
3. 导出名输入框聚焦时，按 `E` 等字符键仍会触发物品栏或其他游戏快捷键；中文输入法也受同一物理按键泄漏影响。

本轮目标是在不引入 Create 硬依赖、不牺牲流式导出边界的前提下，捕获游戏已经计算完成的连接纹理 UV；保留草方块原始双层结构，但把整个选区的侧面 Overlay 汇总为一个独立对象；并在魔杖 GUI 打开时彻底隔离游戏快捷键。

视觉正确性由项目所有者在真实游戏与 Blender 中人工验收。自动化只验证可机械判定的纹理身份、UV、对象结构、材质属性、键盘事件、文档合法性与构建结果。

## 2. 已确认目标

### 2.1 必须实现

- 兼容 Minecraft 1.21.1、NeoForge 21.1.244 与 Create 6.x 的连接纹理导出。
- 不引用 Create、Catnip 或 Flywheel 的编译期类型，不增加对应运行时依赖。
- 当 `BakedQuad` 声明的 Sprite 与顶点实际图集 UV 不一致时，从同一 `TextureAtlas` 反查真实 Sprite。
- Create 方块应导出其 `create:block/*_connected` 纹理表，并保留游戏已经选好的子格 UV。
- 普通方块、动画纹理与现有资源包覆盖保持原有导出行为。
- 保留原版草方块的底层侧面和透明染色 Overlay 两层几何。
- 整个选区的 `minecraft:block/grass_block_side_overlay` 汇总为唯一独立对象。
- Overlay 保留透明材质、群系 Tint 顶点色、原始世界坐标与原始 Quad。
- 魔杖 GUI 打开期间，键盘只有 Esc 可以关闭界面；鼠标取消按钮保持有效，游戏和模组快捷键不得接收键盘事件。
- 英文字符、中文输入法提交字符、数字、负号及常用文本编辑组合键在输入框内正常工作。
- Enter 只提交当前输入框，不直接开始导出；Tab 只切换 GUI 焦点。
- 失败与取消继续遵守现有 `OutputTransaction` 清理规则。

### 2.2 明确不做

- 不重新实现 Create 的连接判定、`CTType` 或 `CTSpriteShiftEntry` 算法。
- 不通过反射读取 Create 私有字段。
- 不默认导出整张方块图集。
- 不把草方块底图与 Overlay 烘焙成单张纹理。
- 不把每个草方块 Overlay 拆成独立对象。
- 不把所有透明材质或所有方块材质都拆成全局对象。
- 不通过清空全局 `KeyMapping` 状态阻止快捷键。
- 不使用截图相似度、自动视觉评分或自动视觉通过判定替代人工验收。

## 3. 根因分析

### 3.1 Create 连接纹理为何退化

Create 1.21.1 的 `CTModel` 先在 `getModelData` 中读取真实世界邻接方块，生成每个面的连接纹理索引；随后在 `getQuads` 中克隆原始 `BakedQuad`，把四个顶点 UV 改写到目标 `*_connected` Sprite 的某个子格。

关键细节是 `BakedQuadHelper.clone` 继续保留原始 `quad.getSprite()`。因此最终 Quad 同时包含：

```text
声明 Sprite：原始单块纹理
实际 UV：目标 connected 纹理表在方块图集中的区域
```

游戏直接用图集 UV 采样，显示正确。当前 `BlockModelExtractor` 先信任 `quad.getSprite()`，提取原始单块 PNG，再按该 Sprite 的边界归一化已经被 Create 改写的 UV。归一化结果落到单块纹理外，材质重复采样后便表现为一个个独立方块。

参考实现：

- Create `CTModel.java`：`https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/foundation/block/connected/CTModel.java`
- Create `CTSpriteShiftEntry.java`：`https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/foundation/block/connected/CTSpriteShiftEntry.java`
- Create `BakedQuadHelper.java`：`https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/foundation/model/BakedQuadHelper.java`

### 3.2 草方块为何多一层侧面

Minecraft 1.21.1 的原版 `grass_block.json` 明确包含两个尺寸相同的立方体元素：

- 第一层四个侧面使用 `minecraft:block/grass_block_side`。
- 第二层四个侧面使用 `minecraft:block/grass_block_side_overlay`，带 `tintindex: 0`。

游戏依赖透明裁剪与生物群系染色把两层合成为最终侧面。导出器当前忠实捕获两个共面 Quad，因此 Blender 会直接看到第二层几何。这不是重复捕获错误，而是原版模型结构被完整暴露。

原版资源：`https://assets.mcasset.cloud/1.21.1/assets/minecraft/models/block/grass_block.json`

### 3.3 输入框为何触发 E 键物品栏

当前 `ExportWandScreen.keyPressed` 对非 Enter、非 Esc 按键调用 `super.keyPressed`。调用链进入 `AbstractContainerScreen.keyPressed` 后，会检查 `minecraft.options.keyInventory`；字符 `E` 的文本写入发生在后续 `charTyped`，所以 `EditBox.keyPressed(E)` 通常不会消费物理按键，容器层随后执行 `onClose()`。

当前实现即使在调用 `super.keyPressed` 后返回 `true` 也已经太晚，因为关闭动作已发生。输入隔离必须在进入 `AbstractContainerScreen` 的快捷键分支之前完成。

## 4. 方案比较

### 4.1 实际 UV 反查 Sprite，采用

根据 Quad 四个顶点的实际图集 UV，从相同 `TextureAtlas` 的已注册 Sprite 中查找完整覆盖该 UV 区域的真实 Sprite。

优点：

- 不依赖 Create 类型。
- 兼容其他采用 Sprite Shift、但保留旧 `BakedQuad.sprite` 元数据的模组。
- 继续输出可编辑的独立 PNG。
- 保留游戏已经算好的连接索引，无需复制模组算法。

代价：需要建立可缓存的图集空间索引，并处理浮点边界。

### 4.2 整张图集回退，不采用

对异常 Quad 导出整张方块图集并保留原始 UV。

优点是最接近 GPU 实际采样。代价是纹理巨大、输出臃肿、Blender 材质难以编辑，也破坏当前独立 Sprite 输出约定。

### 4.3 Create 专用适配器，不采用

通过 Create 或 Catnip 类直接获取目标 Sprite。

该路线对单一版本精确，但与内部类、方法签名和加载顺序强耦合，也会把通用导出器变成 Create 特判集合。

## 5. 总体架构

新增三个职责明确的组件：

```text
BakedQuad
  → 捕获原始顶点与实际图集 UV
  → AtlasSpriteResolver
  → SpriteTextureExtractor
  → BlockPrimitiveRouter
      → Section 主体流
      → Selection 全局草侧 Overlay 流
  → StreamingSceneSession
      → glTF Writer
      → OBJ Writer
```

GUI 输入独立走：

```text
GLFW keyPressed / keyReleased / charTyped
  → ExportWandScreen 输入隔离
      → 当前 EditBox
      → GUI 焦点导航
      → Esc 关闭
  × 不进入游戏 KeyMapping
```

## 6. `AtlasSpriteResolver`

### 6.1 输入与输出

输入：

```text
declaredSprite
四个实际 atlas UV
TextureAtlas 中的 canonical sprites
```

输出：

```text
resolvedSprite
resolutionKind = DECLARED | REDIRECTED | FALLBACK
可选诊断
```

解析结果使用 canonical `TextureAtlasSprite`，不继续使用模组临时包装 Sprite。

### 6.2 空间索引

每张 Atlas 在一次导出期间只建立一个 `AtlasSpriteIndex`：

- 从 `TextureAtlas.getTextures()` 读取稳定资源 ID 与 canonical Sprite。
- 使用归一化 UV 空间的 256×256 固定桶索引，避免每个 Quad 线性扫描整张图集。
- Sprite 插入其覆盖的所有桶；查询使用四个 UV 的包围盒中心桶，再执行精确覆盖检查。
- 索引按 Atlas 身份缓存，资源重载或下一次导出重新建立。

固定桶只用于缩小候选集，不参与最终判定。

### 6.3 覆盖与选择规则

1. 先用 `declaredSprite.contents().name()` 在 Atlas 中取得 canonical 声明 Sprite。
2. 若 canonical 声明 Sprite 在容差内覆盖全部四个 UV，返回 `DECLARED`。
3. 否则查询覆盖全部 UV 的候选 Sprite。
4. 候选唯一时返回该 Sprite。
5. 多候选时选择 UV 面积最小者；面积相同按资源 ID 字典序稳定选择。
6. 无候选时返回 canonical 声明 Sprite，并记录 `ATLAS_SPRITE_RESOLUTION_FAILED`。

容差按候选 Sprite 可推导的 Atlas 尺寸换算，最大为半个 Atlas 像素。容差只用于边界比较，不修改最终 UV。

### 6.4 捕获顺序调整

`BlockModelExtractor.captureQuad` 必须先通过 `CapturingVertexConsumer` 取得真实 UV，再解析 Sprite。后续步骤改为：

```text
capture raw vertices
→ resolve actual sprite from raw UVs
→ extract resolved sprite image
→ normalize raw UVs against resolved sprite bounds
→ resolve material
→ append routed primitive
```

Create 的目标是整张 `*_connected` Sprite。导出其完整纹理表，归一化 UV 仍停留在 Create 已选择的子格中。

### 6.5 缓存

- `SpriteTextureExtractor.Extraction` 按 canonical Sprite 身份缓存。
- `TextureRegistry` 继续按稳定 `TextureKey` 去重。
- 重定向诊断按“声明资源 ID → 目标资源 ID”组合去重，避免每个方块重复刷报告。

## 7. 草方块 Overlay 分流

### 7.1 分类规则

新增 `BlockPrimitiveRouter`。分类只依据解析后的稳定纹理资源 ID：

```text
minecraft:block/grass_block_side_overlay
    → GLOBAL_GRASS_SIDE_OVERLAY
其他纹理
    → SECTION_BLOCKS
```

不根据透明度、Tint、像素内容或几何共面关系猜测，以免误拆其他模组材质。

### 7.2 对象契约

整个选区最多产生一个对象：

```text
selection/grass_side_overlay
```

其 `CapturedNode.Kind` 使用新增的 `OVERLAY`，并写入：

```json
{
  "layerRole": "grass_side_overlay",
  "scope": "selection",
  "sourceTexture": "minecraft:block/grass_block_side_overlay"
}
```

没有草方块 Overlay 时不创建空对象。

### 7.3 材质与几何

- 使用原 `grass_block_side_overlay` RGBA 纹理。
- 保留 RenderType 推导出的 Alpha Mode 与双面策略。
- 保留 `tintindex: 0` 产生的群系顶点色。
- 保留原始 Quad 拓扑、法线、世界坐标和 UV。
- 主体草方块继续保留 `grass_block_side`、顶面与底面。
- Overlay 不计为占位符，不改变扫描方块与渲染方块计数。

### 7.4 流式写入

用户已选择整个选区一个 Overlay 对象，因此 Writer 必须跨 Section 合并逻辑对象，同时避免在捕获线程内囤积全部草地顶点。

#### glTF

- 每个 Section 可以提交同名 `OVERLAY` 节点片段。
- `StreamingGltfSession` 按稳定对象键复用同一 Node/Mesh。
- 每批 Primitive 的二进制顶点与索引仍立即写入 `.bin`。
- 文档构建器只持续追加 Primitive 描述，不保留历史顶点对象。

#### OBJ

- `StreamingObjSession` 将 Overlay Primitive 写入输出事务内的临时片段。
- 临时片段使用标准 OBJ 负相对索引，使其不依赖最终主体顶点偏移。
- `finish()` 时只写一次 `o selection_grass_side_overlay`，再追加片段。
- 临时片段随后删除；取消或异常时由 `OutputTransaction` 清理。

这样 Blender 中只出现一个草侧 Overlay 对象，同时保持大选区导出的有界内存特性。

## 8. GUI 键盘与输入法隔离

### 8.1 基本规则

- `ExportWandScreen.passEvents = false`。
- Esc 直接调用 Screen 关闭逻辑并返回 `true`。
- Enter 或小键盘 Enter 只提交当前聚焦的坐标端点或导出名并返回 `true`。
- Tab 只调用 GUI 焦点导航；无论父类返回值如何，Screen 最终返回 `true`。
- 其他物理按键不得进入 `AbstractContainerScreen` 的物品栏、热栏、丢弃或模组快捷键分支。

### 8.2 文本框路由

输入框聚焦时：

- `keyPressed` 直接调用当前 `EditBox.keyPressed`，随后无条件返回 `true`。
- `keyReleased` 直接调用当前焦点控件，随后无条件返回 `true`。
- `charTyped` 直接调用当前 `EditBox.charTyped`，随后无条件返回 `true`。

这允许 Ctrl+A/C/V/X、退格、Delete、方向键、英文字符和中文输入法提交字符作用于输入框。字符键的 `keyPressed` 即使被 `EditBox` 返回为未处理，也必须由 Screen 消费；真正文本仍通过后续 `charTyped` 写入。

### 8.3 无输入框焦点

除 Esc 和 Tab 外，E、WASD、数字热栏键、Q、F、模组快捷键及字符事件全部返回 `true`，不改变玩家、容器、物品栏或全局按键状态。

关闭 GUI 后不修改任何 `KeyMapping`，下一帧由 Minecraft 正常恢复游戏输入。

## 9. 诊断与错误处理

新增诊断码：

- `ATLAS_SPRITE_REDIRECTED`：INFO，记录声明 Sprite 与真实 Sprite。
- `ATLAS_SPRITE_RESOLUTION_FAILED`：WARNING，记录 Atlas、声明 Sprite 与 UV 包围盒。
- `GLOBAL_OVERLAY_SPOOL_FAILED`：FAILURE，记录临时片段阶段与异常。

规则：

- Sprite 重定向成功不使导出变成 `completed_with_warnings`。
- 反查失败保留声明 Sprite，继续导出其他对象。
- 单个 Overlay Quad 分类失败时留在 Section 主体，不丢弃几何。
- Overlay 临时片段写入失败属于输出事务失败，不发布不完整目录。
- glTF Overlay 合并键冲突、Kind 不一致或 Extras 不一致时失败，禁止静默合并语义不同的节点。
- GUI 输入隔离不捕获或吞掉系统级窗口关闭事件。

## 10. 自动测试设计

### 10.1 Sprite 解析

- 声明 Sprite 完整覆盖四个 UV时返回 `DECLARED`。
- Create 风格夹具：声明普通 Sprite、UV 指向 `*_connected`，返回目标 Sprite。
- 目标为 2×2、4×4 与 8×8 纹理表时保留正确局部 UV。
- 半像素边界容差不误判到邻接 Sprite。
- 多候选选择面积最小者，并用资源 ID 稳定打破平局。
- 无候选返回声明 Sprite并生成一次警告。
- 同一重定向重复出现时只记录一次 INFO。
- 普通动画 Sprite 保持现有首帧与动画旁车行为。

### 10.2 方块捕获与 Overlay

- 测试 BakedModel 返回“旧 Sprite 元数据 + 目标 Atlas UV”的 Quad，导出材质必须引用 connected TextureKey。
- 普通方块的材质与 UV 回归不变。
- `grass_block_side` 留在 Section 主体。
- `grass_block_side_overlay` 进入 `OVERLAY` 路由。
- Overlay 保留 MASK/BLEND、Tint 顶点色和 Quad 源拓扑。
- 无草方块时不创建空 Overlay。

### 10.3 Writer

- 多个 Section 的同名 Overlay 在 glTF 中只产生一个 Node/Mesh。
- OBJ 中只出现一次 `o selection_grass_side_overlay`。
- Overlay 的全部面、材质和顶点均存在。
- OBJ 负相对索引导入结构合法。
- Overlay 临时片段在成功、取消与异常后均不存在。
- glTF 继续通过内部校验，OBJ/MTL 路径保持有效。

### 10.4 GUI 输入

- 名称框聚焦时，`E` 的 `keyPressed` 不关闭 GUI，后续 `charTyped('e')` 写入文本。
- 中文字符通过 `charTyped` 写入并可由 `ExportName` 接受。
- 坐标框接受数字、负号、退格与方向键。
- Ctrl+A/C/V/X 只作用于当前输入框。
- Enter 只提交当前字段，不启动导出。
- Tab 只移动 GUI 焦点。
- Esc 关闭 GUI。
- E、WASD、数字热栏键、Q、F 与未绑定模组按键均被消费。
- `keyReleased` 不泄漏到游戏。
- GUI 关闭后不残留被修改的全局 KeyMapping 状态。

## 11. 人工视觉验收

人工视觉验收由项目所有者执行，自动化不得自行宣称视觉通过。

### 11.1 Create 对照

1. 在游戏中搭建至少包含安山机壳、黄铜机壳、流体储罐或保险库的连续结构。
2. 保存目标图或现场截图。
3. 导出同一选区并导入 Blender。
4. 对照每个边、角、中心格与纹理方向。
5. 检查连接纹理没有退回单块重复，也没有跨 Sprite 采样。

### 11.2 草方块

1. 导出包含多个生物群系色调草方块的选区。
2. Blender Outliner 中确认只有一个 `selection/grass_side_overlay`。
3. 隐藏该对象后，只剩底层泥土草边；恢复后视觉与游戏一致。
4. 检查 Overlay 没有被烘焙、丢色或拆成大量对象。

### 11.3 输入法与快捷键

1. 名称框输入包含 `e` 的英文名称。
2. 使用中文输入法输入中文导出名。
3. 分别按 E、WASD、1 至 9、Q、F 与已安装模组快捷键。
4. GUI 全程保持打开，玩家与物品栏无响应。
5. Esc 关闭后再次测试游戏快捷键，确认立即恢复。

## 12. 发布验收

候选版本只有在以下机械检查完成后才可交给人工视觉验收：

- 相关定向测试全部通过。
- `clean test build` 成功。
- `runServerSmoke` 成功。
- 生产 JAR 不包含 Create、Catnip 测试替身或 testmod 类。
- `jdeps` 不出现 Create/Catnip 字节码依赖。
- glTF 内部校验通过。
- OBJ/MTL 结构检查通过。
- 报告能追溯 Sprite 重定向与 Overlay 对象。

最终视觉通过结论只由项目所有者在真实 Create 场景和 Blender 中给出。
