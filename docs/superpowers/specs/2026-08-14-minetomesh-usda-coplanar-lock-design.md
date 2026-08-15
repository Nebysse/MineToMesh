# MineToMesh USDA、共面分层与持久选区设计

## 1. 背景

MineToMesh 当前同时输出 glTF 2.0 与 OBJ。glTF 适合运行时交换，但标准拓扑只支持三角形、点和线；OBJ 可以保留 Quad，却缺少可靠的场景层级、实例和现代材质表达。Blender 等 CG 软件还会把 Minecraft 原始模型中完全共面的多层几何渲染成黑斑或闪烁，例如零厚度加速铁轨以及草方块底色层与 Tint Overlay。

本次改动增加文本 OpenUSD（USDA）作为 CG 制作格式，立即移除当前 OBJ/MTL 输出，并在格式分流前修正普通方块模型中的完全重合四边面。同时，法杖界面增加客户端持久“锁定选区”，使选区框在法杖不在手中、重新连接或重启客户端后仍可恢复。

## 2. 已确认决策

- 每次导出生成 glTF、BIN、USDA、纹理、材质 Sidecar 与报告。
- OBJ/MTL 立即移除，不保留隐藏开关或过渡版本。
- 历史发布记录继续保留，当前 README、测试矩阵和实现文档改为 glTF/USDA。
- USDA 使用 `.usda` 文本文件和外部 PNG，不生成 USDC 或 USDZ。
- USDA 保留 Minecraft 原始 Quad；glTF 继续按规范三角化。
- 完全重合面全部保留，后续层沿各自法线微量偏移。
- 重合检测只作用于普通方块 `BakedQuad`，不修改流体、实体和方块实体 Renderer 输出。
- 新增“锁定选区”客户端选项，与现有手持预览独立。
- 每个单人世界或多人服务器只保存一个最后锁定选区；新锁定覆盖旧记录。
- 锁定选区跨重启持久化，并按世界或服务器隔离。

## 3. 非目标

- 不跨相邻方块合并 Quad 或构造大 N-gon。
- 不检测只有部分面积相交的任意共面多边形。
- 不对近似共面几何使用容差匹配。
- 不修改动态 Renderer、流体或实体的几何拓扑。
- 不接入 OpenUSD 原生 C++ 运行库。
- 不生成 USDC、USDZ、Alembic、COLLADA 或 PLY。
- 不为旧 OBJ 调用方保留 Java API 兼容层。
- 不改变 glTF 的标准三角拓扑与 Khronos 校验要求。

## 4. 总体架构

三条链路相互独立：

```text
普通方块 BakedQuad
  → 完全重合检测与分层偏移
  → PrimitiveAccumulator
  → 不可变 ChunkBatch
      ├─ StreamingGltfSession
      └─ StreamingUsdaSession

法杖 GUI 当前选区
  → LockedSelectionStore
  → 按世界/服务器持久化
  → SelectionOverlayRenderer
```

`StreamingSceneSession` 同时管理 glTF 与 USDA Writer。任一 Writer 打开、追加或完成失败时，整个 `OutputTransaction` 回滚，不发布半套场景。

输出目录固定为：

```text
<导出名>/
├─ <导出名>.gltf
├─ <导出名>.bin
├─ <导出名>.usda
├─ textures/
├─ materials/
└─ report.json
```

## 5. 完全重合四边面处理

### 5.1 处理位置与范围

处理发生在 `BlockModelExtractor` 收集完单个方块的全部 `PendingStream` 后、路由到普通 Section 或全局草地 Overlay 之前。这样草方块底色层与 Overlay 能在拆分到不同 Accumulator 之前参与同一轮检测。

检测仅处理四顶点方块面。每个方块单独分组，避免把相邻方块共享边界误判为同一重合组。

### 5.2 精确几何指纹

每个四边面生成 `FaceGeometryKey`：

1. 读取四个顶点的位置。
2. 将 `-0.0F` 归一化为 `0.0F`。
3. 使用 `Float.floatToIntBits()` 表示每个坐标。
4. 将四个 XYZ 元组按字典序排序。
5. 排序结果组成不可变 Key。

Key 不包含绕序、法线、UV、材质、颜色、透明模式或 Overlay 路由。因此同向覆盖层、反向零厚度双面以及不同材质的完全共面 Quad 都会进入同一组。

