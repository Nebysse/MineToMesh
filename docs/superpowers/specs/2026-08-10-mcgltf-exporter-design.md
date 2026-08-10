# MC glTF Exporter 设计规格

- 状态：已批准
- 日期：2026-08-10
- 目标版本：Minecraft 1.21.1
- 模组平台：NeoForge 21.1.248
- Java：21
- Mod ID：`mcgltf`
- 显示名称：`MC glTF Exporter`
- Java 包：`com.onecuber.mcgltf`
- 运行环境：仅物理客户端

## 1. 目标

制作一个 NeoForge 1.21.1 客户端模组，使玩家能够在游戏内通过两点选区指令，将当前客户端已加载的地图区域导出为 glTF 2.0 场景。

导出内容包括：

- 原版及其他模组的普通方块模型
- 原版及其他模组的流体
- 原版及其他模组的方块实体渲染内容
- 选区内普通实体的当前静态姿态
- 方块、流体、方块实体和实体实际使用的纹理
- 独立 glTF 材质及对应的独立 Minecraft 材质描述 JSON
- 缺失区块、兼容性降级和失败对象的结构化报告

导出结果以 Blender 编辑与渲染为首要使用场景。

## 2. 已确认的产品约束

### 2.1 选区方式

采用 WorldEdit 式两点选区：

```text
/mcgltf pos1
/mcgltf pos2
/mcgltf export <名称>
```

`pos1` 和 `pos2` 取玩家执行指令时所在的方块坐标。两个点共同定义包含边界的整数方块闭区间。

### 2.2 运行侧

模组仅在物理客户端加载。导出读取客户端实际启用的模组、资源包、烘焙模型、实体渲染器、纹理图集和动态纹理。

服务器无需安装本模组。客户端只能导出服务器已经发送并在客户端保持加载的数据。

### 2.3 实体范围

导出：

- 普通方块
- 流体
- 方块实体
- 普通实体
- 生物、载具、盔甲架、掉落物及模组实体

排除：

- 玩家
- 粒子
- 天气
- 天空和云
- 实体阴影
- 实体名称标签
- 调试包围盒

实体输出为捕获时刻的静态姿态，不输出 Minecraft 动画控制器或时间轴。

### 2.4 未加载区块

选区内未加载区块直接跳过。任务继续执行，并在 `report.json` 中记录缺失区块坐标和数量。

缺失区块与已加载区块的交界面按空气处理，保留已加载区域的边界表面。

### 2.5 光照

采用干净材质模式：

- 保留基础纹理
- 保留 Alpha
- 保留群系染色
- 保留实体颜色
- 保留发光语义
- 不烘焙天空光
- 不烘焙方块光
- 不烘焙 Minecraft 环境遮蔽

### 2.6 输出位置

默认输出目录：

```text
.minecraft/mcgltf-exports/<名称>/
```

已有同名目录时生成递增后缀，例如 `castle-2`、`castle-3`。正式导出目录不会被覆盖。

### 2.7 无法捕获的对象

无法通过标准 Minecraft/NeoForge 顶点通道取得几何时，生成半透明洋红色包围盒占位物。占位节点记录注册表 ID、坐标、渲染器类名和失败原因。

## 3. 技术方案结论

采用渲染感知型混合导出管线：

1. 普通方块从最终 `BakedModel/BakedQuad` 提取。
2. 流体通过 `BlockRenderDispatcher#renderLiquid` 和捕获型 `VertexConsumer` 提取。
3. 方块实体调用实际 `BlockEntityRenderer` 并捕获其 `MultiBufferSource` 输出。
4. 普通实体调用实际 `EntityRenderer` 并捕获其 `MultiBufferSource` 输出。
5. 直接 GPU 实例化、自定义 OpenGL 或无法解释的着色器路径降级为材质替代或占位物。

不以原始模型 JSON 解析作为主路径。NeoForge 自定义模型加载器、OBJ、Composite 和动态模型最终都会收敛为 `BakedModel`，读取最终烘焙结果具有更高兼容性。

不采用全帧 GPU 抓取。全帧抓取难以稳定恢复对象身份、Sprite 来源、RenderType 语义和独立材质。

