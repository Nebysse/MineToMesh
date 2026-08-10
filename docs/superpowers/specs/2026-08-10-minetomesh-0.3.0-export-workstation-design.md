# MineToMesh 0.3.0 区域导出工作台设计

日期：2026-08-10  
状态：设计已逐节确认，待书面规格复核  
目标版本：0.3.0  
Minecraft：1.21.1  
NeoForge：最低 21.1.244

## 1. 目标

MineToMesh 0.3.0 新增一个可放置、可合成、带持久化选区的“区域导出工作台”。玩家右键方块后打开完整 GUI，通过两个三维整数坐标编辑选区，观察选区体积，并从客户端启动现有 glTF 与 OBJ 导出管线。

本版本同时把模组从纯客户端模组调整为客户端与服务端均需安装的双端模组。服务端负责方块、选区、菜单、权限校验和多人同步；渲染捕获、纹理读取与文件写入仍只在发起操作的玩家客户端执行。

## 2. 已确认的产品决策

- 选区数据属于操作方块，不属于玩家。
- 每个工作台保存一组起点和终点，所有玩家共享。
- 坐标支持手动输入、上下按钮、鼠标滚轮和脚下取点。
- 上下按钮固定调整 `±1`。
- 悬停输入框时滚轮调整 `±1`，`Shift + 滚轮` 调整 `±10`。
- 选区显示可在 GUI 中切换，关闭 GUI 后仍可持续显示。
- 选区使用橙色半透明体积表面和蓝色纵深辅助边线。
- 选区遵守正常深度遮挡，不穿墙显示。
- GUI 关闭时立即取消仍在进行的导出并清理事务目录。
- 游戏内报告只显示摘要，完整诊断继续写入 `report.json`。
- 导出名默认自动生成，玩家可以在 GUI 中修改，但不写入方块实体。
- 工作台可在创造模式获得，也提供生存配方。
- 不设置选区体积上限。
- 保留现有 `/mcgltf` 客户端命令。
- 视觉使用 Blender 致敬配色：橙色、蓝色、深灰和中灰。
- GUI 采用双栏仪表台与轻倒角，信息层级参考 Create，但不依赖或复制 Create 的代码和素材。
- 方块为有明确水平朝向的工业设备，正面图标是蓝色正交线框立方体与从内部向右上射出的橙色小箭头。
- 方块最终纹理使用原生 `16×16` 分辨率。

## 3. 范围外内容

0.3.0 不实现以下功能：

- 服务端渲染捕获或服务端文件导出。
- 世界点选工具、魔杖或 WorldEdit 式左右键选点。
- 完整游戏内诊断浏览器、日志筛选、复制日志或打开系统目录。
- HUD 常驻进度条。
- 导出暂停、恢复或断线续传。
- 工作台物品栏、红石控制、动态发光或动态方块模型。
- 选区体积软警告、二次确认或硬上限。
- Create 模组运行时依赖。
- 对 0.2.0 捕获后端、双格式拓扑和纹理兼容策略的重新设计。

## 4. 总体架构

采用 NeoForge `AbstractContainerMenu`、`BlockEntity` 与显式 `CustomPacketPayload` 组合。

```text
ExportWorkstationBlock
  └─ ExportWorkstationBlockEntity
       ├─ first: BlockPos
       └─ second: BlockPos

Server ExportWorkstationMenu
  ├─ 校验方块身份与玩家距离
  ├─ 同步六个坐标整数
  ├─ 接收合法编辑
  └─ 生成不可变导出快照

Client ExportWorkstationScreen
  ├─ 坐标编辑与本地输入校验
  ├─ 导出名编辑
  ├─ ExportTelemetry 展示
  └─ 摘要报告展示

Client WorkstationExportController
  ├─ 接收服务端授权快照
  ├─ 创建 DefaultExportPipeline
  ├─ 桥接 ExportJobManager
  └─ 关闭 GUI 时取消任务

Client SelectionOverlayRenderer
  └─ 绘制当前玩家已启用的工作台选区
```

### 4.1 包边界

新增以下包：

```text
com.onecuber.mcgltf.content
com.onecuber.mcgltf.workstation
com.onecuber.mcgltf.network
com.onecuber.mcgltf.client.workstation
```

`content` 只负责注册表入口；`workstation` 包含双端安全的方块、方块实体、菜单和值对象；`network` 包含 Payload 与服务端处理；`client.workstation` 包含 Screen、控件、选区渲染和客户端导出控制器。

服务端可达类不得静态引用 `Minecraft`、`GuiGraphics`、渲染器、客户端纹理或现有客户端捕获类。

## 5. 工作台方块与持久化

### 5.1 方块

`ExportWorkstationBlock` 使用水平朝向属性。放置时正面背向放置玩家的视线方向，使带图标的操作面朝向玩家。右键空手或持有普通物品均打开菜单，不消费手中物品。

