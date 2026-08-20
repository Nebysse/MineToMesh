# MineToMesh

MineToMesh 是 Minecraft 世界导出模组，将客户端当前已加载的选区写成 Blender 可编辑的 glTF 2.0 与文本 OpenUSD（USDA）场景。当前同时维护 Minecraft 1.21.1 NeoForge 正式版 `1.3.0` 与 Minecraft 26.2 Fabric Alpha `1.3.0-fabric-alpha.1`。1.2.0 增加精确共面 Quad 分层，解决 Powered Rail 和草方块叠层在 Blender 中发黑或闪烁的问题；1.3.0 增加超视距选区滚动导出：服务端按紧凑批次强制加载、冻结全服随机刻、临时切换追踪中心，并支持可中途停止的完整生命周期进度。USDA 保留源 Quad，并通过 PreviewSurface 材质引用外部 PNG。导出魔杖负责保存和编辑选区，服务端权威校验物品身份、坐标与权限，客户端负责渲染捕获、纹理读取和文件写入。

## 特性

- 客户端和服务端均需安装同一平台、同一版本的 MineToMesh。
- NeoForge 正式版目标为 Minecraft 1.21.1、NeoForge 21.1.244、Java 21。
- Fabric Alpha 目标为 Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.157.0+26.2、Java 25。
- NeoForge 使用正式模组列表封面，作者显示为 `岚苍穹 nebysse`。
- 每根导出魔杖独立保存 `wandId`、维度、POS1、POS2、Overlay 开关与导出名；移动物品不会丢失数据，两根魔杖互不串线。
- 支持原版及模组 `BakedModel`、流体、方块实体渲染器和普通实体渲染器。
- 动态方块实体按完整 `MaterialKey` 全局合批；相同材质的连续传送带等几何在 Blender 中形成一个网格对象。
- 根据 Quad 的实际 Atlas UV 反查真实 Sprite，兼容 Create 6.x `*_connected` 纹理表，不引入 Create、Catnip 或 Flywheel 硬依赖。
- 原版草方块保留底层侧面与 Tint Overlay 双层几何；整个选区的 Overlay 合并为一个 `selection/grass_side_overlay` 对象。
- 每次导出同时生成 glTF 2.0 与 USDA，两种格式共享外部 PNG 纹理。
- glTF 按规范三角化；USDA 保留捕获到的原始 Quad，并固定 `subdivisionScheme = "none"`。
- 精确重叠的普通方块 Quad 按自身法线以 `1/1024` 格逐层偏移；首层位置保持不变，所有叠层几何均保留。
- USDA 使用 PreviewSurface 表达纹理、顶点 Tint、透明、双面和发光语义；最近邻语义写入 `minetomesh:samplerMode` 与材质 Sidecar。若 Blender 导入器未读取它，需把 Image Texture 插值手动设为 `Closest`。
- 输出层级固定为 `Chunks`、`BlockEntities`、`Entities`、`Placeholders`、`Overlays`。
- 坐标以选区最小点为局部原点，一格对应 Blender 一米；导出空间保留 Minecraft 的 `(X,Y,Z)` 相对方向，不执行轴反射。
- Blender 导入 glTF 后执行 Y-up 到 Z-up 的轴旋转：Minecraft `+X` → Blender `+X`、Minecraft `+Y` → Blender `+Z`、Minecraft `+Z` → Blender `-Y`。
- 未加载区块不会被强制加载，诊断写入 `report.json`。
- 1.3.0 起超视距选区由服务端按最大 `4×4` 紧凑宏窗口滚动强加载，不再产生缺失区块的成功结果。
- 导出期间全服 `randomTickSpeed = 0`，结束、取消、失败、断线、超时与服务端重启后均恢复会话前数值。
- 全服同时只允许一个导出会话；批次大小 `1～16` 随魔杖保存，数据处理线程数随本机保存。
- 导出中途可点击“停止导出”，GUI 保持打开，清理完成后可再次导出。
- 导出期间玩家视角所在世界可能短暂卸载或闪烁，这是追踪中心切换的预期表现。

## 安装

### NeoForge 1.21.1 正式版

