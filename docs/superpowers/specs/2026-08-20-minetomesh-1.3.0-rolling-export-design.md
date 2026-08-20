# MineToMesh 1.3.0 滚动区块导出设计

日期：2026-08-20  
状态：已确认，等待书面规格复核

## 1. 目标

MineToMesh 1.3.0 解决选区超出玩家视距后区块被跳过的问题，并提升超大选区导出的稳定性与可观测性：

1. 由服务端按滚动窗口强制加载选区区块，并临时切换发起玩家的区块追踪中心，使客户端能够捕获视距外内容。
2. 导出期间将全服 `randomTickSpeed` 临时设为 `0`，会话结束后可靠恢复。
3. 保留 Minecraft 主线程安全捕获，引入可配置的纯数据工作线程池和单线程确定性写入。
4. 在 GUI 中完整显示加载、同步、捕获、处理、写入、校验和清理进度。
5. 支持导出中途停止，并在取消、失败、断线、超时和服务器重启路径中释放所有资源。

版本号：

- Minecraft 1.21.1 NeoForge：`1.3.0`
- Minecraft 26.2 Fabric：`1.3.0-fabric-alpha.1`

最终产物：

- `neoforge-1.21.1/build/libs/MineToMesh-1.3.0-neoforge-1.21.1.jar`
- `fabric-26.2/build/libs/MineToMesh-1.3.0-fabric-alpha.1+mc26.2.jar`

## 2. 已确认约束

- 选区总区块数不设产品级上限。
- 服务端不会一次性强制加载全部选区。
- 每个滚动窗口处理 `1～16` 个水平区块，默认 `4`。
- 批次大小保存在当前魔杖中；旧魔杖缺少字段时使用 `4`。
- 全服同时只允许一个导出会话。
- 导出期间全服 `randomTickSpeed = 0`，完成或终止后恢复会话开始前的值。
- 允许临时切换发起玩家的客户端区块追踪中心；玩家实体和相机位置不变，但世界可能短暂卸载或闪烁。
- 用户可在 GUI 设置数据处理线程数；线程数保存在客户端本机配置，不写入魔杖。
- 工作线程上限按本机 CPU 计算，并硬限制为 `16`。
- 点击“停止导出”后 GUI 保持打开，完成清理后允许再次导出。
- GUI 关闭、断线、切维度、资源重载、绑定失效和超时仍会取消会话。
- Minecraft 世界、渲染器、纹理管理器和 GPU 访问不得移入通用工作线程。
- 输出顺序必须确定，相同输入不能因线程调度产生不同节点或材质编号。

## 3. 总体架构

1.3.0 使用服务端权威的全局 `ServerExportSessionManager` 和客户端流水线式 `ExportJob`。

```text
服务端唯一 ExportSession
    │ 验证、冻结随机刻、分配滚动窗口、持有区块票据
    ▼
客户端区块同步确认
    │
    ▼
Render Thread：6 ms/Tick 安全捕获
    │ RawChunkBatch
    ▼
1～N 个 CPU Worker：纯数据转换
    │ sequence-tagged ChunkBatch
    ▼
OrderedBatchBuffer：按 sequence 提交
    │
    ▼
单 Writer Thread：glTF/BIN/USDA/PNG/report.json
```

平台层分别实现 NeoForge 1.21.1 与 Fabric 26.2 的：

- 网络载荷注册与发送
- 区块加载票据
- 玩家区块追踪中心切换与恢复
- 服务端生命周期和断线清理
- 客户端 Tick、GUI 和渲染线程接入

`common` 继续只保存 loader-free 状态机、惰性游标、进度模型、并发调度、输出顺序和恢复策略，不导入 Minecraft 或加载器类型。

## 4. 服务端导出会话

### 4.1 全局互斥

`ServerExportSessionManager` 只允许一个非终态会话。新请求验证：

- 当前没有活动会话
- 玩家在线且连接有效
- 玩家具有导出权限
- 魔杖 UUID、绑定槽位和菜单仍有效
- 玩家维度与选区维度一致
- 选区坐标位于世界合法范围
- `batchChunkCount` 位于 `1～16`
- 请求没有复用活动或已完成的 `sessionId`