## 4. 顶层模块

```text
command
   │
   ▼
job ──────► world
   │           │
   ▼           ▼
capture ──► scene ◄── material / texture
               │
               ▼
             gltf
               │
               ├─ report
               └─ output
```

### 4.1 `command`

负责客户端指令与选区状态：

```text
/mcgltf pos1
/mcgltf pos2
/mcgltf export <名称>
/mcgltf export <名称> confirm
/mcgltf status
/mcgltf cancel
```

选区与当前维度绑定。切换维度、退出世界或断开服务器时清空选区。

### 4.2 `job`

`ExportJobManager` 维护全局唯一导出任务：

```text
IDLE
→ PLANNING
→ CAPTURING
→ WRITING
→ COMPLETED | CANCELLED | FAILED
```

同一客户端同时只允许一个活动任务。任务提供进度、取消、异常隔离和聊天反馈。

### 4.3 `world`

负责：

- 规范化两个选区点
- 计算整数闭区间
- 枚举涉及的区块与区块截面
- 标记已加载和未加载区块
- 提供选区边界判断
- 提供流体渲染使用的选区裁切视图

该模块描述导出范围，不解析模型。

### 4.4 `capture`

包含：

- `BlockModelExtractor`
- `FluidGeometryCapture`
- `BlockEntityCapture`
- `EntityCapture`
- `CapturingMultiBufferSource`
- `CapturingVertexConsumer`
- `PlaceholderFactory`
- `RenderTypeInspector`

Minecraft 客户端渲染 API 和 Access Transformer 访问集中在此模块。

### 4.5 `scene`

纯 Java 中间模型：

```text
ExportScene
├─ SceneMetadata
├─ ChunkMesh
├─ CapturedObject
├─ MeshPrimitive
├─ VertexData
├─ MaterialKey
├─ TextureKey
└─ Diagnostic
```

`scene` 不依赖 `BlockState`、`Entity`、`BakedQuad` 或其他 Minecraft 类型。

### 4.6 `texture`

负责：

- 方块 Sprite 裁切
- 实体资源纹理读取
- 动态纹理像素复制
- UV 图集坐标局部化
- 纹理来源和输出路径注册
- 动画纹理静态预览及源文件保存

### 4.7 `material`

负责将 RenderType、纹理、透明、Cull、发光和采样语义映射到 glTF PBR 材质及独立材质 JSON。

### 4.8 `gltf`

包含：

- `BinaryBufferWriter`
- `GltfDocumentBuilder`
- `GltfExporter`

使用流式 `.bin` 写入和最终 JSON 组装，不在内存中构建整个世界的顶点对象树。

### 4.9 `report`

负责导出统计、缺失区块、兼容性降级、失败对象、耗时和任务状态。

## 5. 任务数据流

```text
导出指令
→ 参数与选区校验
→ 区块加载状态规划
→ 创建临时目录
→ 分时捕获实体与区块
→ 密封区块批次
→ 后台写入 BIN/PNG/JSON
→ 结构校验
→ 原子发布正式目录
```

### 5.1 客户端线程职责

客户端线程执行：

- 读取 `ClientLevel`
- 调用 BakedModel
- 调用流体、BER 和实体渲染器
- 读取纹理图集和动态纹理
- 将捕获结果转换为纯 Java 批次

### 5.2 后台线程职责

后台单线程执行：

- PNG 编码
- 材质 JSON 写入
- `.bin` 流式写入
- `.gltf` JSON 组装
- `report.json` 写入
- 最终目录发布

### 5.3 队列与预算

默认配置：

```text
captureBudgetMs = 6
writerQueueCapacity = 2
softBlockLimit = 4_194_304
```

每个客户端 Tick 最多消耗约 6 毫秒捕获预算。单个无法拆分的模组渲染器调用可以超过预算，其耗时必须进入报告。

超过软体积限制时，普通 `export` 只显示警告和确认指令；`export <名称> confirm` 才开始任务。

后台队列容量为两个密封批次。队列满时暂停捕获，避免内存无限增长。

### 5.4 捕获顺序

