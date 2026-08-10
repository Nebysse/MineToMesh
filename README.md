# MC glTF Exporter

面向 Minecraft 1.21.1 NeoForge 的客户端侧世界导出模组，将客户端当前已加载的选区写成 Blender 可编辑的 glTF 2.0 场景。

## 特性

- 服务器无需安装，联机时只读取客户端已经收到的世界状态。
- 支持原版及模组 `BakedModel`、流体、方块实体渲染器和普通实体渲染器。
- 每次导出同时生成 glTF 2.0 与 OBJ；两种格式共用同一套纹理。
- glTF 按规范三角化；OBJ 精确保留捕获到的原始 Quad，其他拓扑不会被猜测性合并。
- OBJ 普通方块按区段与材质合并，方块实体、实体和占位符保持独立对象。
- 保留独立 PNG 纹理、顶点色、透明模式、双面和发光语义。
- 纹理按资源文件、CPU 动态纹理、GPU level 0 回读的顺序获取，兼容只有运行时纹理的渲染器。
- 通用渲染后端作用域可临时切换到标准 `VertexConsumer` 备用路径；首个内置适配器支持 Flywheel 1.x，不依赖 Create 私有模型。
- 输出层级固定为 `Chunks`、`BlockEntities`、`Entities`、`Placeholders`。
- 坐标转换为 `(X,Y,Z) → (X,Y,-Z)`，选区最小点作为局部原点，一格对应 Blender 一米。
- 捕获采用滚动快照：实体先捕获，随后按稳定顺序逐区段扫描。每客户端 Tick 预算约 6 ms，后台写入队列容量为 2。

## 安装

1. 安装 Minecraft 1.21.1、NeoForge 21.1.244 或更高的兼容 21.1.x 版本。
2. 将 `mcgltf-0.2.0.jar` 放入客户端 `.minecraft/mods/`，并移除旧版 JAR。
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
├─ <名称>.obj
├─ <名称>.mtl
├─ report.json
├─ textures/
│  ├─ minecraft/
│  ├─ <modid>/
│  └─ generated/
└─ materials/
   └─ *.json
```

未加载区块不会被强制加载，会记录在 `report.json`。捕获期间若世界继续变化，不同区段可能来自不同 Tick，因此输出属于滚动快照。兼容性与完整性优先于导出速度；后端切换和 GPU 纹理回读发生时，客户端短暂卡顿属于预期行为。

## Blender 导入

在 Blender 中选择“文件 → 导入 → glTF 2.0”打开 `.gltf`，或选择“文件 → 导入 → Wavefront (.obj)”打开 `.obj`。请保持 `.gltf`、`.bin`、`.obj`、`.mtl` 与 `textures/` 的相对目录结构不变。

Minecraft 的标准渲染路径会被直接捕获。Flywheel 1.x 等受支持后端会在对象捕获期间临时进入 CPU 备用渲染作用域，并在 `finally` 路径恢复原状态。纯着色器生成且没有 CPU 备用几何的对象仍无法逆向恢复，只能生成半透明洋红包围盒并记录稳定诊断码。阴影、火焰、名称文本、Minecraft 光照和 AO 不会烘焙进导出结果。

## 验证

```powershell
./gradlew.bat clean test build
Set-Location tools
npm install
npm run validate -- ..\run\mcgltf-exports\smoke\smoke.gltf
```

真实模组验收建议使用 Create 6.0.10、Flywheel 1.0.6 与 Touhou Little Maid 1.5.3：

1. 备份并停用 `mcgltf-0.1.0.jar`，安装 `mcgltf-0.2.0.jar`。
2. 选择含 Create 齿轮箱、带支架传动件、空/满储液罐及一只女仆的区域并导出。
3. 确认齿轮箱和储液罐没有错误占位符，轴保持当前姿态，满罐液位正确。
4. 确认女仆皮肤不使用棋盘格降级纹理。
5. 分别将 glTF 与 OBJ 导入 Blender 5.2，对比原点、变换、材质、UV 与面拓扑。
6. 使用 Khronos Validator 验证 glTF 零错误。
7. 导出结束后继续游玩至少一分钟，确认 Flywheel 视觉仍然正常。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
