# MineToMesh 0.4.0 导出魔杖设计

状态：设计已确认  
日期：2026-08-11  
目标版本：0.4.0  
Minecraft：1.21.1  
NeoForge：21.1.244

## 1. 目标

MineToMesh 0.4.0 使用可携带的“导出魔杖”替换区域导出工作台。每根魔杖独立保存一个选区，玩家可在世界中以 WorldEdit 风格快速设置 `pos1` 与 `pos2`，再通过 Shift+右键打开导出 GUI，执行现有 glTF 与 OBJ 导出管线。

本次版本属于破坏性替换：区域导出工作台的注册项、玩法入口、代码和资源全部硬删除，不保留旧世界兼容壳。

## 2. 已确认的产品规则

- 每根魔杖自身拥有选区，不使用玩家全局状态或世界外部存储。
- 左键方块设置 `pos1`，并完全阻止方块破坏与裂纹。
- 右键方块设置 `pos2`，并完全阻止方块自身交互。
- Shift+右键具有最高优先级，无论命中空气还是方块都只打开 GUI。
- Shift+左键空气直接清除当前魔杖选区。
- Shift+左键方块仍设置 `pos1`，不执行清除。
- 跨维度设置端点会被拒绝，原选区保持不变。
- GUI 坐标框保持可编辑，支持原有步进与滚轮微调。
- 删除两组“脚下”按钮，不在 GUI 增加清除按钮。
- 选区仅在手持对应魔杖且该魔杖开启显示时渲染。
- 世界取点、清除和维度拒绝只使用声音反馈，不发送聊天或动作栏文字。
- 专用服务器仍仅允许 OP 或原生指令权限等级不低于 2 的管理员执行导出。
- 普通玩家仍可制作魔杖、设置选区、打开 GUI 和查看 Overlay。
- 集成单人服务器无视导出权限。
- 新魔杖或选区不完整时仍可打开 GUI；选区完整前导出按钮禁用。

## 3. 范围

### 3.1 包含

- 新物品 `mcgltf:export_wand`。
- 自定义 ItemStack Data Component。
- 左键方块、右键方块、Shift+左键空气和 Shift+右键交互。
- 不依赖方块实体的物品菜单与 Screen。
- 魔杖数据的服务端权威修改与同步。
- 手持魔杖驱动的选区 Overlay。
- 16×16 工程测量标杆物品贴图。
- 可合成配方和创造模式页签入口。
- 对现有导出授权、进度与取消链路的物品化改造。
- 区域导出工作台的完整删除。

### 3.2 不包含

- 多选区列表、选区命名库或历史记录。
- 魔杖耐久、附魔、升级或品质系统。
- 跨维度选区。
- 多根魔杖同时显示 Overlay。
- 普通玩家的专服导出权限配置界面。
- 旧工作台存档迁移或 Missing Mapping 修复。
- 新的导出格式或导出算法修改。

## 4. 版本与兼容性

版本升级为 `0.4.0`。构建产物为：

```text
mcgltf-0.4.0.jar
```

0.4.0 会移除 `mcgltf:export_workstation` 的方块、物品、方块实体和菜单注册。含旧工作台的存档可能出现缺失注册项、方块丢失或加载警告；该风险已明确接受。

现有 glTF、OBJ、纹理捕获、进度统计、事务发布和关闭 GUI 取消语义保持不变。

## 5. 领域模型与 Data Component

### 5.1 `ExportWandSelection`

魔杖使用类型化自定义 Data Component 保存：

```text
wandId: Optional<UUID>
selectionDimension: Optional<ResourceLocation>
pos1: Optional<BlockPos>
pos2: Optional<BlockPos>
overlayEnabled: boolean
exportName: String
```

约束：

- 物品最大堆叠数为 1。
- `wandId` 初始为空，在首次服务端使用时生成，并随 ItemStack 保存与同步。
- `pos1` 和 `pos2` 各自以完整 `BlockPos` 为最小持久化单位。
- 任一端点存在时，`selectionDimension` 必须存在。
- 两个端点只能属于 `selectionDimension` 指定的同一维度。
- 两个端点均不存在时，`selectionDimension` 必须为空。
- `overlayEnabled` 默认值为 `true`。
- `exportName` 默认值为 `export`。
- Data Component 必须同时提供持久化 Codec 与网络 StreamCodec。