属性：

- 金属音效。
- 硬度 `3.0`。
- 爆炸抗性 `6.0`。
- 需要正确镐类工具。
- 无物品栏。
- 无红石输入与输出。
- 无动态 `LIT` 状态。

玩家距离工作台超过八格、方块被拆除或原位置不再是同类型工作台时，`stillValid` 返回 false 并关闭菜单。

### 5.2 方块实体

`ExportWorkstationBlockEntity` 保存两个 `BlockPos`。首次放置时，两点均初始化为方块自身坐标。维度不重复写入 NBT，始终使用方块实体所在世界的维度。

NBT 字段固定为：

```text
First: int[3]
Second: int[3]
```

读取损坏或缺失字段时回退为工作台自身坐标。保存和读取均复制值，不暴露可变数组。

坐标变化后：

1. 更新方块实体。
2. 调用 `setChanged()`。
3. 通知当前打开的菜单同步。
4. 发送仅包含六个坐标整数的方块实体更新包给区块追踪客户端。

多人同时编辑时，服务端按合法 Payload 到达顺序提交，最后一次提交生效。

## 6. 注册与内容资源

统一注册：

- `export_workstation` Block。
- `export_workstation` BlockItem。
- `export_workstation` BlockEntityType。
- `export_workstation` MenuType。
- MineToMesh CreativeModeTab。

创造页签使用工作台方块作为图标。

生存配方：

```text
I G I
R C R
I I I
```

- `I`：Iron Ingot
- `G`：Glass Pane
- `R`：Redstone
- `C`：Cartography Table

产出一个 `export_workstation`。

资源包括方块状态、水平朝向模型、物品模型、战利品表、镐标签、配方及 `zh_cn`、`en_us` 本地化。

## 7. Menu 与网络协议

### 7.1 Menu

`ExportWorkstationMenu` 不包含物品槽。客户端构造器通过 `IContainerFactory` 接收工作台 `BlockPos`；服务端构造器持有 `ContainerLevelAccess` 与方块实体数据引用。

六个坐标整数通过 `ContainerData` 同步。NeoForge 1.21.1 提供完整整数同步，不按原版短整数截断。

### 7.2 Payload

协议使用固定版本字符串并注册在 `RegisterPayloadHandlersEvent`。

建议 Payload：

- `UpdateCoordinatePayload(stationPos, endpoint, axis, value)`
- `CaptureFeetPayload(stationPos, endpoint)`
- `ExportRequestPayload(stationPos, exportName)`
- `ExportGrantedPayload(stationPos, exportName, first, second, dimension)`
- `ExportRejectedPayload(stationPos, reasonKey)`

所有 C2S 处理在服务端主线程执行，并校验：

- 玩家当前打开的是 `ExportWorkstationMenu`。
- 菜单绑定位置等于 Payload 位置。
- 原位置仍为工作台。
- 玩家仍在八格交互距离内。
- 坐标是合法 `BlockPos`，Y 位于当前维度构建高度内。
- 导出名通过现有 `ExportName` 安全规则且不超过 64 个字符。

非法请求只返回本地化拒绝原因，不断开玩家连接。

“记录脚下方块”由服务端根据 `ServerPlayer` 当前坐标计算，不信任客户端提交的玩家位置。

### 7.3 不可变导出快照

服务端通过验证后规范化两点，生成包含维度、起点、终点和导出名的不可变快照，再发送给发起玩家。客户端只能在维度仍匹配且绑定 Screen 仍打开时启动导出。

导出启动后，其他玩家对工作台坐标的修改不会改变已经运行的任务。

## 8. GUI 规格

### 8.1 视觉

Screen 逻辑尺寸固定为 `384×216` GUI 像素：

- 顶部标题栏：`x=0, y=0, width=384, height=20`。
- 左栏：`x=4, y=24, width=208, height=166`。
- 右栏：`x=216, y=24, width=164, height=166`。
- 底部日志栏：`x=4, y=194, width=376, height=18`。
- 四周外边距和栏间距均为 `4` GUI 像素。

配色职责：

- 橙色：主操作、起点、活动选择。
- 蓝色：终点、进度、辅助状态。
- 深灰和中灰：窗口、面板、输入框与结构背景。

倒角范围：

- 外框倒角半径：`5` GUI 像素。
- 主面板与主按钮倒角半径：`4` GUI 像素。
- 输入框、小按钮与日志栏倒角半径：`2` GUI 像素。

Minecraft 实现使用整数坐标与 nearest filtering，不直接照搬网页 CSS 半像素或浏览器抗锯齿。

### 8.2 坐标编辑

每个端点包含 X、Y、Z 三个有符号整数输入框。