1. 先捕获选区内普通实体。
2. 区块按 X、Z 稳定排序。
3. 每个区块按 Y 方向 16 格截面处理。
4. 每个位置依次处理普通方块和流体。
5. 截面内方块实体生成独立对象节点。
6. 截面密封后提交后台线程。

稳定排序用于产生可比较、可测试的输出结构。

### 5.5 一致性

导出采用分时滚动快照。每个对象在处理时读取一次状态。大型导出期间世界若发生变化，不保证全部区块来自同一游戏 Tick。

报告记录：

- `captureStartGameTime`
- `captureEndGameTime`
- `snapshotMode: rolling`

## 6. 普通方块捕获

每个方块位置取得 `BlockState`、`BakedModel` 和 `ModelData`。

基础 ModelData 来自对应方块实体；随后调用模型的 `getModelData(level, pos, state, baseData)`，支持连纹理和模组动态数据。

按 `getRenderTypes(state, random, modelData)` 枚举渲染层。每个方向使用方块状态位置种子调用：

```text
getQuads(state, direction, random, modelData, renderType)
```

### 6.1 面剔除

- 选区内部使用原版 `Block.shouldRenderFace`
- 选区外视为空气
- 未加载相邻区块视为空气
- `direction == null` 的不可遮挡 Quad 始终保留
- 空气和 `RenderShape.INVISIBLE` 跳过
- `ENTITYBLOCK_ANIMATED` 由方块实体捕获器负责动态内容

### 6.2 Quad 转换

每个 Quad：

1. 应用方块状态的位置偏移。
2. 将图集 UV 转为 Sprite 局部 `[0,1]` UV。
3. 读取 BlockColors 群系染色。
4. 将原始顶点色与 Tint 相乘。
5. 忽略 AO、天空光和方块光。
6. 使用 4 个顶点和 6 个索引生成两个三角形。
7. 执行坐标变换、法线变换和绕序修正。

不执行全区块顶点哈希合并。每个 Quad 内复用 4 个顶点，避免误合并不同法线和 UV 的顶点。

## 7. 流体捕获

通过 `BlockRenderDispatcher#renderLiquid` 输出到捕获型 `VertexConsumer`。

默认传入选区裁切 `BlockAndTintGetter`：选区外返回空气和空流体，使水面与流体侧面在选区边界封口。

若模组流体错误地将接口强制转型为 `ClientLevel`：

1. 记录兼容性警告。
2. 使用真实 ClientLevel 重试。
3. 在报告中标记边界可能未封口。

流体纹理识别顺序：

1. `IClientFluidTypeExtensions` 提供的 Still、Flowing、Overlay Sprite
2. 根据一个图元的 UV 范围匹配候选 Sprite
3. 查询完整方块图集的 UV 区域索引
4. 使用白色纹理和顶点色降级

## 8. 方块实体捕获

取得实际 `BlockEntityRenderer`，使用局部原点 PoseStack 和捕获型 MultiBufferSource 调用。

普通方块的静态 BakedModel 与 BER 动态几何同时保留。方块实体输出为独立 glTF 节点，节点保存：

- 方块实体注册表 ID
- 世界坐标
- 局部坐标
- 渲染器类名
- 降级信息

## 9. 普通实体捕获

查询选区 AABB 内实体：

- 排除 Player
- 排除已移除实体
- 每个实体使用独立捕获上下文
- 保留盔甲、手持物、附加渲染层和绳索
- 忽略名称标签、阴影、火焰和调试线框
- Glint 降级为独立半透明发光层
- 当前模型或骨骼姿态烘焙为静态顶点

实体节点保存：

- 实体注册表 ID
- UUID
- 世界坐标
- 局部坐标
- 渲染器类名
- 降级信息

## 10. 顶点与拓扑

中间顶点格式：

```text
POSITION : float32 × 3
NORMAL   : float32 × 3
TEXCOORD : float32 × 2
COLOR_0  : uint8 normalized × 4
INDEX    : uint32
```

拓扑映射：