其他玩家请求时返回 `minetomesh.error.export.server_busy`。

### 4.2 状态机

```text
IDLE
→ PREPARING
→ LOADING_BATCH
→ WAITING_CLIENT
→ CAPTURING_BATCH
→ PROCESSING_BATCH
→ RELEASING_BATCH
→ LOADING_BATCH（还有批次）
→ FINALIZING
→ CLEANUP
→ COMPLETED
```

终止路径：

```text
任意非终态
→ CANCELLING 或 FAILED
→ CLEANUP
→ CANCELLED 或 FAILED
```

会话至少保存：

- `sessionId`
- 玩家 UUID
- 魔杖 UUID
- 维度标识
- 规范化选区
- 惰性水平区块游标
- 总区块数和总批次数
- 当前 `batchSequence`
- 当前批次区块坐标
- 每批区块数
- 原始 `randomTickSpeed`
- 原区块追踪中心
- 当前阶段开始时间
- 最近一次客户端心跳时间
- 已加载、同步、捕获、处理和写入计数

### 4.3 超时

| 阶段 | 超时 |
|---|---:|
| 服务端加载单批区块 | 60 秒 |
| 客户端确认本批可读 | 30 秒 |
| 客户端进度心跳 | 15 秒 |
| 取消清理 | 10 秒 |
| Writer 最终提交 | 120 秒 |

超时统一进入失败清理，禁止保留区块票据。

## 5. 惰性区块计划与滚动窗口

### 5.1 惰性游标

现有 `WorldPlanner` 不再为完整选区提前构造所有 `SectionWork`。公共层新增仅保存边界的范围对象和游标：

```text
ChunkRange(minChunkX, maxChunkX, minChunkZ, maxChunkZ)
ChunkBatchCursor.next(batchSize)
```

游标先按 `ChunkX → ChunkZ` 将选区划分成不超过 `4×4` 的紧凑宏窗口，再在每个宏窗口内部按用户批次大小 `1～16` 分组。任何单批区块都位于同一个最大 `4×4` 区域内，批次追踪中心取该批包围范围的中心；这样即使批次为 `16`，也不会形成超出低视距缓存的 `1×16` 长条。单个区块内部按 `SectionY` 和方块坐标稳定排序。内存中只保留当前宏窗口、当前批次及少量有界队列。

总区块数通过边界差直接计算为 `long`。总批次数按完整 `4×4` 宏窗口、X/Z 边缘窗口和角落窗口分别计算 `ceil(windowChunkCount / batchSize)` 后求和，不能简单使用全局 `ceil(totalChunks / batchSize)`。所有计算必须使用溢出检查；若 Minecraft 合法坐标仍导致计数无法表达，则请求失败并返回明确诊断，不能回绕成负数。

### 5.2 滚动加载

每个批次：

1. 从游标取得最多 `batchChunkCount` 个水平区块。
2. 服务端为这些区块添加 MineToMesh 专用票据。
3. 等待区块达到可发送状态。
4. 以当前紧凑批次包围范围的中心作为追踪中心，临时切换发起玩家的区块追踪中心。
5. 向客户端发送当前批次和坐标。
6. 客户端确认每个区块在 `ClientLevel` 中可读。
7. 客户端捕获、处理并确认批次完成。
8. 服务端释放本批票据。
9. 继续下一批。

在任何时刻，MineToMesh 主动持有的强加载区块数量不得超过本次批次大小。

### 5.3 失败语义

某批区块在超时内无法加载或同步时，整个导出失败。1.3.0 不再把该区块悄悄放入 `missingChunks` 后发布有缺口的正式结果。失败报告保留区块坐标和阶段诊断，事务目录不发布。

## 6. 区块追踪中心

会话开始时记录玩家原追踪中心。当前批次加载完成后，平台适配器临时控制该玩家的区块追踪中心，使远方区块可以进入客户端区块缓存。

约束：