第一版不使用容差。坐标只要存在任意可表示差异，就不会命中。

### 5.3 偏移规则

同组面保持原捕获顺序：

```text
第 0 层：0
第 1 层：normal × 1/1024
第 2 层：normal × 2/1024
第 N 层：normal × N/1024
```

每层法线优先使用四个顶点法线的归一化平均值。平均值无效时使用顶点位置叉积计算几何法线，并与可用顶点法线保持同向。两种法线都无效时不移动该层，并记录 `COPLANAR_FACE_NORMAL_INVALID`。

偏移只替换 `Vertex.position`，保留法线、UV、Color、MaterialKey、PrimitiveMode、绕序和路由。

### 5.4 统计与报告

`report.json` 升级 Schema，增加：

```json
"geometryAdjustments": {
  "coplanarGroups": 18,
  "offsetFaces": 21,
  "maxLayers": 3,
  "byBlock": {
    "minecraft:grass_block": 12,
    "minecraft:powered_rail": 4
  }
}
```

普通命中只累计统计，不逐面生成 Diagnostic。只有无法计算位移法线等异常路径写 Diagnostic，避免大面积草地造成报告膨胀。

## 6. USDA Stage 契约

### 6.1 Stage 元数据

USDA 头部为：

```usda
#usda 1.0
(
    defaultPrim = "MineToMesh"
    metersPerUnit = 1
    upAxis = "Y"
)
```

采用 Y-Up，使 Blender 导入后的坐标继续满足：

- Minecraft `+X` 对应 Blender `+X`。
- Minecraft `+Y` 对应 Blender `+Z`。
- Minecraft `+Z` 对应 Blender `-Y`。

所有多边形 Mesh 写入：

```usda
uniform token subdivisionScheme = "none"
```

避免 DCC 将 Minecraft Quad 解释为 Catmull-Clark 细分曲面。

### 6.2 场景层级

Stage 使用以下路径：

```text
/MineToMesh
├─ Chunks
├─ BlockEntities
├─ Entities
├─ Placeholders
├─ Overlays
└─ Materials
```

`/MineToMesh` 通过自定义属性保存 Minecraft 版本、NeoForge 版本、导出器版本、维度、选区、原点、资源包、模组列表、快照模式和导出选项。

普通捕获节点保持独立 Xform。节点中的表面 Primitive 合并为一个或少量 Mesh，并用 `UsdGeomSubset` 按完整 `MaterialKey` 绑定材质。由于 USD 的 `doubleSided` 属于 Gprim，至少按双面属性分桶，不能把不同双面语义强行放入同一 Mesh。

方块实体继续按完整 `MaterialKey` 全局合批；普通实体保持独立；草侧 Overlay 合并为一个选择级对象。

### 6.3 拓扑映射

| 内部模式 | USDA 表达 |
|---|---|
| `QUADS` | `faceVertexCounts` 每项为 4 |
| `TRIANGLES` | `faceVertexCounts` 每项为 3 |
| `TRIANGLE_FAN` | 保持绕序展开为三角面 |
| `TRIANGLE_STRIP` | 保持奇偶绕序展开为三角面 |
| `LINES` | 线性、非周期 `BasisCurves` |
| `LINE_STRIP` | 线性、非周期 `BasisCurves` |

当前捕获层没有通用 N-gon PrimitiveMode；USDA 数据模型允许未来直接写入任意 `faceVertexCounts`，本次不跨方块重建 N-gon。

### 6.4 顶点属性

Mesh 输出：

- `points`
- `normals`
- `faceVertexCounts`
- `faceVertexIndices`
- `primvars:st`
- `primvars:minetomeshTint`

UV、法线与 Tint 使用 `faceVarying`，保留面角接缝。USD 与 glTF 的纹理纵向约定不同，USDA 写入 `st` 时使用：

```text
st.x = uv.x
st.y = 1.0 - uv.y
```

### 6.5 材质网络

每个完整 `MaterialKey` 对应一个稳定命名的 `UsdShadeMaterial`。名称由可读前缀和完整键哈希组成，避免相同纹理名但不同透明、发光或采样语义被误合并。

材质使用标准节点：

```text
UsdPreviewSurface
├─ UsdUVTexture
├─ UsdPrimvarReader_float2    读取 st
└─ UsdPrimvarReader_float4    读取 minetomeshTint
```