| Minecraft Mode | glTF Mode |
|---|---|
| QUADS | TRIANGLES |
| TRIANGLES | TRIANGLES |
| TRIANGLE_STRIP | TRIANGLE_STRIP |
| TRIANGLE_FAN | TRIANGLE_FAN |
| LINES | LINES |
| LINE_STRIP | LINE_STRIP |
| 其他模式 | 占位物 |

每个 MeshPrimitive 只对应一个 MaterialKey。普通方块按区块和材质合并；实体与方块实体保持独立节点。

## 11. 坐标系统

Minecraft 坐标转换为 glTF 右手坐标：

```text
(X, Y, Z) → (X, Y, -Z)
```

同时：

- 法线 Z 分量取反
- 三角形绕序反转
- 选区最小点作为局部原点
- 世界原点和完整选区坐标写入根节点 `extras`

一格对应一米。Blender glTF 导入器负责从 glTF Y-Up 转换到 Blender Z-Up。

## 12. 纹理

纹理不重新打包为图集。输出路径保持资源身份：

```text
textures/<namespace>/<path>.png
```

同一个资源位置只输出一次。不同资源位置即使像素相同，也保持独立文件。

动态纹理输出到：

```text
textures/generated/<sha256前16位>.png
```

动态纹理读取失败时：

- 几何继续保留
- 使用紫黑棋盘纹理
- 写入报告

### 12.1 动画纹理

- glTF 材质使用第一逻辑帧
- 原始动画 PNG 保存到 `textures/source/<namespace>/<path>.png`
- `.mcmeta` 保存到对应 PNG 旁的 `<文件名>.png.mcmeta`
- 材质 JSON 记录帧尺寸、序列和时长
- 报告标记 `ANIMATED_TEXTURE_STATIC_PREVIEW`

## 13. 材质

MaterialKey 由以下字段组成：

- TextureKey
- AlphaMode
- AlphaCutoff
- DoubleSided
- Emissive
- BlendSemantic
- SamplerMode

群系和实体颜色通过 `COLOR_0` 表达，不制造颜色材质变体。

默认 PBR：

```text
metallicFactor = 0
roughnessFactor = 1
baseColorFactor = [1, 1, 1, 1]
```

材质语义：

| Minecraft 语义 | glTF |
|---|---|
| solid | OPAQUE |
| cutout / cutout_mipped | MASK，alphaCutoff 0.5 |
| translucent | BLEND |
| eyes / emissive | emissiveTexture / emissiveFactor |
| glint | BLEND + Emissive 降级层 |
| 禁用 Cull | doubleSided |
| 未知自定义 RenderType | 根据透明、Cull、纹理状态推断 |

采样默认使用最近邻，保持像素边缘。

### 13.1 独立材质 JSON

每个 glTF 材质具有独立描述文件，至少包含：

```json
{
  "schemaVersion": 1,
  "name": "create_block_brass_casing_opaque",
  "gltfMaterialIndex": 12,
  "sourceTexture": "create:block/brass_casing",
  "exportedTexture": "../textures/create/block/brass_casing.png",
  "renderType": "solid",
  "alphaMode": "OPAQUE",
  "alphaCutoff": null,
  "doubleSided": false,
  "emissive": false,
  "sampler": "nearest",
  "degradations": []
}
```

## 14. glTF 场景结构

```text
MCGLTF_<名称>
├─ Chunks
│  ├─ Chunk_0_0
│  └─ Chunk_0_1
├─ BlockEntities
├─ Entities
└─ Placeholders
```

根节点 extras 保存：

- Minecraft 版本
- NeoForge 版本
- 当前维度
- 选区世界坐标
- 局部原点
- 资源包列表
- 已加载模组及版本
- 捕获开始和结束时间
- 生成器版本

## 15. 输出结构

```text
.minecraft/mcgltf-exports/<名称>/
├─ <名称>.gltf
├─ <名称>.bin
├─ textures/
│  ├─ <namespace>/<path>.png
│  ├─ generated/<hash>.png
│  └─ source/
├─ materials/
│  └─ <material-name>.json
└─ report.json
```

导出名称允许中文和常规 Unicode，统一为 NFC。拒绝：