- `Enter` 或失去焦点时提交。
- 上下按钮固定 `±1`。
- 悬停滚轮 `±1`。
- `Shift + 滚轮` 为 `±10`。
- 输入框获得键盘焦点时，滚轮仍可调整，但必须先提交当前可解析文本；不可解析文本保持原样并标红。
- 合法提交由服务端回传后的值覆盖本地镜像。
- 非法值不发送 Payload。

范围按包含首尾方块计算：

```text
sizeX = abs(x2 - x1) + 1
sizeY = abs(y2 - y1) + 1
sizeZ = abs(z2 - z1) + 1
volume = sizeX * sizeY * sizeZ
```

计算使用溢出安全的 long 运算。0.3.0 不设置体积上限。

### 8.3 导出名

打开 GUI 时按工作台坐标与当前时间生成默认名称。玩家可以修改；名称不持久化到方块实体。

非法名称输入框标红，导出按钮禁用。名称最长 64 个字符。

### 8.4 状态机

Screen 状态：

1. `READY`：允许编辑与导出。
2. `INVALID_INPUT`：标记非法输入并禁用导出。
3. `WAITING_FOR_GRANT`：请求已发送，短暂锁定操作。
4. `EXPORTING`：锁定坐标和名称，显示进度与取消。
5. `COMPLETED`：显示摘要，可关闭或开始下一次导出。
6. `FAILED`：显示简短原因。
7. `CANCELLED`：显示取消结果，随后允许重新开始。

导出期间关闭 Screen、按 Esc、方块失效或距离过远均取消客户端任务。正常完成后关闭 Screen 不删除结果。

## 9. 选区渲染

选区显示状态属于每名玩家的客户端会话，以维度和工作台位置为键：

```text
OverlayKey(dimension, stationPos)
```

行为：

- GUI 中打开后，关闭 GUI 仍持续显示。
- 再次打开同一工作台可关闭。
- 方块实体同步变化时刷新。
- 方块被拆除、退出世界或切换维度时清除对应显示。
- 不写入玩家持久配置，也不广播显示开关。

渲染只绘制六个边界表面与十二条边：

- 橙色低 Alpha 表面。
- 蓝色纵深辅助边线。
- 开启深度测试和正常深度写入策略，被实体方块遮挡。
- 不逐方块遍历选区内部。
- 使用相机相对坐标和当前帧矩阵，避免远坐标精度漂移。

## 10. 导出控制与进度

### 10.1 复用现有管线

工作台不创建第二套导出器。`WorkstationExportController` 将服务端授权快照交给现有：

```text
DefaultExportPipeline.create(...)
ExportJobManager.start(...)
```

现有客户端命令继续使用相同管线。

### 10.2 ExportTelemetry

新增线程安全、只读快照式 `ExportTelemetry`，用于 GUI 展示可信的阶段进度。

整体进度权重：

- 捕获实体与区段：`0–80%`
- 清空写入队列：`80–88%`
- 写入纹理与材质：`88–93%`
- 完成 glTF 与 OBJ：`93–97%`
- 写入报告并提交事务：`97–100%`

权重表达阶段进度，不宣称字节级精确。进度必须单调不减，并同时显示阶段名、当前对象、队列深度和耗时。

### 10.3 摘要

完成后产生客户端内存中的 `ExportSummary`：

- 最终状态。
- 输出目录。
- 已捕获对象数量。
- Primitive 数量。
- 写入纹理数量。
- 警告数量。
- 总耗时。
- 简短失败原因。

游戏内不展示完整诊断列表。完整细节仍位于 `report.json`。报告 schema 只有在实际新增序列化字段时才升级，不为 Screen 内存摘要强制升级。

### 10.4 取消

取消流程：

1. `ExportJobManager.cancel(reason)`。
2. 停止后续捕获。
3. 写入线程接收取消信号。
4. `OutputTransaction` 删除 staging 目录。
5. 已经成功提交的旧输出目录不受影响。

0.3.0 不支持恢复被取消的任务。

## 11. 视觉资产与来源

已确认的设计参考保存在：

```text
docs/superpowers/design-assets/minetomesh-0.3.0/
  export-workstation-dashboard.html
  gui-concept.png
  gui-greenkey-atlas.png
  workstation-block-greenkey.png
```

这些文件属于设计输入，不直接等同于生产资源。

### 11.1 GUI 图集处理

1. 检查绿幕背景是否严格为 `#00FF00`。
2. 使用精确颜色键转透明，不采用模糊容差吞噬边缘。
3. 按矩形切分可复用控件。
4. 清理近绿色污染、跨素材阴影和非整数像素边缘。
5. 整理为生产 sprite atlas 与坐标清单。
6. 文字、数字和动态进度永远由游戏代码绘制。

若生图素材无法达到像素边缘要求，则只把它作为视觉参考并手工重绘；不得为了“保留生图”牺牲可切片性。

