# Fabric 26.2 Alpha.1 验证记录

验证日期：2026-08-20

## 自动化结果

| 项目 | 命令 / 证据 | 结果 |
|---|---|---|
| 公共核心 | `./gradlew.bat clean :common:test --no-configuration-cache` | 通过 |
| NeoForge 回归 | `:neoforge-1.21.1:test :neoforge-1.21.1:build` | 通过 |
| Fabric 测试与构建 | `:fabric-26.2:test :fabric-26.2:build` | 通过 |
| NeoForge 专用服务器 | `:neoforge-1.21.1:runServerSmoke` | 输出 `MINETOMESH_SERVER_READY` 后受控退出 |
| Fabric 专用服务器 | `:fabric-26.2:fabricServerSmoke` | Minecraft 26.2、Java 25 启动，输出 `MINETOMESH_SERVER_READY` 后受控退出 |
| 根聚合构建 | `./gradlew.bat build --no-configuration-cache` | 通过 |
| Fabric JAR 契约 | `FabricJarContractTest` | 双入口、公共 glTF/USDA 核心、资源、配方及 77 张 GUI 切片通过 |

## 客户端启动

`./gradlew.bat :fabric-26.2:runClient` 已完成一次约 5 分钟启动冒烟并正常退出。日志确认加载：

- Minecraft 26.2
- Fabric Loader 0.19.3
- MineToMesh 1.2.0-fabric-alpha.1
- MineToMesh 资源参与 ResourceManager 重载

开发启动器使用离线伪会话，日志中出现 Mojang 用户属性与 Realms 的 401 鉴权错误；未发现 MineToMesh 初始化异常。这只能证明客户端可启动，不能证明游戏内导出功能已通过。

## 产物

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `MineToMesh-1.2.0-neoforge-1.21.1.jar` | 1,015,447 B | `3CDD2F515BC33C6558A1F04EBAC3D38821069D1BE72EA04870EF82D663929986` |
| `MineToMesh-1.2.0-fabric-alpha.1+mc26.2.jar` | 1,025,094 B | `32EB53093F5CC13FBCA86CA37A6F84F41206C090FF3D85D5FC85AEEB986E8DE0` |

SHA-256 对应本次验证构建；源码或资源再次变化后必须重新计算。

## 尚未执行的人工验收

以下项目明确为**未执行**：

- 在真实 Fabric 26.2 世界中获取魔杖并完成方块、空气、跨维度取点
- GUI 坐标编辑、中文输入法、Overlay 遮挡及锁定选区跨重启
- 普通玩家与管理员的真实专用服务器权限闭环
- 实际导出原版方块、流体、实体和方块实体
- 核对 `.gltf`、`.bin`、`.usda`、PNG 与 `report.json`
- 在 Blender 5.2 检查轴向、尺度、UV、材质、Quad 与共面分层
- Khronos glTF Validator `numErrors: 0`
- 第三方 Fabric 渲染模组兼容性

因此 `1.2.0-fabric-alpha.1` 当前可定义为“自动化构建、双端启动与包结构通过的 Alpha 候选”，不能定义为“完整人工验收通过”。