创造模式复制可能复制同一 `wandId`。菜单身份验证同时绑定手、槽位和 `wandId`，因此克隆物品不会跨槽位获得当前菜单的修改权限。

### 5.2 `ExportWandService`

所有魔杖数据写入集中在服务端服务层：

- `ensureIdentity(stack)`
- `setEndpoint(player, hand, endpoint, blockPos)`
- `clearSelection(player, hand)`
- `updateEndpoint(player, binding, endpoint, blockPos)`
- `setOverlayEnabled(player, binding, enabled)`
- `setExportName(player, binding, exportName)`
- `snapshotForExport(player, binding)`

服务层负责物品身份、维度、构建高度、导出名和权限验证。失败操作不得部分修改 Data Component。

## 6. 输入优先级与交互

| 输入 | 命中目标 | 行为 | 原版行为 |
|---|---|---|---|
| 左键 | 方块 | 设置 `pos1` | 取消破坏与裂纹 |
| Shift+左键 | 方块 | 设置 `pos1` | 取消破坏与裂纹 |
| 左键 | 空气 | 无魔杖操作 | 保持无结果 |
| Shift+左键 | 空气 | 清除选区 | 每次按键只执行一次 |
| 右键 | 方块 | 设置 `pos2` | 取消方块交互 |
| 右键 | 空气 | 无魔杖操作 | 不打开 GUI |
| Shift+右键 | 方块或空气 | 打开导出 GUI | 不设置 `pos2`，不触发方块 |

### 6.1 左键方块

使用 NeoForge 左键方块事件在客户端和服务端取消破坏。服务端读取玩家实际命中的方块位置并更新当前手持魔杖，客户端只负责即时阻断视觉破坏。

### 6.2 右键

`ExportWandItem` 的方块使用与空气使用逻辑共享 Shift 优先规则：

- Shift 状态为真时请求服务端打开魔杖菜单。
- 未按 Shift 且命中方块时设置 `pos2` 并消费交互。
- 未按 Shift 且命中空气时返回无操作。

### 6.3 Shift+左键空气

Minecraft 没有等价的服务端方块命中事件。客户端监听攻击键映射触发，只在“Shift、魔杖在手、准星未命中方块”的组合下发送清除请求。服务端重新验证当前手中的魔杖后执行清除。按住攻击键不得逐 Tick 重复清除。

## 7. 维度与坐标规则

- 首次设置任一端点时，把当前维度写入 `selectionDimension`。
- 后续端点设置必须发生在相同维度。
- 跨维度请求被拒绝，不改变原有维度和端点。
- 清除操作同时移除维度、`pos1` 和 `pos2`。
- 清除操作保留 `overlayEnabled` 与 `exportName`；因为端点为空，Overlay 自然没有可渲染几何。
- GUI 中未设置的端点显示三个空输入框。
- Screen 可在本地暂存不完整文本；只有 X/Y/Z 三轴均为合法整数时才提交完整端点。
- 服务端验证 Y 值位于当前维度构建高度范围内。
- 体积计算继续使用溢出安全的 `long` 运算，不设置硬上限。

## 8. 声音反馈

世界交互不发送动作栏或聊天文字。

- `pos1` 设置成功：`minecraft:block.note_block.hat`，pitch `0.75`。
- `pos2` 设置成功：`minecraft:block.note_block.hat`，pitch `1.25`。
- 清除成功：`minecraft:block.beacon.deactivate`，pitch `1.0`。
- 跨维度或非法操作：`minecraft:entity.villager.no`，pitch `1.0`。
- GUI 打开：`minecraft:item.book.page_turn`，pitch `1.1`。

音量统一为 `0.6`，仅对操作玩家播放。全部复用原版 SoundEvent，不增加自定义音频资产。

## 9. 物品菜单与 GUI

### 9.1 `ExportWandMenu`

菜单在服务端打开，并绑定：

```text
hand
inventorySlot
wandId
```

`stillValid` 必须验证绑定槽位仍持有 `mcgltf:export_wand` 且 `wandId` 一致。魔杖被移动、替换、丢弃或不再位于绑定槽位时菜单失效。

