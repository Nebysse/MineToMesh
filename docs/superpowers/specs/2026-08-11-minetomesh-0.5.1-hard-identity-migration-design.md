# MineToMesh 0.5.1 完整身份硬迁移设计

日期：2026-08-11
状态：设计已确认，待正式规格复核
基线分支：`main`
目标版本：`0.5.1`
Minecraft：1.21.1
NeoForge：21.1.244

## 1. 目标

MineToMesh 0.5.1 将项目现行的 `mcgltf` 内部身份完整迁移为 `minetomesh`，覆盖构建产物、NeoForge Mod ID、Java 包、资源命名空间、网络 Payload、命令入口、测试模组和导出目录。

本次采用硬迁移策略，不提供旧命名空间兼容壳、Missing Mapping 或命令别名。完成后，生产代码、生产资源、当前测试和当前发布文档只使用 MineToMesh 新身份。

## 2. 已确认方案

采用完整硬迁移，不采用以下折中方案：

- 不只修改游戏内显示名称。
- 不只修改 JAR 文件名。
- 不保留 `mcgltf` 作为运行时 Mod ID。
- 不保留 `com.onecuber.mcgltf` Java 包。
- 不保留 `/mcgltf` 命令别名。
- 不提供 `mcgltf:*` 到 `minetomesh:*` 的缺失注册映射。

## 3. 0.5.1 身份契约

| 身份项目 | 0.5.1 值 |
|---|---|
| 版本号 | `0.5.1` |
| NeoForge Mod ID | `minetomesh` |
| 显示名称 | `MineToMesh` |
| Java 根包 | `com.nebysse.minetomesh` |
| Maven Group | `com.nebysse.minetomesh` |
| 构建产物 | `MineToMesh-0.5.1.jar` |
| 资源命名空间 | `minetomesh:*` |
| 主命令 | `/minetomesh` |
| 客户端导出目录 | `.minecraft/minetomesh-exports/` |
| 测试模组 ID | `minetomesh_test` |
| 服务端烟测系统属性 | `minetomesh.serverSmoke` |
| 服务端就绪标记 | `MINETOMESH_SERVER_READY` |
| Git 标签 | `v0.5.1` |
| GitHub Release 标题 | `MineToMesh 0.5.1` |

## 4. Java 包与类名迁移

所有受版本控制的 Java 源码迁移到以下根路径：

```text
src/main/java/com/nebysse/minetomesh/
src/test/java/com/nebysse/minetomesh/
src/testmod/java/com/nebysse/minetomesh/
```

所有 `package com.onecuber.mcgltf...` 和对应 import 改为 `com.nebysse.minetomesh...`。

品牌入口类同步改名：

- `McGltf` → `MineToMesh`
- `McGltfClient` → `MineToMeshClient`
- `McGltfContent` → `MineToMeshContent`
- `McGltfMetadataTest` → `MineToMeshMetadataTest`
- `McGltfTestMod` → `MineToMeshTestMod`

描述 glTF 文件格式的类型保留原名，例如 `GltfDocumentBuilder`、`StreamingGltfSession` 和 `InternalGltfValidator`。这里的 `Gltf` 表示标准格式，不属于旧品牌身份。

## 5. NeoForge 与构建身份

`gradle.properties` 采用：

```properties
mod_id=minetomesh
mod_name=MineToMesh
mod_version=0.5.1
mod_group_id=com.nebysse.minetomesh
```

Gradle `archivesName` 使用 `mod_name`，确保文件名严格为：

```text
MineToMesh-0.5.1.jar
```

生成后的 `META-INF/neoforge.mods.toml` 必须包含：

```toml
modId="minetomesh"
version="0.5.1"
displayName="MineToMesh"
```

客户端和服务端依赖 side 保持 `BOTH`。主模组测试绑定改为 `mods.minetomesh`，测试模组注册名改为 `minetomesh_test`。

## 6. 注册表与资源命名空间

资源目录执行完整移动：

```text
assets/mcgltf/ → assets/minetomesh/
data/mcgltf/   → data/minetomesh/
```

所有生产代码、JSON、语言文件、模型、配方、纹理、数据组件、菜单、物品和网络 Payload 使用 `minetomesh` 命名空间。

示例：

```text
mcgltf:export_wand → minetomesh:export_wand
mcgltf:wand_selection → minetomesh:wand_selection
```

语言键同步迁移，例如：

```text
item.mcgltf.export_wand → item.minetomesh.export_wand
itemGroup.mcgltf → itemGroup.minetomesh
```

生产 JAR 不得包含 `assets/mcgltf/`、`data/mcgltf/` 或任何旧工作台资源。

## 7. 网络、命令与运行时标识

所有自定义 Payload 的 `ResourceLocation` 命名空间改为 `minetomesh`。0.5.1 客户端与旧版服务端不保证协议兼容，客户端和服务端必须安装同一版本。

命令根节点从：

```text
/mcgltf
```

硬迁移为：