1. 安装 Minecraft 1.21.1、NeoForge 21.1.244 与 Java 21。
2. 将 `MineToMesh-1.3.0-neoforge-1.21.1.jar` 放入**客户端和服务端**的 `mods/` 目录，移除其他 MineToMesh JAR。
3. 启动游戏。实际导出文件写在发起操作的玩家客户端。

### Fabric 26.2 Alpha

1. 安装 Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.157.0+26.2 与 Java 25。
2. 将 `MineToMesh-1.3.0-fabric-alpha.1+mc26.2.jar` 放入**客户端和服务端**的 `mods/` 目录。
3. Alpha 版保留魔杖、GUI、选区、权限、Overlay、方块、流体、方块实体、实体以及 glTF/USDA 双格式导出。第三方渲染后端尚未声明兼容；无法取得 CPU 几何时会生成占位体并写入诊断。

运行时 Mod ID 为 `minetomesh`，Java 根包为 `com.nebysse.minetomesh`。客户端与服务端必须使用同一平台和版本。

## 导出魔杖

可从 MineToMesh 创造模式页签获取，配方为：

```text
  A
 RC
S  
```

- `A`：紫水晶碎片
- `R`：红石
- `C`：铜锭
- `S`：木棍

### 操作表

| 输入 | 目标 | 行为 |
|---|---|---|
| 普通左键 | 方块 | 设置 POS1，阻止方块破坏与裂纹 |
| 普通右键 | 方块 | 设置 POS2，阻止容器或方块交互 |
| 普通左键 | 空气 | 设置 POS1 为眼睛沿准星方向前方两格所在的空气方块 |
| 普通右键 | 空气 | 设置 POS2 为眼睛沿准星方向前方两格所在的空气方块 |
| Shift+左键 | 方块 | 设置 POS1 |
| Shift+左键空气 | 空气 | 清除该魔杖的 POS1/POS2，保留名称和 Overlay 偏好 |
| Shift+右键 | 方块或空气 | 打开导出 GUI |

取点、清除和打开菜单仅播放声音反馈。跨维度设置端点会被拒绝，并保留原选区。

### GUI 与 Overlay

- GUI 可直接编辑 POS1/POS2 的 X/Y/Z；回车或失焦提交完整端点。
- 步进按钮和滚轮为 `±1`，`Shift+滚轮` 为 `±10`。
- 导出名支持 Unicode；中英文通过 `charTyped` 进入聚焦输入框，在回车或失焦时写回当前魔杖，关闭重开仍保留。
- GUI 打开时消费 E、WASD、数字热栏键及模组快捷键；Esc 关闭，Enter 仅提交当前字段，Tab 仅切换焦点，关闭后游戏快捷键立即恢复。
- GUI 提供三个互不耦合的开关：`手持预览`、`锁定选区`、`导出玩家`。手持预览控制当前魔杖 Overlay；锁定选区控制客户端持久框；导出玩家只影响导出内容。
- Overlay 为橙色半透明体积与蓝色深度遮挡边线。手持预览需要对应魔杖仍在主手或副手；锁定选区无需继续持有魔杖。
- 点击锁定选区会直接读取 GUI 中六个坐标。首次点击保存；选择不同区域后点击会替换旧锁定；当前坐标与锁定相同时再次点击会解除锁定。
- 锁定记录写入 `.minecraft/config/minetomesh/locked-selections.json`，支持跨重启恢复，并按存档或服务器隔离。记录只含 SHA-256 Profile 键，不保存原始服务器地址；锁定状态不写入魔杖组件，也不发送新的服务端网络载荷。
- 锁定区域在维度不匹配时隐藏，返回原维度后恢复；切换世界或服务器只切换 Profile，不删除其他 Profile 的记录。损坏的配置会隔离为 `locked-selections.json.corrupt-*`，客户端继续使用空记录启动。
- GUI 打开期间移动、替换或移除绑定魔杖会使菜单失效；正在进行的 GUI 导出随之取消。

### 权限与导出生命周期