菜单失效或关闭时：

- 取消本 Screen 所拥有的等待授权状态。
- 取消本 Screen 启动且尚未终止的导出任务。
- 不删除已经发布成功的导出目录。

### 9.2 `ExportWandScreen`

沿用现有 384×216 GUI 框架与九宫格渲染策略：

- 标题改为“导出魔杖”。
- 删除两个“脚下”按钮。
- 坐标输入框宽度由约 82px 扩展到约 116px。
- 保留六坐标输入、上下步进、滚轮 `±1`、Shift+滚轮 `±10`。
- 保留选区显示开关。
- 保留导出名、导出、取消、进度、摘要和底部状态区。
- 不增加 GUI 清除按钮。
- 九宫格固定边框继续按 8 个物理屏幕像素换算 GUI Scale。
- 固定角不拉伸，边缘单轴拉伸，中心安全区双轴拉伸。

新魔杖或不完整选区允许打开 GUI。导出按钮只有在两个端点完整、维度有效、导出名合法且无活动任务时才启用。

### 9.3 坐标与导出 Payload

所有 GUI 写入和导出请求携带菜单绑定身份。服务端从当前绑定 ItemStack 读取真实数据，不接受客户端提交完整导出快照作为权威来源。

导出 Grant 包含服务端冻结的：

```text
wandId
selectionDimension
pos1
pos2
exportName
```

客户端仅在当前 Screen 仍打开、菜单绑定匹配且维度一致时启动现有导出管线。

## 10. 选区 Overlay

Overlay 每帧检查玩家当前主手与副手：

- 只选择当前实际手持的导出魔杖。
- `overlayEnabled` 必须为真。
- `pos1`、`pos2` 与维度必须完整且匹配当前客户端维度。
- 同一时刻只渲染一根魔杖的选区；主手优先于副手。
- 切换到其他物品立即隐藏。
- 再次拿回同一根魔杖时根据其 Data Component 恢复。

视觉保持现有规则：橙色半透明体积、蓝色辅助边线、正常深度遮挡。

## 11. 权限

权限仅限制“开始导出”，不限制制作、取点、打开 GUI 或查看 Overlay。

- 集成单人服务器：放行。
- 专用服务器：要求 `player.createCommandSourceStack().hasPermission(2)`。
- 权限不足：服务端返回本地化拒绝原因，GUI 显示失败状态，不启动客户端导出任务。

## 12. 视觉与资源

### 12.1 物品视觉

物品贴图为 16×16 像素工程测量标杆：

- 金属灰主体。
- 橙色代表 `pos1`。
- 蓝色代表 `pos2`。
- 轮廓强调测绘仪、标尺和工程工具语义。
- 不使用高魔法水晶风格。

资源路径：

```text
assets/mcgltf/models/item/export_wand.json
assets/mcgltf/textures/item/export_wand.png
```

### 12.2 GUI 资源迁移

77 张已批准的独立切片迁移为：

```text
assets/mcgltf/textures/gui/export_wand/gui_001.png
assets/mcgltf/textures/gui/export_wand/gui_002.png 至 gui_076.png
assets/mcgltf/textures/gui/export_wand/gui_077.png
```

纹理常量类改名为 `ExportWandTextures`。生产界面不得引用旧整图 `textures/gui/export_workstation.png`。

## 13. 合成与创造栏

精确有序配方：

```text
_ _ A
_ R C
S _ _
```

JSON pattern：

```json
[
  "  A",
  " RC",
  "S  "
]
```

材料：

- `A`：`minecraft:amethyst_shard`
- `R`：`minecraft:redstone`
- `C`：`minecraft:copper_ingot`
- `S`：`minecraft:stick`

产出：

```text
1 × mcgltf:export_wand
```

魔杖不可堆叠、无耐久，不提供附魔能力。MineToMesh 创造模式页签只展示导出魔杖，并使用魔杖作为图标。

## 14. 工作台硬删除清单

删除以下注册与实现：