### 11.2 方块纹理处理

最终生产纹理固定为：

```text
export_workstation_front.png   16×16
export_workstation_side.png    16×16
export_workstation_back.png    16×16
export_workstation_top.png     16×16
export_workstation_bottom.png  16×16
```

设计规则：

- 正面：蓝色正交线框立方体和向右上射出的橙色箭头。
- 侧面：深灰分段机壳、短蓝色状态槽和少量橙色方向标记。
- 背面：接口、散热格栅和蓝色状态灯。
- 顶面：无字母的橙蓝正交网格。
- 底面：耐磨底板、四角固定脚和检修盖。
- 左上受光，右下压暗。
- 倒角控制为 1–2 个逻辑像素。

生图输出必须重新映射到真实 `16×16` 逻辑网格并人工检查，不能仅按图片尺寸宣称原生像素精度。

## 12. 错误处理

- 找不到工作台：关闭菜单并显示本地化错误。
- 玩家距离无效：拒绝 Payload，菜单按 `stillValid` 关闭。
- 坐标越界：输入框标红，不提交。
- 导出名非法：禁用导出。
- 客户端维度与快照不一致：拒绝启动。
- 已有任务运行：复用现有“already running”策略并显示摘要错误。
- 写入失败：保持事务原子性，显示简短原因，完整堆栈进入日志和报告。
- 方块实体 NBT 损坏：回退到自身位置，不使世界加载失败。

## 13. 测试策略

严格执行 TDD。

### 13.1 单元测试

- 版本常量和 Gradle 元数据升级为 0.3.0。
- 方块实体 NBT 往返、缺失字段和损坏字段回退。
- 坐标规范化、尺寸、体积和溢出处理。
- Menu 数据映射和距离有效性。
- Payload 编解码。
- Payload 菜单身份、位置、距离和坐标校验。
- 脚下坐标由服务端玩家位置产生。
- 多玩家最后合法提交生效。
- 导出快照不可变。
- 输入框提交、按钮步长、滚轮步长与 Shift 步长。
- 非法输入不发送网络请求。
- Telemetry 进度单调不减。
- Screen 关闭触发取消。
- 摘要状态映射。
- OverlayKey 生命周期和清除策略。

### 13.2 集成与游戏测试

- Block、Item、BlockEntityType、MenuType 和 CreativeModeTab 注册。
- 放置朝向与五面纹理映射。
- 方块保存、重载和掉落。
- 菜单打开与失效。
- Dedicated Server 启动不加载客户端类。
- 原有命令和工作台共用导出管线。
- 原有 0.2.0 测试全部继续通过。

### 13.3 手工验收

- GUI Scale 2、3、4 下无重叠、裁切或不可点击控件。
- 四个水平朝向外观正确。
- 两名玩家同时打开并编辑同一工作台。
- 按钮、滚轮、Shift 滚轮和脚下取点正确。
- 选区半透明体积被世界几何正常遮挡。
- 关闭 GUI 取消导出并清理 staging。
- Create、Touhou Little Maid 和普通方块导出回归。
- Blender 中纯白顶点色仍不生成多余 Alpha 乘法节点。

## 14. 文档与发布

- `gradle.properties` 与 `McGltf.VERSION` 更新为 `0.3.0`。
- `McGltf.DISPLAY_NAME` 更新为 `MineToMesh`。
- README 更新双端安装要求、工作台操作、命令备用入口、配方与导出取消规则。
- 手工验收矩阵增加工作台、多玩家、GUI Scale、Overlay 和 Dedicated Server 项目。
- 构建产物为 `mcgltf-0.3.0.jar`。
- 最终发布前验证生产 JAR 不包含 testmod 和设计原图。

## 15. 参考依据

- NeoForge 1.21.1 Menus：`https://docs.neoforged.net/docs/1.21.1/gui/menus/`
- NeoForge 1.21.1 Payloads：`https://docs.neoforged.net/docs/1.21.1/networking/payload/`
- Create 1.21.1 `AllGuiTextures` 仅用于理解紧凑机械 GUI 的分段方式；生产资源全部原创，且 MineToMesh 不添加 Create 依赖。

## 16. 完成标准

0.3.0 只有在以下条件全部满足时才视为完成：

- 双端注册和 Dedicated Server 安全通过。
- 工作台可合成、放置、保存、打开和多人同步。
- GUI 所有已确认交互可用。
- 选区显示生命周期和深度遮挡正确。
- 工作台导出复用现有管线并保持事务原子性。
- 关闭 GUI 可以可靠取消。
- 摘要和进度不虚构数据。
- 生产 GUI 与方块纹理通过像素级人工检查。
- 全量自动化测试通过。
- 用户完成真实模组与 Blender 场景验收。
