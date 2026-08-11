# MineToMesh

MineToMesh 是面向 Minecraft 1.21.1 NeoForge 的世界导出模组，将客户端当前已加载的选区写成 Blender 可编辑的 glTF 2.0 与 OBJ 场景。0.4.0 使用**导出魔杖**保存和编辑选区：服务端权威校验物品身份、坐标与权限，客户端负责渲染捕获、纹理读取和文件写入。

## 特性

- 客户端和服务端均需安装，目标版本为 NeoForge 21.1.244。
- 每根导出魔杖独立保存 `wandId`、维度、POS1、POS2、Overlay 开关与导出名；移动物品不会丢失数据，两根魔杖互不串线。
- 支持原版及模组 `BakedModel`、流体、方块实体渲染器和普通实体渲染器。
- 每次导出同时生成 glTF 2.0 与 OBJ，两种格式共享纹理。
- glTF 按规范三角化；OBJ 保留捕获到的原始 Quad。
- 保留 PNG 纹理、顶点色、透明模式、双面和发光语义。
- 输出层级固定为 `Chunks`、`BlockEntities`、`Entities`、`Placeholders`。
- 坐标转换为 `(X,Y,Z) → (X,Y,-Z)`，选区最小点为局部原点，一格对应 Blender 一米。
- 未加载区块不会被强制加载，诊断写入 `report.json`。

## 安装

1. 安装 Minecraft 1.21.1 与 NeoForge 21.1.244。
2. 将 `mcgltf-0.4.0.jar` 放入**客户端和服务端**的 `mods/` 目录，移除其他 MineToMesh JAR。
3. 启动游戏。实际导出文件写在发起操作的玩家客户端。

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
- 导出名在回车或失焦时写回当前魔杖，关闭重开仍保留。
- Overlay 为橙色半透明体积与蓝色深度遮挡边线。
- 只有手持对应魔杖、Overlay 开启、选区完整且维度匹配时才显示；切走物品立即隐藏，重新拿回同一根魔杖后恢复。
- GUI 打开期间移动、替换或移除绑定魔杖会使菜单失效；正在进行的 GUI 导出随之取消。

### 权限与导出生命周期

- 单人模式允许本地玩家导出。
- 专用服务器要求权限等级 2 或更高，普通玩家仍可取点和编辑魔杖，但不能启动客户端导出。
- 点击导出后，服务端从当前菜单绑定的物品重新读取 UUID、选区、维度和导出名，并返回不可变快照。
- 客户端只接受与当前 `wandId` 和维度匹配的授权；退出世界只清理 Controller 状态，网络回调在进程生命周期内保持安装。
- 导出期间关闭 GUI 会取消任务并清理事务目录；正常完成的结果不会因关闭 GUI 被删除。

## 0.4.0 迁移警告

0.4.0 硬删除了区域导出工作台及其方块实体、菜单、配方和网络协议，**不兼容旧存档**中的工作台数据。升级前请备份世界；旧工作台不会转换为导出魔杖。

## 指令备用入口

```text
/mcgltf pos1
/mcgltf pos2
/mcgltf export <名称>
/mcgltf export <名称> confirm
/mcgltf status
/mcgltf cancel
```

`/mcgltf` 仍作为无需魔杖的备用入口。超过软限制 4,194,304 格时，需要执行聊天中给出的 `confirm` 指令。

## 输出

输出根目录为 `.minecraft/mcgltf-exports/`：

```text
mcgltf-exports/<名称>/
├─ <名称>.gltf
├─ <名称>.bin
├─ <名称>.obj
├─ <名称>.mtl
├─ report.json
├─ textures/
└─ materials/
```

同名目录自动使用 `名称-2`、`名称-3` 等后缀。捕获采用滚动快照，每客户端 Tick 预算约 6 ms，后台写入队列容量为 2。

## Blender 导入

在 Blender 中选择“文件 → 导入 → glTF 2.0”打开 `.gltf`，或选择“文件 → 导入 → Wavefront (.obj)”打开 `.obj`。保持 `.gltf`、`.bin`、`.obj`、`.mtl` 与 `textures/` 的相对目录不变。

Flywheel 1.x 等受支持后端会在捕获期间临时进入 CPU 备用渲染作用域，并在 `finally` 路径恢复。纯着色器生成且没有 CPU 几何的对象无法逆向恢复，会生成半透明洋红包围盒并记录诊断码。阴影、火焰、名称文本、Minecraft 光照和 AO 不会烘焙。

## 构建与验证

```powershell
./gradlew.bat clean test build
./gradlew.bat runServerSmoke
Set-Location tools
npm install
npm run validate -- ..\run\mcgltf-exports\smoke\smoke.gltf
```

真实模组验收建议使用 Create 6.0.10、Flywheel 1.0.6 与 Touhou Little Maid 1.5.3，分别把 glTF 与 OBJ 导入 Blender 5.2，对比原点、尺度、材质、UV 和拓扑，并用 Khronos Validator 要求 `numErrors: 0`。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
