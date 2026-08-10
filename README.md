# MC glTF Exporter

面向 Minecraft 1.21.1 NeoForge 的客户端侧世界导出模组，将客户端当前已加载的选区写成 Blender 可编辑的 glTF 2.0 场景。

## 特性

- 服务器无需安装，联机时只读取客户端已经收到的世界状态。
- 支持原版及模组 `BakedModel`、流体、方块实体渲染器和普通实体渲染器。
- 保留独立 PNG 纹理、顶点色、透明模式、双面和发光语义。
- 输出层级固定为 `Chunks`、`BlockEntities`、`Entities`、`Placeholders`。
- 坐标转换为 `(X,Y,Z) → (X,Y,-Z)`，选区最小点作为局部原点，一格对应 Blender 一米。
- 捕获采用滚动快照：实体先捕获，随后按稳定顺序逐区段扫描。每客户端 Tick 预算约 6 ms，后台写入队列容量为 2。

## 安装

1. 安装 Minecraft 1.21.1、NeoForge 21.1.244 或更高的兼容 21.1.x 版本。
2. 将 `mcgltf-0.1.0.jar` 放入客户端 `.minecraft/mods/`。
3. 启动客户端。服务端无需安装本模组。

## 指令

```text
/mcgltf pos1
/mcgltf pos2
/mcgltf export <名称>
/mcgltf export <名称> confirm
/mcgltf status
/mcgltf cancel
```

`pos1`、`pos2` 使用玩家当前所在方块。名称含空格时使用引号。选区超过软限制 **4,194,304 格**时，需要执行聊天中给出的 `confirm` 指令。

## 输出

输出根目录为 `.minecraft/mcgltf-exports/`。同名目录已存在时自动使用 `名称-2`、`名称-3` 等后缀。

```text
mcgltf-exports/<名称>/
├─ <名称>.gltf
├─ <名称>.bin
├─ report.json
├─ textures/
│  ├─ minecraft/
│  ├─ <modid>/
│  └─ generated/
└─ materials/
   └─ *.json
```

未加载区块不会被强制加载，会记录在 `report.json`。捕获期间若世界继续变化，不同区段可能来自不同 Tick，因此输出属于滚动快照。

## Blender 导入

在 Blender 中选择“文件 → 导入 → glTF 2.0”，打开导出目录中的 `.gltf`。请保持 `.gltf`、`.bin` 与 `textures/` 的相对目录结构不变。

Minecraft 的标准渲染路径会被直接捕获。仅通过自定义 GPU 绘制、着色器注入或绕过 `VertexConsumer` 输出的对象无法还原时，会生成半透明洋红包围盒，并在报告中记录稳定诊断码。阴影、火焰、名称文本、Minecraft 光照和 AO 不会烘焙进导出结果。

## 验证

```powershell
./gradlew.bat clean test build
Set-Location tools
npm install
npm run validate -- ..\run\mcgltf-exports\smoke\smoke.gltf
```

## 许可证

MIT，详见 [LICENSE](LICENSE)。