- 玩家实体位置、朝向和相机位置保持不变。
- 同一时间只维护当前批次窗口。
- 客户端只有在 `level.hasChunk` 和批次坐标全部匹配后才确认可读。
- 批次切换期间 GUI 显示“同步区块”，不启动捕获。
- 会话完成、取消或失败后恢复原追踪中心，并触发原视距内容重新同步。
- 玩家断线时无需发送恢复包，但必须释放服务端票据；重连后由原版系统按真实位置建立追踪中心。
- 如果平台 API 无法可靠阻止原版追踪中心在导出期间覆盖临时中心，则该平台实现必须在服务端 Tick 中维持当前中心，不能退化为跳过远方区块。

## 7. 随机刻冻结与崩溃恢复

### 7.1 正常流程

进入 `PREPARING`：

1. 原子写入 `config/minetomesh/export-session-recovery.json`。
2. 记录会话标识、维度、原 `randomTickSpeed` 和写入时间。
3. 将全服 `randomTickSpeed` 设为 `0`。
4. 活动会话每个服务端 Tick 校验该规则仍为 `0`。

进入最终 `CLEANUP`：

1. 恢复会话开始前保存的值。
2. 让世界规则进入正常保存路径。
3. 原子删除恢复日志。
4. 释放全局会话锁。

管理员在活动会话期间修改该规则会被会话管理器重新压回 `0`；清理时恢复导出开始前的值。

### 7.2 异常恢复

服务器启动时，若恢复日志存在：

1. 在接受新导出前读取并验证文件。
2. 恢复记录中的原始 `randomTickSpeed`。
3. 清除可能残留的 MineToMesh 会话状态。
4. 删除恢复日志。
5. 将恢复结果写入服务器日志。

损坏的恢复文件不能被静默忽略。服务端记录错误并拒绝新的导出会话，避免在未知规则状态下继续运行。

## 8. 网络协议

新增或扩展以下双向载荷：

```text
BeginExportRequest
ExportSessionAccepted
ExportSessionRejected
BatchLoadStarted
BatchReady
BatchClientReadable
BatchCaptureCompleted
ExportProgressHeartbeat
CancelExportRequest
ExportCancelAcknowledged
ExportClientCompleted
ExportSessionFinished
ExportSessionFailed
```

所有会话载荷包含：

- `sessionId`
- `wandId`
- `dimension`

批次载荷额外包含：

- `batchSequence`
- 当前批次区块坐标

服务端拒绝旧批次、重复确认、非会话玩家、错误维度、错误魔杖、超出当前窗口的坐标和终态会话的迟到数据包。取消和完成消息必须幂等，重复到达不得重复恢复规则或移除无关票据。

## 9. 多线程数据流水线

### 9.1 线程边界

必须在 Minecraft 主线程或 Render Thread 执行：

- 服务端区块票据和游戏规则操作
- 客户端世界读取
- 方块模型、流体、实体和方块实体渲染器调用
- TextureManager、Atlas、NativeImage 和 GPU 纹理访问

允许在工作线程执行：

- 项目自有不可变顶点数据的坐标转换
- Quad/三角形拓扑转换
- 法线、UV、材质键和共面分层
- 区块内合批与诊断整理
- 不接触 Minecraft 对象的统计和中间文档构造

### 9.2 工作线程配置

```java
cpuThreads = Runtime.getRuntime().availableProcessors();
maxWorkers = max(1, min(16, cpuThreads - 2));
defaultWorkers = min(4, maxWorkers);
effectiveWorkers = min(configuredWorkers, currentBatchChunkCount);
```

- 配置范围为 `1～maxWorkers`。
- 始终为 Minecraft 保留两个逻辑线程；CPU 少于三个逻辑线程时仍允许一个 Worker。
- 当前批次小于配置线程数时不创建无效并发任务。
- 工作线程使用固定大小 Executor，不为每个区块创建新线程。
- 所有任务共享取消令牌，并在内部长循环中定期检查。

### 9.3 有界队列与确定性输出

Render Thread 输出带 `sequence` 的不可变 `RawChunkBatch`。Worker 可以乱序完成，但结果进入 `OrderedBatchBuffer`，Writer 只能按递增 `sequence` 写入。

队列均有界：

- 原始批次待处理数量不得无限增长。
- 已处理待写入容量为 `max(2, effectiveWorkers)`。
- 队列满时停止申请下一批并暂停生产，形成背压。

