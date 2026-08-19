# MineToMesh Fabric 26.2 多加载器适配设计

## 1. 目标

在保留现有 Minecraft 1.21.1 NeoForge 21.1.244 正式版本的前提下，为 MineToMesh 新增 Minecraft 26.2 Fabric 完整功能版本，并将仓库整理成可长期维护的 Gradle 多项目结构。

交付版本：

- NeoForge 1.21.1：`1.2.0`
- Fabric 26.2：`1.2.0-fabric-alpha.1`

Fabric 版本必须保持现有功能语义，包括魔杖、GUI、选区、网络权限、方块与流体捕获、方块实体与普通实体捕获、Overlay、glTF、BIN、USDA、纹理、报告及任务生命周期。对于 Fabric 26.2 生态中不存在或无法实测的第三方模组版本，保留后端兼容接口、占位几何和诊断机制，但不得宣称已经完成真实兼容验证。

## 2. 约束

- 现有 NeoForge 1.21.1 行为与测试能力必须保留。
- NeoForge 模块继续使用 Java 21。
- Fabric 26.2 使用 Java 25、Fabric Loader 0.19.3、Fabric API 0.157.0+26.2。
- Gradle Toolchain 自动获取隔离的 Java 25，不修改系统 `JAVA_HOME`，也不破坏现有 Java 21 环境。
- 不引入 Architectury。两个目标使用不同 Minecraft 大版本，且渲染捕获深度依赖版本 API，通用加载器抽象无法消除主要适配成本。
- 不复制完整导出引擎。与 Minecraft API 无关的算法和业务逻辑只能保留一份。
- 当前工作区已有的 `.gitignore` 修改、`$null`、`模组封面.png` 等非本任务改动不得被覆盖或误提交。

## 3. 仓库结构

```text
MineToMesh/
├─ common/
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/
│     └─ test/java/
├─ neoforge-1.21.1/
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/
│     ├─ main/resources/
│     ├─ main/templates/
│     ├─ test/java/
│     └─ testmod/
├─ fabric-26.2/
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/
│     ├─ main/resources/
│     └─ test/java/
├─ build.gradle
├─ gradle.properties
└─ settings.gradle
```

根项目负责公共仓库、构建聚合、编码设置和测试约定。各平台模块独立声明 Minecraft、加载器、Java Toolchain、运行配置和最终产物名称。

## 4. 模块边界

### 4.1 `common`

`common` 只包含可脱离 Minecraft 运行的代码，并以 Java 21 字节码构建，以便两个平台共同加载。主要内容包括：

- glTF、BIN、USDA 写入和内部校验
- 场景节点、顶点、材质键、纹理键和拓扑转换
- Quad 共面分层、几何调整与统计
- 导出名称、输出事务、报告与诊断
- 任务状态机、取消令牌、进度和遥测
- 选区数学、权限与输入判定等纯业务规则
- 对应的纯 Java 单元测试

公共层不得导入 `net.minecraft.*`、`net.neoforged.*` 或 `net.fabricmc.*`。现有以 `BlockPos`、`ResourceLocation` 等 Minecraft 类型表达的跨平台状态，应在边界处转换为项目自有值对象，例如 `BlockPoint`、字符串资源标识或专用记录类型。

最终平台 JAR 必须包含 `common` 编译输出，玩家不需要额外安装公共库 JAR。

### 4.2 `neoforge-1.21.1`

该模块承接现有 Minecraft 与 NeoForge 绑定逻辑：

- NeoForge 初始化、注册和事件总线
- NeoForge 网络载荷注册、收发与线程切换
- Minecraft 1.21.1 的方块模型、流体、方块实体和实体捕获
- 魔杖组件、菜单、GUI、输入、命令与 Overlay
- 纹理 Atlas、资源纹理和 GPU 纹理读取
- Flywheel 检测和 CPU 回退作用域
- `neoforge.mods.toml`、Access Transformer 和测试模组

此次结构迁移不得改变 NeoForge 的功能语义。现有测试应随对应源码移动，并继续通过。

### 4.3 `fabric-26.2`

Fabric 模块使用 26.2 API 重新实现平台边界：

- `ModInitializer` 与 `ClientModInitializer`
- Fabric Registry、Networking API 和生命周期事件
- 服务端权限验证、不可变授权快照与客户端接收器
- Fabric Screen/Menu、客户端命令、输入和世界渲染回调
- Minecraft 26.2 的方块模型、流体、方块实体和实体捕获
- Atlas、资源纹理与 GPU 纹理读取
- `fabric.mod.json`
- 仅在公开 API 无法满足捕获需求时使用最小范围 Access Widener 或 Mixin

Mixin 不得承担普通事件注册或可由 Fabric API 完成的功能。每个 Mixin 或访问放宽必须有明确用途和对应验证。

## 5. 运行数据流