`UsdUVTexture.inputs:scale` 连接 Tint Primvar，使纹理采样与群系颜色相乘。纹理 RGB 连接 `diffuseColor`；Alpha 连接 `opacity`。MASK 材质写入 `opacityThreshold`，BLEND 材质保持连续透明；发光材质同时连接 `emissiveColor`。

Mesh 的 `doubleSided` 按分桶后的材质语义写入。

UsdPreviewSurface 没有跨 DCC 统一的最近邻过滤字段。USDA 写入自定义 `minetomesh:samplerMode`，并继续在 `materials/` Sidecar 中保存精确采样语义。Blender 5.2 人工验收最近邻恢复情况；若原生导入器不读取该信息，README 明确要求把 Image Texture 插值设为 Closest。本次不伪造非标准 USD Shader。

### 6.6 流式写入

`StreamingUsdaSession` 不在内存中持有完整世界。它为类别、全局 Overlay 和按材质合批的方块实体维护受控临时片段，例如：

```text
.<名称>-chunks.usdapart
.<名称>-block-entities-<材质>.usdapart
.<名称>-overlay.usdapart
```

每个 `ChunkBatch` 只追加文本片段、数组元素和计数。`finish()` 组装 Stage 头、根节点、类别、材质与闭合括号，并验证数组长度、面计数、索引范围和引用路径。

完成、取消、构造失败和中途异常路径都必须关闭 Writer 并删除 `.usdapart`。

## 7. OBJ 移除

删除当前生产代码：

- `StreamingObjSession`
- `ObjTopologyConverter`
- `ObjNames`
- `StreamingSceneSession` 中的 OBJ 生命周期和统计
- 当前 README、测试矩阵、输出事务断言与文档中的现行 OBJ 契约

删除对应当前测试，并用 USDA 等价测试覆盖 Quad、材质、合批、Overlay、取消和临时文件清理。旧版本 `docs/releases/` 保持原文，因为它们描述当时真实发布内容。

根 Extras 的 `formats` 改为 `["gltf", "usda"]`，移除 `sourceTopologyPreservedInObj`，增加 `sourceTopologyPreservedInUsda=true`。

## 8. 持久锁定选区

### 8.1 数据所有权

现有 `ExportWandSelection.overlayEnabled` 继续控制手持法杖预览。锁定选区是纯客户端状态，不加入法杖 DataComponent，不增加网络 Payload，也不修改服务端授权流程。

新增 `LockedSelectionStore` 保存每个世界或服务器最后锁定的一个完整选区。

### 8.2 配置格式

配置路径：

```text
.minecraft/config/minetomesh/locked-selections.json
```

格式：

```json
{
  "schemaVersion": 1,
  "profiles": {
    "<SHA-256上下文键>": {
      "dimension": "minecraft:overworld",
      "pos1": [0, 64, 0],
      "pos2": [32, 96, 32]
    }
  }
}
```

上下文原文为：

- 单人模式：规范化存档根路径。
- 多人模式：规范化服务器地址与端口。

文件只保存 SHA-256，不明文保存路径或服务器地址。每个 Profile 只有一个记录，维度包含在记录中；在另一维度锁定会覆盖原记录。

### 8.3 原子写入与损坏恢复

写入流程为同目录临时文件、flush、原子移动替换。文件系统不支持原子移动时退化为受控替换。

只有磁盘写入成功后才更新内存状态。写入失败时保留旧文件和旧内存记录，并在 GUI 状态栏显示失败。

配置解析失败时将原文件隔离为带时间戳的 `.corrupt` 文件，加载空 Store 并记录日志；手持预览继续工作。

### 8.4 GUI 行为

现有底部一行改成三个等宽开关：

```text
手持预览：开 | 锁定选区：关 | 导出玩家：关
```

锁定按钮规则：

- 当前完整选区等于当前 Profile 的锁定记录：点击后解锁。
- 当前完整选区与锁定记录不同：点击后覆盖为当前选区。
- 当前选区不完整：拒绝操作并显示状态提示。
- 锁定状态以“当前法杖选区是否等于持久记录”为准。

### 8.5 渲染解析

`SelectionOverlayRenderer` 同时解析：

1. 当前主手或副手法杖的手持预览。
2. 当前世界或服务器 Profile 的持久锁定记录。

两个 Snapshot 完全相同时只绘制一次；不同时同时显示。锁定记录的维度与当前维度不匹配时隐藏，返回原维度后恢复。无法建立当前世界 Profile 时只禁用持久记录，不影响手持预览。