```text
/minetomesh
```

不注册旧命令别名。

服务端烟测属性从 `mcgltf.serverSmoke` 改为 `minetomesh.serverSmoke`，就绪文本继续使用已经符合新品牌的 `MINETOMESH_SERVER_READY`。

日志记录器、线程名、诊断中的品牌标签及测试夹具描述使用 MineToMesh 或 `minetomesh`，不继续生成新的 `mcgltf` 运行时文本。

## 8. 导出目录迁移

新导出的根目录为：

```text
.minecraft/minetomesh-exports/
```

0.5.1 不移动、合并或删除用户已有的：

```text
.minecraft/mcgltf-exports/
```

旧目录保留在磁盘上供用户自行归档。新版本只向 `minetomesh-exports` 写入。

输出格式、同名目录后缀、事务目录、glTF/OBJ 文件结构和报告内容保持现有行为。

## 9. 破坏性兼容策略

本次明确选择 C1 硬迁移：

- 不注册 Missing Mapping。
- 不保留旧 Mod ID 壳。
- 不保留旧资源命名空间壳。
- 不保留旧 Payload ID。
- 不保留旧命令别名。
- 不尝试迁移旧魔杖的数据组件。

旧世界中的 `mcgltf:*` 物品、组件或注册对象可能变成缺失项。README 与 GitHub Release Notes 必须醒目标注：升级前备份世界，0.5.1 不保证旧魔杖继续存在。

该策略不会主动修改世界文件，也不会主动删除旧导出文件。

## 10. 文档边界

当前用户文档统一使用：

- `MineToMesh-0.5.1.jar`
- `/minetomesh`
- `.minecraft/minetomesh-exports/`
- `minetomesh:*`

历史规格、历史计划和迁移说明允许出现 `mcgltf`，前提是语境明确指向旧版本或旧身份。自动残留扫描不得简单禁止整个仓库出现旧字符串，而应分别扫描生产源码、生产资源、当前测试与生成 JAR。

## 11. 测试策略

实施采用测试驱动开发，先建立会失败的 0.5.1 身份契约：

1. `MineToMesh.VERSION == "0.5.1"`。
2. 生成元数据包含 `modId="minetomesh"` 与 `version="0.5.1"`。
3. glTF generator 为 `MineToMesh 0.5.1`。
4. README 包含新 JAR、命令、导出目录及破坏性警告。
5. Java 源码位于 `com.nebysse.minetomesh`。
6. 当前生产源码和生产资源不含旧包声明与旧命名空间。
7. 命令只注册 `/minetomesh`。
8. 输出管线只写入 `minetomesh-exports`。
9. 测试模组 ID 与服务端烟测属性使用 `minetomesh`。

机械验证包含：

```powershell
.\gradlew.bat clean test build
.\gradlew.bat runServerSmoke
```

生产 JAR 审计必须确认：

- 文件名为 `MineToMesh-0.5.1.jar`。
- 包含 `com/nebysse/minetomesh/`。
- 不含 `com/onecuber/mcgltf/`。
- 包含 `assets/minetomesh/` 与 `data/minetomesh/`。
- 不含 `assets/mcgltf/`、`data/mcgltf/`、`mcgltf_test` 或 testmod 类。
- 元数据 version、Mod ID、显示名称及 side 正确。

## 12. 发布流程

只有全部机械验证通过后才执行远端发布：

1. 将迁移分支合并到 `main`。
2. 在合并后的 `main` 再次运行完整构建与服务端烟测。
3. 推送 `main` 到 `minetomesh/main`。
4. 创建带注释标签 `v0.5.1` 并推送。
5. 创建 GitHub Release `MineToMesh 0.5.1`。
6. 上传 `MineToMesh-0.5.1.jar`。
7. Release Notes 列出身份迁移、连接纹理与 Overlay 能力，并明确硬迁移风险。
8. 计算并公布 SHA-256。
9. 核对远端 `main`、标签目标和 Release 资产哈希与本地一致。

若 GitHub 身份验证或 Release API 不可用，停止在最后一个已验证且可恢复的步骤，报告具体阻塞，不伪造发布完成状态。

## 13. 验收标准

0.5.1 只有在以下条件全部满足后才可宣称发布完成：

- 版本、Mod ID、Java 包、Maven Group、JAR 名和资源命名空间符合身份契约。
- `/minetomesh` 可用且 `/mcgltf` 未注册。
- 新导出写入 `.minecraft/minetomesh-exports/`。
- `clean test build` 全绿。
- `runServerSmoke` 加载 MineToMesh 0.5.1 并输出 `MINETOMESH_SERVER_READY`。
- 生产 JAR 残留扫描通过。
- `main` 已推送。
- `v0.5.1` 已推送且指向发布提交。
- GitHub Release 已创建并包含 JAR。
- Release 资产 SHA-256 已记录。

真实客户端中的旧世界缺失项表现、新命令交互和新导出目录由项目所有者人工验收；自动化测试不替代该人工结论。
