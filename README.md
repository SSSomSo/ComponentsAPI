# ComponentsAPI

让**方块**与**生物**使用符合原版体系的**自定义数据组件**，并通过配套客户端模组完成模型渲染等客户端能力。

- 作者：**Mc_SomSo**
- 许可证：**CC BY-SA 4.0**
- 服务端：Paper 26.2（Java 25），插件 `ComponentsAPI-1.2.0.jar`（**强依赖 SSCustomItems**，缺失时不会启动）
- 客户端：Fabric（Minecraft 26.2，Fabric API 0.161.0+26.2），模组 `ComponentsAPI-mod-1.2.0.jar`

---

## 1. 内置组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `capi:block_model` | 字符串（模型标识） | 覆盖该方块的客户端模型，值为 `capi:block/<name>`，模型文件为资源包内 `assets/capi/models/block/<name>.json` |
| `capi:entity_model` | 字符串（模型标识） | 覆盖该生物的客户端模型。值为 `bedrock/<name>` 时使用**基岩版几何 + 动画** |
| `capi:block_light` | 复合标签 | `{eat_environment_light:false,light:1}`，`light` 为 0–15 的发光等级 |
| `capi:slipperiness` | 浮点 | 预留：表面滑行系数，原样存储与同步 |

未识别的组件会**原样存储并联网同步**，供其他插件 / 模组扩展。

---

## 2. 方块组件

### 带方块实体的方块

组件存储在方块实体 NBT 的 `capi` 复合标签中，可以直接用原版指令修改：

```mcfunction
data merge block 10 70 10 {capi:{"capi:block_model":"'capi:block/ruby'"}}
data merge block 10 70 10 {capi:{"capi:block_light":{eat_environment_light:0b,light:8}}}
```

### 纯方块

纯方块没有 NBT，ComponentsAPI 维护兜底索引 `plugins/ComponentsAPI/blocks_fallback.json`：

```mcfunction
/capi block 10 70 10 set capi:block_model "capi:block/ruby"
/capi block ~ ~ ~ set capi:block_light {eat_environment_light:false,light:10}
/capi block ~ ~ ~ remove capi:block_model
/capi block ~ ~ ~ get
```

---

## 3. 生物组件

组件存储在实体的 PDC（`PublicBukkitValues`）中：

```mcfunction
/capi entity @e[type=zombie,limit=1] set capi:entity_model "bedrock/example"
/capi entity @e[type=zombie,limit=1] remove capi:entity_model
```

---

## 4. 基岩版实体模型与动画

ComponentsAPI 客户端可以直接解析**基岩版（Bedrock）几何与动画 JSON** 并在 Java 版中渲染。

### 资源约定

```
assets/capi/bedrock/<name>.geo.json
assets/capi/bedrock/<name>.animation.json
assets/capi/textures/bedrock/<name>.png
```

### 特性

- 手持物品：骨骼标记 `"capi:hand":"right"/"left"`
- 第三人称显示：通过 PlayerRenderer 继承链
- 方块显示：支持 BlockDisplay/ItemDisplay
- 双 UV：盒子 UV + 每面 UV（含 uv_size/rotation）
- 声音关键帧：动画顶层 `sound_effects`，3D 定位播放
- 动画选择：移动播含 walk，静止播含 idle

---

## 5. 资源包与自动加载

- 资源包内置在插件 jar 中，启动时自动解压、计算 SHA-1，由内置 HTTP 服务（默认端口 8732）托管
- 玩家加入后自动推送；不支持运行时卸载
- 配置见 `plugins/ComponentsAPI/config.yml`

---

## 6. 构建

### 插件（JDK 25 + Maven）
```
mvn -f plugin/pom.xml clean package
```

### 模组（JDK 25 + Gradle 9.7）
```
cd mod
gradlew build
```

---

## 7. 已知限制

1. 纯方块组件只能用 `/capi block` 管理
2. 基岩动画不支持 Molang、catmull-rom
3. 内置资源包不支持运行时卸载
4. ComponentsAPI 插件必须与 SSCustomItems 同时安装
5. 更新后请完整重启，不建议 PlugManX
