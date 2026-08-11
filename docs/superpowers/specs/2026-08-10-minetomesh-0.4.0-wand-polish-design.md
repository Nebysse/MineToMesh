# MineToMesh 0.4.0 魔杖交互与视觉完善设计

日期：2026-08-10  
状态：待用户书面复核  
基线分支：`feature/mcgltf-0.4.0-export-wand`

## 1. 目标

在已完成的 0.4.0 导出魔杖架构上增加四项闭环改进：

1. GUI 打开期间严格隔离键盘事件，仅允许 Esc 退出，同时保证文本编辑正常。
2. 增加“导出玩家”勾选项，由每根魔杖独立持久化，默认关闭。
3. 将选区 Overlay 改为橙色世界边界纹理外壳，并显示 POS1/POS2 的单格半透明端点框。
4. 将导出魔杖物品贴图替换为已确认的 32×32 透明像素纹理。

本轮保持版本号 `0.4.0`，完成后重新构建并替换候选 JAR。

## 2. 已确认行为

### 2.1 GUI 键盘隔离

- GUI 打开时 `passEvents=false`。
- 只有 Esc 能关闭 GUI。
- `E`、数字热栏键、移动键及其他游戏或模组快捷键均不得关闭 GUI 或传入游戏。
- 文本框聚焦时，字符、数字、负号及 `Ctrl+A/C/V/X` 只作用于文本框。
- Enter 只提交当前聚焦的坐标端点或导出名，不直接启动导出。
- Tab 只在 GUI 控件间移动焦点。
- 未被控件处理的 `keyPressed`、`keyReleased` 与 `charTyped` 仍由 Screen 消费。
- 鼠标按钮、滚轮和 GUI 按钮维持现有行为。

实现不得通过清空全局 KeyMapping 状态来“修复”泄漏，避免破坏关闭 GUI 后的正常输入。

### 2.2 导出玩家选项

- `ExportWandSelection` 增加 `includePlayers` 布尔字段。
- 默认值为 `false`；旧 ItemStack 缺少字段时必须安全解码为 `false`。
- 每根魔杖独立保存该值，移动物品后仍保留，两根魔杖互不影响。
- GUI 左栏底部原 Overlay 按钮拆成两个并列的 92×16 切片风格勾选按钮：
  - 左侧：`选区显示`
  - 右侧：`导出玩家`
- 服务端通过菜单作用域 Payload 修改绑定 ItemStack，槽位或 `wandId` 不匹配时拒绝写入。
- 点击导出时，服务端从绑定魔杖读取该值并写入不可变 Grant 快照。
- 导出开始后再修改开关，不影响已经获批的任务。
- 开启时导出选区内所有可见玩家，包含发起导出的本地玩家。
- 关闭时继续排除所有玩家，保持旧行为。
- 被移除的玩家不导出；玩家仍须与选区 AABB 相交。
- 隐身或渲染器未产生可导出顶点的玩家沿用现有实体捕获降级与诊断策略，不人为生成可见皮肤。
- `/mcgltf` 指令备用入口继续使用 `includePlayers=false`，不改变既有命令行为。

## 3. 数据与授权链路

### 3.1 Data Component

`ExportWandSelection` 字段顺序扩展为：

```text
wandId
selectionDimension
pos1
pos2
overlayEnabled
includePlayers
exportName
```

持久 Codec 使用 `optionalFieldOf("include_players", false)`；StreamCodec 在客户端和服务端同步读写该字段。

新增不可变修改方法：

```text
withIncludePlayers(boolean)
```

清除端点时保留 `overlayEnabled`、`includePlayers` 和 `exportName`。

### 3.2 网络

新增：

```text
ToggleWandIncludePlayersPayload(boolean enabled)
```

服务端处理要求：

```text
ServerPlayer
当前 containerMenu 为 ExportWandMenu
menu.resolveBoundStack(player) 成功
```

`ExportWandGrantedPayload` 增加 `boolean includePlayers`。Request 仍只携带导出名，不能由客户端请求体伪造该选项。

### 3.3 Controller 与管线

新增值对象：

```text
ExportOptions(boolean includePlayers)
```

数据流：

```text
Grant
→ ExportWandController.JobStarter
→ DefaultExportPipeline.create
→ ProductionCaptureSource
→ EntityCapture.collect/captureAll
```

`EntityCapture.shouldInclude` 增加 `includePlayers` 参数。非玩家实体逻辑不变；玩家仅在开关开启时通过过滤。

导出根 `extras` 写入 `includePlayers`，便于结果追溯。

## 4. Overlay 设计

### 4.1 数据源

`HeldWandOverlaySource` 不再只返回完整 `Selection`，改为返回包含以下数据的快照：