```text
魔杖或命令输入
  → 平台服务端验证物品、维度、坐标与权限
  → 服务端返回不可变导出授权快照
  → 平台客户端按 Tick 预算捕获当前已加载世界
  → 平台捕获层转换为 common 场景数据
  → common 写出 glTF、BIN、USDA、纹理和 report.json
```

两个平台必须维持以下既有约束：

- 服务端权威验证导出请求。
- 实际渲染捕获与文件写入发生在发起操作的客户端。
- 未加载区块不被强制加载。
- 退出世界、切换维度、资源重载和关闭绑定 GUI 时，任务按既有生命周期取消或完成。
- 正常完成结果不因 GUI 关闭而删除。
- 软限制、确认流程、输出目录防冲突和事务清理语义不变。

## 6. 资源边界

可安全共享的资源包括：

- GUI 与物品纹理
- 中英文语言文件
- Logo
- 经验证不受版本格式影响的数据

必须按平台分别维护的资源包括：

- `neoforge.mods.toml` 与 `fabric.mod.json`
- 物品模型和配方 JSON
- Access Transformer、Access Widener 与 Mixin 配置
- 受 Minecraft 资源包或数据包格式变化影响的文件

Fabric 26.2 资源不得反向覆盖 NeoForge 1.21.1 可用资源。

## 7. 构建接口与产物

根项目支持以下命令：

```powershell
./gradlew.bat clean build
./gradlew.bat :common:test
./gradlew.bat :neoforge-1.21.1:build
./gradlew.bat :fabric-26.2:build
```

预期正式产物：

```text
neoforge-1.21.1/build/libs/MineToMesh-1.2.0-neoforge-1.21.1.jar
fabric-26.2/build/libs/MineToMesh-1.2.0-fabric-alpha.1+mc26.2.jar
```

根 `build` 必须聚合公共测试和两个平台构建。单独构建任一平台时，不应要求先手工构建或复制 `common` JAR。

## 8. 错误与降级策略

- 不得通过删除功能、吞掉异常或返回空结果来换取编译成功。
- 26.2 API 语义变化必须在 Fabric 平台层明确适配。
- 捕获失败需写入稳定诊断码和 `report.json`。
- 无法逆向恢复的第三方 GPU 对象继续生成半透明洋红包围盒。
- 平台模块构建失败不得覆盖另一平台已有产物。
- 损坏的持久选区配置继续隔离并以空状态启动。
- 网络载荷必须验证方向、玩家、魔杖身份、维度、坐标和权限，拒绝原因应可诊断。

## 9. 测试与验收

### 9.1 自动测试

1. `common` 纯 Java 单元测试全部通过。
2. NeoForge 现有测试全部通过，证明目录迁移未引入行为回归。
3. Fabric 为以下功能建立对应测试：
   - 注册与元数据完整性
   - 网络载荷编解码及方向约束
   - 权限、魔杖绑定与授权快照
   - 选区、Overlay 状态和持久锁定
   - 任务开始、取消、失败与完成生命周期
   - 服务端客户端类隔离
   - 资源和最终 JAR 内容完整性
4. 两个平台分别完成 `build`。
5. 两个平台分别完成无 GUI 服务端启动冒烟测试。

新增或改变行为遵守测试先行：先写会因缺失能力而失败的测试，确认失败原因正确，再实现最小代码并复验。单纯 Gradle 配置迁移使用构建失败作为红灯，并用结构与产物测试补足回归保障。

### 9.2 Fabric 客户端真实验收

在 Minecraft 26.2 Fabric 客户端中至少验证：

- 魔杖获取、POS1/POS2、空气取点、清除与 GUI 打开
- 中文输入、坐标编辑、开关、手持预览与持久锁定选区
- 单人导出和专用服务器权限拒绝/允许
- 原版普通方块、Tint Overlay、流体、方块实体、普通实体与玩家
- 退出、切换维度、资源重载和导出取消
- glTF、BIN、USDA、纹理与 `report.json` 同时生成
- 同名目录自动追加数字后缀
- glTF 与 USDA 的原点、尺度、材质、UV、拓扑和叠层结果

导出结果必须通过项目内部校验。工具环境允许时，再使用 Khronos glTF Validator，要求 `numErrors: 0`。

第三方模组兼容只在存在可运行的 Fabric 26.2 对应版本并完成真实测试后写入支持声明。

## 10. 完成定义

任务完成需同时满足：

- 根仓库形成 `common`、`neoforge-1.21.1`、`fabric-26.2` 三模块结构。
- NeoForge 1.21.1 正式版可继续编译，现有测试通过。
- Fabric 26.2 完整功能版本可编译，新增测试通过。
- 两个预期命名的可安装 JAR 均已生成。
- 无 GUI 服务端冒烟测试通过。
- Fabric 客户端最小真实导出验收完成，或将无法自动完成的人工验收项明确列为未验证，不能以编译通过冒充功能通过。
- 未覆盖或误提交用户已有的无关工作区改动。