- 单人模式允许本地玩家导出。
- 专用服务器要求权限等级 2 或更高，普通玩家仍可取点和编辑魔杖，但不能启动客户端导出。
- 点击导出后，服务端从当前菜单绑定的物品重新读取 UUID、选区、维度和导出名，并返回不可变快照。
- 客户端只接受与当前 `wandId` 和维度匹配的授权；退出世界只清理 Controller 状态，网络回调在进程生命周期内保持安装。
- 导出期间关闭 GUI 会取消任务并清理事务目录；正常完成的结果不会因关闭 GUI 被删除。

## 0.5.1 硬迁移警告

0.5.1 将旧 Mod ID `mcgltf` 完整迁移为 `minetomesh`，不提供 Missing Mapping、旧资源兼容壳或 `/mcgltf` 命令别名，因此**不兼容旧存档**中的旧注册项和旧魔杖数据。升级前请备份世界；旧魔杖不会转换为新身份下的魔杖。

旧 `.minecraft/mcgltf-exports/` 目录不会被移动或删除，新导出统一写入 `.minecraft/minetomesh-exports/`。需要旧结果时可直接从旧目录读取。0.5.0 对区域导出工作台的硬删除仍然有效，旧工作台也不会恢复或转换。

## 指令备用入口

```text
/minetomesh pos1
/minetomesh pos2
/minetomesh export <名称>
/minetomesh export <名称> confirm
/minetomesh status
/minetomesh cancel
```

`/minetomesh` 是无需魔杖的备用入口，不保留旧命令别名。超过软限制 4,194,304 格时，需要执行聊天中给出的 `confirm` 指令。

## 输出

输出根目录为 `.minecraft/minetomesh-exports/`：

```text
minetomesh-exports/<名称>/
├─ <名称>.gltf
├─ <名称>.bin
├─ <名称>.usda
├─ report.json
├─ textures/
└─ materials/
```

同名目录自动使用 `名称-2`、`名称-3` 等后缀。捕获采用滚动快照，每客户端 Tick 预算约 6 ms，后台写入队列容量为 2。

## Blender 导入

在 Blender 5.2 中选择“文件 → 导入 → glTF 2.0”打开 `.gltf`；需要保留 Quad 时，选择“文件 → 导入 → Universal Scene Description”打开 `.usda`。保持 `.gltf`、`.bin`、`.usda` 与 `textures/` 的相对目录不变。USDA 舞台为 Y-Up、`metersPerUnit = 1`，Blender 导入时完成 Y-up 到 Z-up 的轴旋转。PreviewSurface 对 Cutout、Blend、顶点 Tint 和发光的呈现受 Blender USD 导入器能力影响，复杂材质仍建议导入后人工检查。

1.2.0 不再生成 OBJ/MTL，也不提供兼容开关。成功或取消导出后不应残留 `.usdapart` 临时片段。

Flywheel 1.x 等受支持后端会在捕获期间临时进入 CPU 备用渲染作用域，并在 `finally` 路径恢复。纯着色器生成且没有 CPU 几何的对象无法逆向恢复，会生成半透明洋红包围盒并记录诊断码。阴影、火焰、名称文本、Minecraft 光照和 AO 不会烘焙。

## 构建与验证

```powershell
# 完整双平台构建
./gradlew.bat clean build --no-configuration-cache

# 分平台构建
./gradlew.bat :neoforge-1.21.1:test :neoforge-1.21.1:build --no-configuration-cache
./gradlew.bat :fabric-26.2:test :fabric-26.2:build --no-configuration-cache

# 服务端冒烟
./gradlew.bat :neoforge-1.21.1:runServerSmoke --no-configuration-cache
./gradlew.bat :fabric-26.2:fabricServerSmoke --no-configuration-cache

Set-Location tools
npm install
npm run validate -- ..\run\minetomesh-exports\smoke\smoke.gltf
```

最终 JAR：

- `neoforge-1.21.1/build/libs/MineToMesh-1.3.0-neoforge-1.21.1.jar`
- `fabric-26.2/build/libs/MineToMesh-1.3.0-fabric-alpha.1+mc26.2.jar`

真实模组验收建议使用 Create 6.0.10、Flywheel 1.0.6 与 Touhou Little Maid 1.5.3，分别把 glTF 与 USDA 导入 Blender 5.2，对比原点、尺度、材质、UV、Quad 拓扑和 Powered Rail 叠层，并用 Khronos Validator 要求 glTF `numErrors: 0`。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