同一选区、同一资源状态和同一配置下，Worker 调度顺序不得改变节点、材质编号或文件结构。

### 9.4 实体归属

实体只在其当前位置所属水平区块批次中捕获。跨越区块边界的包围盒不能导致重复导出；玩家是否导出继续服从 `includePlayers`。

## 10. 进度模型

### 10.1 加权范围

| 百分比范围 | 计数来源 |
|---:|---|
| 0～5% | 服务端验证、恢复日志、冻结随机刻和建立会话 |
| 5～20% | 累计完成加载并同步到客户端的区块数 |
| 20～65% | 已完成客户端捕获的位置数和实体状态 |
| 65～80% | Worker 已处理完成的区块数 |
| 80～95% | Writer 已按序持久化的批次数 |
| 95～100% | 纹理、文档、校验、报告、发布和清理 |

加载、捕获、处理和写入可以重叠。公共 `ExportTelemetry` 保存各阶段独立计数，再计算单调不减的总体百分比。只有输出发布、服务端恢复和客户端追踪中心恢复均完成后才能显示 `100%`。

### 10.2 快照字段

进度快照至少包含：

- 会话状态和本地化阶段键
- 总百分比
- 当前批次序号与总批次数
- 已加载区块与总区块
- 当前批次已完成区块与批次大小
- 已捕获位置与总位置
- 已处理区块
- 已持久化批次
- 配置和实际工作线程数
- 处理队列和写入队列深度
- 当前区块坐标或对象标识
- 已用时间

GUI、`/minetomesh status` 和诊断日志读取同一不可变快照，不能各自计算不同百分比。

## 11. GUI 与持久化

### 11.1 每批区块数

- GUI 输入范围 `1～16`，默认 `4`。
- 支持文本输入、`±1` 按钮和滚轮。
- 保存到当前魔杖 `batchChunkCount` 字段。
- 服务端接收请求时重新读取当前绑定魔杖并校验，不能信任客户端单独提交值。
- 会话开始后锁定；GUI 中途修改只影响下一次导出。

### 11.2 数据处理线程

- 保存到 `config/minetomesh/client-export-settings.json`。
- 所有魔杖共用本机设置。
- GUI 显示 CPU 逻辑线程数和当前最大 Worker 数。
- 读取损坏配置时隔离损坏文件，回退到 `defaultWorkers` 并记录诊断。
- 超限输入自动钳制后持久化。
- 会话开始后锁定；中途修改只影响下一次导出。

### 11.3 按钮状态

| 状态 | 主按钮 | 次按钮 |
|---|---|---|
| 空闲 | 导出 | 关闭 |
| 等待服务端、加载、捕获、处理、写入 | 禁用 | 停止导出 |
| 正在取消或清理 | 禁用 | 禁用 |
| 完成、失败或取消 | 再次导出 | 关闭 |

“停止导出”不关闭 GUI。GUI 关闭仍发送取消请求并启动本地清理。

阶段名使用语言资源键，不直接暴露内部英文枚举值。

## 12. 取消与清理

点击停止后：

1. 客户端进入 `CANCELLING`，禁止新批次和新任务。
2. 设置本地共享取消令牌。
3. 清空尚未运行的 Worker 任务。
4. Writer 停止接受批次并关闭事务目录。
5. 向服务端发送幂等取消请求。
6. 服务端释放当前区块票据。
7. 服务端恢复追踪中心与 `randomTickSpeed`。
8. 服务端释放全局会话锁并返回确认。
9. 客户端显示“已取消”，保留 GUI 并允许重新开始。

清理步骤必须可重复调用。任何中间步骤失败时继续执行其余恢复动作，并汇总诊断，不能因为第一个异常跳过后续恢复。

## 13. 报告

`report.json` 增加：

```json
{
  "snapshotMode": "server_coordinated_rolling_window",
  "batchChunkCount": 4,
  "configuredWorkerThreads": 4,
  "effectiveWorkerThreads": 4,
  "totalChunks": 1024,
  "peakForcedChunks": 4,
  "cancelledAtStage": null,
  "randomTickSpeedBeforeExport": 3,
  "timingsMillis": {
    "serverPreparation": 0,
    "chunkLoading": 0,
    "clientSynchronization": 0,
    "capture": 0,
    "processing": 0,
    "writing": 0,
    "cleanup": 0
  }
}
```