```text
Optional<BlockPos> pos1
Optional<BlockPos> pos2
Optional<Selection> selection
ResourceLocation dimension
```

选择规则仍为主手优先、副手回退。魔杖需满足 Overlay 开启且维度匹配。即使只有一个端点，也要显示该端点框；主选区外壳只在两个端点完整时显示。

### 4.2 主选区

- 使用原版世界边界纹理 `minecraft:textures/misc/forcefield.png`。
- 建立适配 `POSITION_COLOR_TEX` 的半透明、深度测试 RenderType。
- 六个面均铺设纹理，UV 按世界尺寸重复，不把整张纹理拉伸到整面。
- UV 随时间缓慢滚动，保持世界边界的能量墙语义。
- 顶点颜色统一染为 MineToMesh 橙色 `#ED741C`。
- 删除现有蓝色总边线。
- 外壳仍遵守实体方块深度遮挡。

### 4.3 端点框

- POS1：一格 AABB，灰色半透明面与灰白轮廓。
- POS2：一格 AABB，白色半透明面与白色轮廓。
- AABB 范围为端点方块坐标到坐标 `+1`。
- 端点框在主外壳之后绘制，并使用极小外扩避免与外壳共面闪烁。
- 两个端点重合时按 POS1 后、POS2 后的顺序绘制，使白色 POS2 成为最终可见层。

## 5. 32×32 魔杖纹理

已确认视觉方向：橙铜色粗测量杆、紫水晶传感器、冰蓝测距模块。

处理纪律：

1. 使用纯绿幕 `#00FF00` 母图。
2. 先完成 Chroma Key 与绿色溢色清理。
3. 再裁切主体并留一像素安全边距。
4. 使用 Pillow `Image.Resampling.NEAREST` 缩至 32×32；禁止 Bilinear、Bicubic、Lanczos 或 GPU 线性采样。
5. 最终 PNG 使用透明背景、整像素 Alpha，无半透明抗锯齿边缘。

候选自动检查结果：32×32、760 个透明像素、264 个不透明像素、0 个半透明像素、0 个残留绿色像素。

生产资源覆盖：

```text
src/main/resources/assets/mcgltf/textures/item/export_wand.png
```

物品模型路径不变。

## 6. GUI 布局

左栏底部两个按钮共享原 Overlay 行：

```text
x = LEFT.x + 12,  width = 92   → 选区显示
x = LEFT.x + 108, width = 92   → 导出玩家
height = 16
```

两按钮不与 POS2 的第三个坐标框或步进按钮相交。沿用现有 GUI 切片、OFF/ON 指示器和 8 物理像素九宫格策略。

## 7. 错误处理

- 无绑定魔杖、槽位变化或 UUID 不匹配：忽略切换 Payload，不修改其他 ItemStack。
- 旧组件缺少新字段：解码为 `includePlayers=false`。
- Grant UUID 或维度不匹配：Controller 拒绝。
- 世界边界纹理资源缺失：记录一次警告并跳过主外壳，端点框仍可绘制；不得导致客户端崩溃。
- GUI 关闭后输入恢复正常，不保留粘滞按键状态。

## 8. 测试与验收

### 自动测试

- `ExportWandSelection` Codec 与 StreamCodec 新旧默认值、复制及清除保留。
- `ToggleWandIncludePlayersPayload` 往返。
- 菜单绑定成功与错槽/错 UUID 拒绝修改。
- Grant Codec 携带不可变 `includePlayers`。
- Controller 将 `ExportOptions` 传给 JobStarter。
- `EntityCapture.shouldInclude` 覆盖开关关闭、开启、玩家、自身、移除实体和边界相交。
- Overlay Source 覆盖单端点、双端点、主副手优先、维度和开关。
- Renderer 契约覆盖 forcefield 纹理、橙色、六面纹理、灰白端点及无蓝色总边线。
- GUI 布局覆盖两个 92×16 按钮不重叠。
- GUI 键盘测试覆盖 Esc、E、字母、数字、Enter、Tab、剪贴板组合键和字符事件。
- 纹理资源测试覆盖 32×32、Alpha、无绿幕残留。

### 最终验证

```text
clean test build
runServerSmoke
JAR 内容与元数据审计
真实客户端键盘输入测试
玩家开关关闭/开启对照导出
单端点与完整选区 Overlay 人工视觉验收
Blender 导入含玩家的 glTF/OBJ
```

## 9. 不在本轮范围

- 玩家类型白名单、按 UUID 选择玩家。
- 导出隐身玩家的强制显形。
- `/mcgltf` 指令新增玩家开关参数。
- Overlay 颜色自定义。
- 版本号提升到 0.4.1。