- `/` 与 `\`
- 控制字符
- `.` 与 `..`
- Windows 保留名称
- 末尾空格或句点
- 超过 64 个 Unicode 码点的名称

## 16. 事务式输出

任务先写入：

```text
.minecraft/mcgltf-exports/.tmp-<UUID>/
```

全部文件成功关闭并通过内部结构检查后，再原子重命名为正式目录。

失败或取消时只清理本任务临时目录。已经存在的正式导出不会修改。

## 17. 错误策略

### 17.1 致命错误

以下错误终止任务：

- 未进入世界
- 选区不完整
- 选区跨维度
- 输出目录不可写
- 后台写入器失败
- glTF 索引关系不合法
- 内存耗尽
- 磁盘空间耗尽
- 世界退出、断开连接或资源包重载

### 17.2 可降级错误

以下错误继续任务：

- 单个模型或渲染器异常
- 单张纹理无法读取
- 自定义 RenderType 无法解释
- GPU 实例化对象没有顶点输出
- 动态纹理读取失败
- 不支持的顶点拓扑

降级顺序：

1. 几何有效、纹理失败：紫黑棋盘纹理。
2. 几何有效、材质未知：通用 PBR 材质。
3. 几何无效：半透明洋红色包围盒。
4. 节点 extras 和报告同时记录诊断信息。

## 18. 报告

`report.json` 至少包含：

```json
{
  "schemaVersion": 1,
  "status": "completed_with_warnings",
  "snapshotMode": "rolling",
  "selection": {
    "dimension": "minecraft:overworld",
    "min": [0, 64, 0],
    "max": [31, 95, 31],
    "localOrigin": [0, 64, 0],
    "volume": 32768
  },
  "captureStartGameTime": 123400,
  "captureEndGameTime": 123457,
  "counts": {
    "scannedPositions": 32768,
    "renderedBlocks": 12000,
    "renderedFluids": 300,
    "blockEntities": 8,
    "entities": 4,
    "materials": 24,
    "textures": 21,
    "triangles": 92000,
    "placeholders": 1
  },
  "missingChunks": [[2, 0]],
  "warnings": [],
  "failures": [],
  "timingsMs": {
    "planning": 4,
    "capture": 1200,
    "writing": 310,
    "total": 1514
  }
}
```

`scannedPositions` 统计已加载区块内实际访问的方块位置；`renderedBlocks` 与 `renderedFluids` 只统计产生有效几何的对象。每条诊断包含稳定错误代码、对象 ID、坐标、渲染器类名、异常类型和可读消息。

## 19. 测试策略

所有生产行为使用测试驱动开发。

### 19.1 纯 Java 单元测试

覆盖：

- 选区规范化
- Unicode 路径安全
- 坐标与法线变换
- 三角形绕序
- Quad 三角化
- UV 局部化
- 顶点色归一化
- MaterialKey 去重
- RenderType 语义映射
- BufferView 四字节对齐
- Accessor min/max
- 索引边界
- 状态机和取消
- 报告序列化

### 19.2 捕获适配器测试

使用可控假模型和假渲染器验证：

- 方块种子稳定性
- 内部面剔除
- 选区边界封口
- Tint Index
- ModelData 传递
- 多 RenderType 分材质
- VertexConsumer 最后顶点提交
- 全部支持的拓扑
- 异常和零顶点降级

### 19.3 开发客户端集成场景

测试场景包含：

- 石头、草方块、树叶、玻璃
- 水、岩浆
- 箱子、告示牌、旗帜
- 牛、盔甲架、掉落物、船
- 自定义动态 BakedModel
- 自定义 ModelData 模型
- 自定义透明 RenderType
- 自定义流体
- 自定义 BER
- 自定义实体渲染器
- 故意无法捕获的 GPU 路径替身

### 19.4 glTF 验证

端到端结果必须满足：

- URI 全部为相对路径
- 所有引用文件存在
- BufferView 四字节对齐
- Accessor 范围正确
- 索引有效
- 顶点属性无 NaN 或 Infinity
- 法线长度在误差范围内
- Primitive 材质引用有效
- Khronos glTF Validator 零错误

### 19.5 Blender 验收

- 无需手工重连纹理
- 场景层级清晰
- 群系颜色正确
- OPAQUE、MASK、BLEND 正确
- 实体保持静态姿态
- 一格一米
- 世界坐标可从 extras 恢复
- 独立纹理可替换

## 20. 性能验收

- 默认每 Tick 捕获预算约 6 ms
- 单次不可拆分调用超时会被记录
- 写入队列不超过两个批次
- 已写区块截面可释放顶点内存
- 不保存全场景顶点
- 内存复杂度约为当前截面、两个写入批次和全局索引之和
- 取消后不再产生新捕获批次
- 取消和失败不产生正式结果目录

## 21. 功能验收

- [ ] pos1 与 pos2 正确保存
- [ ] 跨维度选区被拒绝
- [ ] 未加载区块被跳过并报告
- [ ] 原版与模组 BakedModel 可导出
- [ ] 流体表面和选区边界正确
- [ ] 方块实体可导出
- [ ] 普通实体可导出
- [ ] 玩家被排除
- [ ] 纹理独立输出
- [ ] 材质 JSON 独立输出
- [ ] 无法捕获对象生成占位物
- [ ] Blender 可直接导入
- [ ] glTF Validator 零错误
- [ ] 失败不污染正式输出

## 22. 实施里程碑

1. NeoForge 工程与纯 Java glTF 核心。
2. 指令、选区和任务状态机。
3. BakedModel 方块与 Sprite 纹理闭环。
4. 流体、群系颜色和透明材质。
5. 方块实体与普通实体捕获。
6. 占位降级、报告、取消和事务输出。
7. 集成测试、Khronos Validator 和 Blender 验收。

每个里程碑必须产生可构建、可测试、可提交的状态。

## 23. 已知兼容性边界

| 对象 | 预期 |
|---|---|
| 原版及模组 JSON 方块 | 高 |
| NeoForge 自定义 BakedModel | 高 |
| OBJ、Composite、ModelData 模型 | 高 |
| 群系染色、随机变体、连纹理 | 高 |
| 标准及模组流体 | 高 |
| 标准 BER 与 EntityRenderer | 较高 |
| 通过 MultiBufferSource 输出的骨骼实体 | 较高，静态姿态 |
| Flywheel 或其他 GPU 实例化对象 | 占位物或适配器扩展 |
| 自定义着色器效果 | 基础材质降级或占位物 |

## 24. 参考资料

- [NeoForge 1.21.1 Getting Started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [NeoForge 1.21.1 BakedModel](https://docs.neoforged.net/docs/1.21.1/resources/client/models/bakedmodel/)
- [NeoForge 1.21.1 Custom Model Loaders](https://docs.neoforged.net/docs/1.21.1/resources/client/models/modelloaders/)
- [NeoForge 1.21.1 Block Entity Renderer](https://docs.neoforged.net/docs/1.21.1/blockentities/ber/)
- [NeoForge 1.21.1 Client Sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides/)
- [NeoForge 1.21.1 Access Transformers](https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers/)
- [NeoForge 1.21.1 RegisterClientCommandsEvent](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/event/RegisterClientCommandsEvent.java)
- [NeoForge 1.21.1 BlockRenderDispatcher Patch](https://github.com/neoforged/NeoForge/blob/1.21.1/patches/net/minecraft/client/renderer/block/BlockRenderDispatcher.java.patch)
- [NeoForge 1.21.1 IClientFluidTypeExtensions](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions.java)
- [Minecraft 1.21.1 EntityRenderDispatcher mappings](https://mappings.dev/1.21.1/net/minecraft/client/renderer/entity/EntityRenderDispatcher.html)
- [Minecraft 1.21.1 VertexConsumer mappings](https://mappings.dev/1.21.1/com/mojang/blaze3d/vertex/VertexConsumer.html)
- [Khronos glTF 2.0 Specification](https://github.com/KhronosGroup/glTF/tree/main/specification/2.0)
- [Khronos glTF Validator](https://github.com/KhronosGroup/glTF-Validator)
- [MiEx Minecraft World Exporter](https://github.com/BramStoutProductions/MiEx)
- [Flywheel](https://github.com/Engine-Room/Flywheel)