正式成功结果不得包含缺失区块。失败或取消的诊断写入日志和可恢复的失败摘要；未发布事务目录仍按现有规则删除。

## 14. 错误处理

稳定错误键至少包括：

- `minetomesh.error.export.server_busy`
- `minetomesh.error.export.invalid_batch_size`
- `minetomesh.error.export.invalid_worker_count`
- `minetomesh.error.export.chunk_load_timeout`
- `minetomesh.error.export.chunk_sync_timeout`
- `minetomesh.error.export.session_mismatch`
- `minetomesh.error.export.tracking_restore_failed`
- `minetomesh.error.export.random_tick_restore_failed`
- `minetomesh.error.export.recovery_file_corrupt`

客户端可见信息使用语言资源，`report.json` 同时保存稳定诊断码、批次序号和相关区块坐标。

## 15. 测试策略

所有新增行为按测试先行实现。

### 15.1 common

- 惰性游标不会提前构造全量区块列表。
- 批次大小为 `1`、`4`、`16` 及最后不足一批时顺序正确。
- 坐标范围、总数和批次数溢出时明确失败。
- CPU 上限、默认值、钳制和有效线程数计算正确。
- Worker 乱序完成时 Writer 仍按 `sequence` 提交。
- 有界队列产生背压且取消可解除等待。
- 进度覆盖所有阶段、单调不减且仅在完整恢复后为 `100%`。
- 清理动作幂等并在部分失败后继续执行。

### 15.2 平台模块

NeoForge 与 Fabric 分别测试：

- 服务端全局会话互斥。
- 权限、魔杖绑定、维度和批次值验证。
- 区块票据申请、批次切换和释放。
- 追踪中心切换、维持和恢复。
- 随机刻保存、冻结、正常恢复和启动恢复。
- 重复包、迟到包、错误会话和非会话玩家拒绝。
- 取消、断线、维度变化、资源重载、超时和服务端停止清理。
- 旧魔杖默认批次值为 `4`。
- 客户端线程配置按本机 CPU 钳制。
- Dedicated Server 不链接客户端类。
- 两个平台 JAR 版本、入口点、公共核心和资源完整。

### 15.3 构建与启动

- `:common:test`
- `:neoforge-1.21.1:test :neoforge-1.21.1:build`
- `:fabric-26.2:test :fabric-26.2:build`
- 两个平台专用服务器冒烟
- 根 `build`

### 15.4 人工验收

至少验证：

1. 选区明显超出当前视距。
2. 批次大小分别为 `1`、`4`、`16`。
3. Worker 分别为 `1`、默认值和本机上限。
4. 加载、捕获、处理、写入、校验和清理阶段均可见。
5. 导出中途停止，GUI 保持打开并可再次导出。
6. 取消、断线和失败后区块票据、追踪中心和随机刻恢复。
7. 导出期间作物不随机生长、火焰不随机扩散。
8. 远方区块完整出现在 glTF 与 USDA。
9. Blender 5.2 中轴向、比例、材质、UV、实体和方块实体正确。
10. Khronos glTF Validator `numErrors: 0`。

## 16. 完成定义

1. 两个平台版本和最终 JAR 名称更新到 1.3.0 系列。
2. 超出视距的选区通过滚动窗口完整导出，不再以缺失区块形式发布成功结果。
3. MineToMesh 主动强加载区块峰值不超过 GUI 批次大小。
4. 全服唯一会话和所有终止路径均能恢复 `randomTickSpeed`、区块票据和追踪中心。
5. Worker 数由用户配置并受 CPU 与硬上限约束。
6. Minecraft API 访问保持在合法线程，纯数据阶段可以并行。
7. Writer 输出顺序确定，重复导出不受线程调度影响。
8. GUI 进度覆盖完整生命周期，停止按钮可中途取消且保持 GUI 打开。
9. 自动测试、双平台构建和服务端冒烟全部通过。
10. 无法自动完成的真实客户端与 Blender 项目明确记录，不能以构建成功替代人工验收。
