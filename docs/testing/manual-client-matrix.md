# 客户端手工验收矩阵

统一流程：进入测试世界，按“设置”放置对象；站在选区两个角分别执行 `/mcgltf pos1`、`/mcgltf pos2`，然后执行“命令”。检查导出目录中的 `.gltf`、`.bin`、`.obj`、`.mtl`、`textures/`、`materials/` 与 `report.json`。

## 0.3.0 区域导出工作台矩阵

以下场景针对工作台 GUI、选区显示与双端行为，需在服务端安装 MineToMesh 后执行。

| 场景 | 设置 | 预期结果 |
|---|---|---|
| 四个水平朝向 | 依次在四个朝向放置工作台 | 正面图标（蓝色线框立方体 + 橙色箭头）始终朝向放置者 |
| GUI Scale 2/3/4 | 依次切换 GUI 缩放并打开工作台 | 双栏仪表台无重叠、无裁切、全部控件可点击 |
| 两名玩家 | 两名玩家同时打开同一工作台 | 各自看到同一组坐标，最后一次合法提交生效，无掉线 |
| 手动坐标 | 输入框输入负数与大数 | 回车/失焦提交，非法输入标红且不发送请求 |
| 步进控制 | 上下按钮、滚轮、Shift+滚轮 | `±1`、`±1`、`±10`，悬停输入框时生效 |
| 脚下取点 | 点击“脚下”按钮 | 端点变为玩家脚下方块，Y 不越界 |
| 导出名 | 修改导出名并导出 | 非法名标红并禁用导出；名称不写入方块实体 |
| 选区显示 | GUI 中开启，关闭 GUI 观察 | 橙色半透明体积 + 蓝色边线持续显示 |
| 深度遮挡 | 选区穿过山体/墙 | 被实体方块正常遮挡，不穿墙显示 |
| 方块拆除 | 开启选区后拆掉工作台 | 对应选区显示清除 |
| 维度切换 | 开启选区后切换维度 | 旧维度选区清除 |
| 关闭即取消 | 大选区导出中关闭 GUI | 任务取消，事务目录清理，无正式输出 |
| 完整诊断 | 导出后查看 | 游戏内仅摘要，`report.json` 含完整诊断 |
| 合成 | 按 IGI/RCR/III 合成 | 产出一个工作台，创造页签也有 |
| 专用服务器 | 仅服务端无客户端环境启动 | 服务端正常加载，无客户端类错误 |
| 保存重载 | 改坐标后保存并重进世界 | 坐标保持 |
| 多人共享 | 两名玩家共享同一工作台 | 选区为方块所有，不属于玩家 |

## 通用工作台流程

统一流程：进入测试世界，按“设置”放置对象；站在选区两个角分别执行 `/mcgltf pos1`、`/mcgltf pos2`，然后执行“命令”。检查导出目录中的 `.gltf`、`.bin`、`.obj`、`.mtl`、`textures/`、`materials/` 与 `report.json`。

| 场景 | 设置 | 命令 | 预期文件 | 预期视觉结果 | 报告预期 |
|---|---|---|---|---|---|
| 石头 | 放置石头 | `/mcgltf export stone` | 五类输出齐全 | 六面、比例一格一米 | 无失败 |
| 草方块 | 放置草方块 | `/mcgltf export grass` | 草方块纹理独立 | 顶部/侧面着色正确 | 无失败 |
| 树叶 | 放置橡树树叶 | `/mcgltf export leaves` | 叶片 PNG、材质 sidecar | MASK 边缘与双面正确 | 无失败 |
| 玻璃 | 放置玻璃 | `/mcgltf export glass` | 玻璃纹理与 BLEND 材质 | 透明面可见 | 无失败 |
| 水 | 水源与流动水相邻 | `/mcgltf export water` | still/flow PNG | 高度、流向 UV 正确 | 无未解析 Sprite |
| 熔岩 | 熔岩源与流动熔岩 | `/mcgltf export lava` | still/flow PNG | 高度及动画首帧正确 | 动画元数据存在 |
| 箱子 | 放置箱子 | `/mcgltf export chest` | 箱子资源纹理 | BER 几何出现 | rendererClass 已记录 |
| 告示牌 | 写字告示牌 | `/mcgltf export sign` | 木材纹理 | 木板导出，文字不导出 | 文本 RenderType 被跳过 |
| 旗帜 | 放置图案旗帜 | `/mcgltf export banner` | 可读纹理或降级纹理 | 旗帜实体几何出现 | 动态纹理诊断稳定 |
| 牛 | 生成牛 | `/mcgltf export cow` | 牛纹理 | 静态姿态、无影子火焰 | UUID 与类型存在 |
| 盔甲架 | 放置盔甲架 | `/mcgltf export armor_stand` | 实体材质 | 当前姿态出现 | 非玩家实体计数 +1 |
| 掉落物 | 丢出物品 | `/mcgltf export item` | 物品纹理 | 掉落物静态姿态 | UUID 已记录 |
| 船 | 放置船 | `/mcgltf export boat` | 船纹理 | 车辆实体完整 | 实体计数 +1 |
| 模组 BakedModel + 空附加渲染器 | 放置 `mcgltf_test:model_data_block` | `/mcgltf export model_data` | glTF/OBJ 及紫水晶纹理 | 静态方块仅出现一次、无占位符 | `AUXILIARY_RENDERER_EMPTY` 可为 INFO，状态仍为 `completed` |
| 模组 BER/实体 | 放置 rendered_block 并生成 test_entity | `/mcgltf export mod_renderers` | 钻石纹理与 generated 运行时纹理 | 青色 BER 与实体出现 | rendererClass 正确 |
| GPU 运行时纹理 | 生成 `mcgltf_test:test_entity` | `/mcgltf export gpu_texture` | `textures/generated/<hash>.png` | 2×2 四角依次为左上红、右上绿、左下蓝、右下白，UV 不倒置 | `GPU_TEXTURE_READBACK_USED` 为 INFO，无 `TEXTURE_READ_FAILED` |
| 未加载区块 | 选区跨越视距外区块 | `/mcgltf export unloaded` | 正常输出 | 仅已加载部分出现 | missingChunks 非空且未加载新区块 |
| 取消 | 启动大选区后立即取消 | `/mcgltf cancel` | 无正式目录、临时目录被清理 | 无结果导入 | 状态 CANCELLED |
| 资源重载取消 | 导出中按 F3+T | `/mcgltf status` | 无正式目录 | 无半成品发布 | 原因 resource_reload |
| 同名后缀 | 连续导出同名两次 | `/mcgltf export duplicate` | `duplicate/` 与 `duplicate-2/` | 两份均可导入 | 两份报告完成 |
| Blender 导入 | 导入 smoke.gltf | `/mcgltf export smoke` | 相对资源完整 | 层级、比例、轴向正确 | origin extras 存在 |
| Khronos 验证 | 安装 tools 依赖 | `npm run validate -- ..\run\mcgltf-exports\smoke\smoke.gltf` | 验证 JSON | 不适用 | `numErrors: 0` |
| 通用后端回退 | 放置 `mcgltf_test:gpu_only_block` | `/mcgltf export gpu_only` | glTF/OBJ 双格式完整 | 回退几何出现、无洋红占位符；OBJ 中原始 Quad 保留为四边面 | `RENDER_BACKEND_FALLBACK_USED` 为 INFO，adapter 为 `mcgltf_test` |
| 后端状态恢复 | 完成上述导出后继续观察测试对象至少一分钟 | 无 | 无额外文件要求 | 正常测试模组渲染保持可见，无状态泄漏或闪烁 | 无 `RENDER_BACKEND_FALLBACK_FAILED` |
| 自定义流体 | 放置测试紫色流体 | `/mcgltf export purple_fluid` | 水 still/flow 副本 | 色调为 `#8A4FFF` | 流体计数 +1 |