- `mcgltf:export_workstation` 方块。
- `mcgltf:export_workstation` BlockItem。
- `mcgltf:export_workstation` 方块实体类型。
- `mcgltf:export_workstation` 菜单类型。
- `ExportWorkstationBlock`。
- `ExportWorkstationBlockEntity`。
- `ExportWorkstationMenu`。
- 工作台专用请求、坐标同步和脚下捕获 Payload。
- 工作台专用 Screen、Controller 与纹理类，功能迁移后旧类删除。
- 工作台方块状态、方块模型、物品模型、五面方块贴图。
- 工作台战利品表、合成表和挖掘标签资源。
- 工作台语言键。
- `textures/gui/export_workstation.png`。
- `textures/gui/workstation/` 旧目录。

可复用的纯值对象或渲染算法在重命名并解除工作台依赖后保留，例如坐标编辑模型、九宫格边框策略和 Overlay 几何逻辑。

## 15. 错误处理

- 未持有魔杖的请求：忽略或返回无效物品拒绝。
- 槽位、手或 `wandId` 不匹配：菜单失效，拒绝修改。
- 跨维度取点：播放拒绝音，数据不变。
- 非法坐标文本：保留本地草稿并标红，不发送部分端点。
- 构建高度越界：服务端拒绝，GUI 显示本地化失败原因。
- 选区不完整：导出按钮禁用；服务端仍执行防御性拒绝。
- 权限不足：返回 `mcgltf.error.wand.no_export_permission`。
- 已有活动任务：返回 already-running 拒绝。
- Screen 关闭、菜单失效、退出世界、切换维度或资源重载：取消尚未终止的任务。
- 网络回包处理器保持进程生命周期常驻，不得在退出世界时重置为空函数。

## 16. 测试设计

### 16.1 单元与集成测试

- Data Component 持久化 Codec 与 StreamCodec 往返。
- 新魔杖默认值与首次身份生成。
- 单端点、完整选区、清除和体积计算。
- 跨维度拒绝且原数据不变。
- 左键方块取消破坏并设置 `pos1`。
- 右键方块取消交互并设置 `pos2`。
- Shift 输入优先级。
- Shift+左键空气单次清除。
- GUI 绑定手、槽位与 `wandId`。
- 移动、替换或丢弃魔杖后菜单失效。
- GUI 坐标草稿在三轴完整后写回 ItemStack。
- 多根魔杖的数据隔离。
- 主手优先和仅手持时 Overlay 显示。
- 单人放行、专服管理员放行、普通玩家拒绝。
- 配方、物品模型、16×16 贴图、创造栏与语言资源。
- 77 张 GUI 切片存在且哈希可追溯。
- 工作台注册、类、Payload 和资源完全不存在。
- 客户端类不会泄漏到专用服务器加载路径。
- 退出并重新进入世界后 Grant/Reject 回包仍可处理。

### 16.2 手工验收

1. 左键方块无裂纹且不破坏，`pos1` 被设置。
2. 右键箱子、门、按钮或其他交互方块只设置 `pos2`。
3. Shift+右键空气和方块均打开 GUI。
4. Shift+左键空气清除；Shift+左键方块仍设置 `pos1`。
5. 主世界绑定后在下界取点被拒绝，原选区不变。
6. 两根魔杖保存不同选区与导出名。
7. 切换手持物品时 Overlay 即时隐藏与恢复。
8. GUI 打开后移动、替换或丢弃魔杖，菜单自动关闭并取消任务。
9. 退出世界再进入后授权回包正常。
10. glTF 与 OBJ 实际导出完成，可在 Blender 中打开。
11. Dedicated Server 启动不链接客户端类。
12. 普通玩家不可导出，管理员与单人模式可导出。

## 17. 发布验收

0.4.0 只有在以下条件全部满足时才可交付：

- `clean test build` 全绿。
- 专用服务器类隔离测试全绿。
- `runServerSmoke` 输出 `MINETOMESH_SERVER_READY`，并显示 MineToMesh 0.4.0。
- 生产 JAR 名为 `mcgltf-0.4.0.jar`。
- JAR 内 `neoforge.mods.toml` 版本为 `0.4.0`、side 为 BOTH。
- JAR 含导出魔杖物品模型、贴图、配方和 77 张 GUI 切片。
- JAR 不含 testmod、设计源文件、`.superpowers`、旧工作台类和旧工作台资源。
- 实机完成“进入世界、退出、重新进入、取点、打开 GUI、导出”的完整闭环。
