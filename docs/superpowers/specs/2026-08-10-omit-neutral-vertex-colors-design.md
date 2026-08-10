# MineToMesh 中性顶点色省略设计

## 背景

MineToMesh 当前为每个 glTF Primitive 无条件写入 `COLOR_0`。即使全部顶点色均为纯白不透明 `RGBA(255,255,255,255)`，Blender glTF 导入器仍会按照 glTF 语义生成 `Mix Vertex Color` 与 `Mix Vertex Alpha` 节点，使贴图 Alpha 经一次恒等乘法后再连接 Principled BSDF。

## 目标

对没有视觉贡献的中性顶点色省略 `COLOR_0`，使普通实体材质的贴图 Alpha 在 Blender 中直接连接 Principled BSDF Alpha，同时保留所有真实 RGB 染色与顶点透明度。

## 决策

采用逐 Primitive 判定：

- 所有顶点均为 `RGBA(255,255,255,255)` 时，不向 glTF 写入颜色 bufferView、accessor 或 `attributes.COLOR_0`。
- 任意顶点的 R、G、B 或 A 不等于 255 时，完整写入现有归一化 `VEC4 COLOR_0`。
- 判定使用捕获后的精确 8 位 `ColorRgba`，不引入阈值。
- OBJ 输出保持不变；OBJ/MTL 当前不消费顶点色。

## 数据流

`StreamingGltfSession` 在写入 Primitive 时检查顶点颜色是否全部中性，并将颜色二进制段改为可选值。`GltfDocumentBuilder` 仅在该段存在时创建颜色 accessor 并添加 `COLOR_0` 属性。位置、法线、UV、索引、材质和拓扑不受影响。

## 正确性边界

- 普通宠物、动物和纯白渲染流省略 `COLOR_0`。
- 草、树叶、水、生物群系染色、彩色实体部件以及任何非 255 Alpha 保留 `COLOR_0`。
- 同一 Primitive 内只要存在一个非中性顶点，整组颜色都保留，避免拆分 Primitive 或改变顶点对应关系。
- 不尝试修改 Blender 导入器生成节点，也不添加 Blender 后处理脚本。

## 测试

1. 纯白 Primitive 的 glTF JSON 不含 `COLOR_0`，且 accessors/bufferViews 不包含无用颜色段。
2. 任一 RGB 通道非白时仍含归一化 `VEC4 COLOR_0`。
3. 任一 Alpha 非 255 时仍含归一化 `VEC4 COLOR_0`。
4. 现有 glTF、OBJ、输出事务和完整构建测试全部通过。

## 验收标准

将纯白顶点色实体导出的 glTF 导入 Blender 后，不再由 `COLOR_0` 触发 `Mix Vertex Alpha`；真实顶点染色材质仍保持原有外观与 glTF 语义。