## 真实模组验收矩阵

目标实例：`D:\data\.minecraft\versions\1.21.1-NeoForge_21.1.244`。安装候选 JAR 前必须先取得用户确认；将 `mcgltf-0.1.0.jar` 可逆地改名为 `.disabled`，再复制 `mcgltf-0.2.0.jar`。

测试版本：Create `6.0.10`、Flywheel `1.0.6`、Touhou Little Maid `1.5.3`。

1. 选区包含 Create 齿轮箱、带支架传动件、空储液罐、满储液罐及一只 Touhou 女仆。
2. 导出后要求此前 17 个 Create 对象的错误 `BLOCK_ENTITY_ZERO_VERTICES` 占位符计数为 0。
3. 要求齿轮箱与储液罐无重复几何，传动轴保持当前姿态，满罐液位与游戏内一致。
4. 要求女仆皮肤非棋盘格；女仆皮肤与 `minecraft` 方块图集均无 `TEXTURE_READ_FAILED`。
5. `AUXILIARY_RENDERER_EMPTY` 允许以 INFO 保留，不得使状态变为 `completed_with_warnings`。
6. 分别将 glTF 与 OBJ 导入 Blender 5.2，对比世界原点、尺度、手性、对象位置、材质与 UV；glTF 应为三角面，OBJ 的源 Quad 应保留为四边面。
7. 对 glTF 运行 Khronos Validator，要求 `numErrors: 0`。
8. 导出结束后继续游玩至少一分钟，要求 Flywheel 视觉保持激活、无对象消失或闪烁。

## 0.3.0 候选包验收记录（未执行）

- 自动化：`clean test build`、`ServerClassIsolationTest`、`runServerSmoke` 由代理执行并记录；其余条目等待用户执行。
- 开发客户端启动与工作台 GUI 验收：待用户执行。
- 真实 Create/Touhou 实例、专用服务器、Khronos Validator 与 Blender 5.2 双格式对比：待用户执行。

## 0.2.0 候选包验收记录（2026-08-10）

- 自动化：在 NeoForge `21.1.244` 基线上执行 `clean test build`，43 个测试套件、124 个测试全部通过，0 failure、0 error、0 skipped。
- 元数据：生成的 `neoforge.mods.toml` 含 `versionRange="[21.1.244,)"`。
- 产物隔离：`mcgltf-0.2.0.jar` 包含 `backend/` 与 `obj/`，不含 `mcgltf_test`、`TestBackendAdapter` 或 `GpuResidentTexture`；`jdeps` 未发现 Create 字节码依赖。
- 开发客户端启动：通过。日志确认 MC glTF Exporter `0.2.0`、兼容夹具 `0.1.0`、NeoForge `21.1.244` 均已加载，资源重载包含 `mod/mcgltf` 与 `mod/mcgltf_test`。
- 开发客户端夹具导出：由用户执行目标方块验证，本记录暂不宣称通过。
- 真实 Create/Touhou 实例、Khronos Validator 与 Blender 5.2 双格式对比：由用户执行，本记录暂不宣称通过。
