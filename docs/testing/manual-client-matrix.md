# 客户端手工验收矩阵

统一流程：进入测试世界，按“设置”放置对象；站在选区两个角分别执行 `/mcgltf pos1`、`/mcgltf pos2`，然后执行“命令”。检查导出目录中的 `.gltf`、`.bin`、`textures/`、`materials/` 与 `report.json`。

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
| 模组 BakedModel | 放置 `mcgltf_test:model_data_block` | `/mcgltf export model_data` | 紫水晶纹理 | 方块仅出现一次 | 无 ModelData 失败 |
| 模组 BER/实体 | 放置 rendered_block 并生成 test_entity | `/mcgltf export mod_renderers` | 钻石、紫水晶纹理 | 青色 BER 与实体出现 | rendererClass 正确 |
| 未加载区块 | 选区跨越视距外区块 | `/mcgltf export unloaded` | 正常输出 | 仅已加载部分出现 | missingChunks 非空且未加载新区块 |
| 取消 | 启动大选区后立即取消 | `/mcgltf cancel` | 无正式目录、临时目录被清理 | 无结果导入 | 状态 CANCELLED |
| 资源重载取消 | 导出中按 F3+T | `/mcgltf status` | 无正式目录 | 无半成品发布 | 原因 resource_reload |
| 同名后缀 | 连续导出同名两次 | `/mcgltf export duplicate` | `duplicate/` 与 `duplicate-2/` | 两份均可导入 | 两份报告完成 |
| Blender 导入 | 导入 smoke.gltf | `/mcgltf export smoke` | 相对资源完整 | 层级、比例、轴向正确 | origin extras 存在 |
| Khronos 验证 | 安装 tools 依赖 | `npm run validate -- ..\run\mcgltf-exports\smoke\smoke.gltf` | 验证 JSON | 不适用 | `numErrors: 0` |
| GPU-only 降级 | 放置 `mcgltf_test:gpu_only_block` | `/mcgltf export gpu_only` | generated 白纹理 | 洋红半透明 AABB | `BLOCK_ENTITY_ZERO_VERTICES` |
| 自定义流体 | 放置测试紫色流体 | `/mcgltf export purple_fluid` | 水 still/flow 副本 | 色调为 `#8A4FFF` | 流体计数 +1 |