## 9. 错误处理与事务语义

- glTF 或 USDA 任一 Writer 失败，整个导出事务失败并清理临时目录。
- USDA 数组计数、拓扑和引用在发布前执行内部验证。
- glTF 继续执行现有内部校验与 Khronos Validator 验收。
- 重合面异常法线只影响该层位移，并写 Diagnostic，不中断整个方块捕获。
- 持久配置读写失败不影响世界渲染、法杖网络状态或导出任务。
- Profile 不可解析、维度不匹配或记录不完整时不渲染锁定框。

## 10. 测试策略

所有实现遵循 TDD，先增加能重现缺失行为的失败测试，再写最小实现。

### 10.1 共面分层自动化测试

- 同向、反向、旋转绕序、逆序及不同材质的完全重合 Quad 命中同组。
- `-0.0F` 与 `0.0F` 等价。
- 第一层不移动，后续层按 `1/1024` 递增。
- 每层沿自己的法线移动。
- 接近但不完全相同的面不移动。
- 三层及以上统计正确。
- 无效平均法线使用几何法线；完全退化时记录 Diagnostic。
- 普通方块集成路径在路由 Overlay 前执行分层。

### 10.2 USDA 自动化测试

- Quad 写出一个四边面，glTF 同源数据写出两个三角面。
- Triangle、Fan、Strip、Lines 与 LineStrip 映射正确。
- `subdivisionScheme="none"`、Y-Up、单位、层级和根元数据正确。
- UV 执行 `1-v`，法线与 Tint 使用 `faceVarying`。
- PreviewSurface、纹理、Tint、Alpha Threshold、发光和双面语义正确。
- 多材质节点使用合法 GeomSubset。
- 方块实体全局材质合批与 Overlay 全局合并正确。
- 数组计数或索引非法时拒绝发布。
- 完成、取消和异常后无 `.usdapart` 残留。
- 输出目录不存在 `.obj` 与 `.mtl`。

### 10.3 持久选区自动化测试

- 单人存档、不同服务器和相同服务器各自隔离。
- 地址与路径规范化后生成稳定哈希。
- 写入、重载、覆盖和解锁正确。
- 重启后恢复，维度不匹配时隐藏，返回后恢复。
- 新选区覆盖旧记录。
- 损坏配置隔离且不崩溃。
- 写入失败时不更新内存锁定状态。
- 手持与锁定 Snapshot 相同时去重，不同时均保留。
- 不完整选区无法锁定。

### 10.4 回归测试

- 全量 JUnit 测试。
- `compileJava`、`compileTestJava` 与资源检查。
- 专用服务端启动烟测，确保客户端持久 Store 和 USD Writer 不污染服务端类加载。
- README 与手工矩阵策略测试更新为 glTF/USDA。

## 11. Blender 5.2 人工验收

- USDA 导入后原版方块保持 Quad。
- 加速铁轨无黑斑、闪烁或完全共面层。
- 草方块底色层与 Tint Overlay 均保留。
- 作物外观没有可见变化。
- Minecraft 与 Blender 坐标轴、原点、尺度、绕序和法线正确。
- PNG、群系 Tint、Cutout、透明与发光材质可用。
- Create 传送带等动态方块实体层级和材质合批正确。
- Outliner 包含固定类别层级，草侧 Overlay 只有一个选择级对象。
- 锁定选区跨客户端重启和重新连接恢复。
- 不同存档或服务器不会显示其他 Profile 的锁定框。
- 维度切换隐藏并在返回后恢复。
- glTF 继续通过 Khronos Validator，USDA 可由 Blender 5.2 无错误导入。

## 12. 完成标准

以下条件全部满足后才视为完成：

1. 所有自动化测试通过。
2. 构建和专用服务端烟测通过。
3. 输出只包含 glTF/BIN/USDA 及共享附属文件，不再生成 OBJ/MTL。
4. USDA 在 Blender 5.2 中保留 Quad、层级、坐标与材质核心语义。
5. 铁轨和草地共面案例完成视觉验收。
6. 持久锁定选区完成跨重启、跨维度与 Profile 隔离验收。
7. 临时片段、失败事务和损坏配置均按设计清理或隔离。
8. README、测试矩阵和报告 Schema 与实际行为一致。
