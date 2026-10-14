# ComponentsAPI

让**方块**与**物品**使用符合原版体系的**自定义数据组件**与**客户端渲染能力**，并通过配套客户端模组完成模型渲染等客户端工作。

- 作者：**Mc_SomSo**
- 许可证：**CC BY-SA 4.0**
- 服务端：Paper 26.2（Java 25），插件 `ComponentsAPI-1.5.0.jar`（**强依赖 SSCustomItems**，缺失时不会启动）
- 客户端：Fabric（Minecraft 26.2，Fabric API 0.161.0+26.2），模组 `ComponentsAPI-mod-1.5.0.jar`

> **职责边界**：ComponentsAPI 只提供**通用能力**（数据组件 + 渲染管线）；具体的自定义物品/方块/伤害类型等内容全部定义在 **SSCustomItems**（SSCI）中。

---

## 1. 内置组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `capi:block_model` | 字符串（模型标识） | 覆盖该方块的客户端模型，值为 `capi:block/<name>`，模型文件为资源包内 `assets/capi/models/block/<name>.json` |
| `capi:entity_model` | 字符串（模型标识） | 生物组件通道（保留存储/同步）；**基岩实体模型渲染已停用**，基岩模型现仅用于物品（见第 4 节） |
| `capi:block_light` | 复合标签 | `{eat_environment_light:false,light:1}`，`light` 为 0–15 的发光等级；`eat_environment_light:true` 时同时清除该位置的天空光（环境光） |
| `capi:slipperiness` | 浮点 | 预留：表面滑行系数，原样存储与同步 |

物品的基岩模型**不通过组件**绑定，而是在物品的 **custom_data** 中写 `capi_bedrock_model`：
```json
"minecraft:custom_data": { "capi_bedrock_model": "capi:example" }
```

未识别的组件会**原样存储并联网同步**，供其他插件 / 模组扩展。

---

## 2. 方块组件

### 带方块实体的方块（箱子、熔炉、告示牌、潜影盒等）

组件存储在方块实体 NBT 的 `capi` 复合标签中，可以直接用原版指令修改：

```mcfunction
data merge block 10 70 10 {capi:{"capi:block_model":'"capi:block/ruby"'}}

data merge block 10 70 10 {capi:{"capi:block_light":{eat_environment_light:0b,light:8}}}
data get block 10 70 10 capi
```

### 纯方块（石头、木板等没有方块实体的方块）

纯方块没有 NBT，ComponentsAPI 在数据目录下维护兜底索引 `plugins/ComponentsAPI/blocks_fallback.json`，
因此**所有方块**都可以通过管理指令写入组件：

```mcfunction
/capi block 10 70 10 set capi:block_model "capi:block/ruby"
/capi block ~ ~ ~ set capi:block_light {eat_environment_light:false,light:10}
/capi block ~ ~ ~ remove capi:block_model
/capi block ~ ~ ~ get
```

> 注意：纯方块的组件**不能**用 `/data merge block` 修改（原版无 NBT 可写），只能用 `/capi block`。

### 关于光照

Paper 26.2 使用 StarLight 光照引擎，`capi:block_light` 通过直接写入区块光照 nibble 实现：

- `light`：该方块位置发出的方块光等级（0–15）；
- `eat_environment_light`：为 `true` 时把该位置的天空光（阳光/环境光）清零，
  适合“吞噬环境光”的方块；为 `false` 时只叠加发光，不影响环境光；
- 移除组件或把 `light` 设为 0 即取消发光。

---

## 3. 生物组件

组件存储在实体的 PDC（`PublicBukkitValues`）中，使用管理指令设置：

```mcfunction
/capi entity @e[type=zombie,limit=1] set capi:some_component "value"
/capi entity @e[type=zombie,limit=1] remove capi:some_component
```

高级用户也可以直接用原版数据指令写入 PDC：

```mcfunction
data merge entity @e[limit=1] {PublicBukkitValues:{"capi:some_component":'"value"'}}
```

> 注：`capi:entity_model` 仍可作为通用组件存储/同步，但客户端**不再渲染基岩实体模型**（基岩模型已专用于物品）。

---

## 4. 基岩版物品模型与动画（核心能力）

ComponentsAPI 客户端可以直接解析**基岩版（Bedrock）几何与动画 JSON**，并把它们渲染成**物品**——
第一人称手持、第三人称手持、其他玩家/生物手持、物品栏图标均支持。

### 绑定方式

物品通过 custom_data 绑定：`"minecraft:custom_data": { "capi_bedrock_model": "<命名空间>:<名字>" }`。

### 同名三件套（资源包 `assets/<命名空间>/` 下）

一个 `<命名空间>:<名字>` 对应三个同名文件：

```
geo_models/<名字>.geo.json          # 基岩 geometry（format_version 1.16.0，bones/cubes）
animations/<名字>.animation.json    # 基岩 animation（含 sound_effects / particle_effects 关键帧）
index/<名字>.json                   # 动作/贴图/按键/偏移配置（ComponentsAPI 专用）
textures/geo_models/<名字>.png      # 模型贴图（默认路径）
textures/item/<名字>.png            # 物品栏 2D 图标（16×16，可选）
```

### index 配置字段

```json
{
  "default_animation": "idle",
  "texture": "capi:textures/geo_models/example.png",
  "slot_texture": "capi:textures/item/example.png",
  "first_person_offset": { "enabled": true, "x": 0.56, "y": -0.52, "z": 0.72 },
  "switch": { "in": "equip_in", "out": "equip_out" },
  "keys": {
    "lc": [ { "clicks": 1, "animation": "swing" } ],
    "rc": [ { "clicks": 1, "animation": "block" } ],
    "mc": [],
    "h":  [ { "animation": "inspect" } ]
  },
  "animations": {
    "idle":    { "interruptible": true },
    "inspect": { "interruptible": false }
  }
}
```

- `texture`：3D 模型贴图（可省略，用默认 `textures/geo_models/<名字>.png`）；`slot_texture`：物品栏图标（可省略，省略则显示原版物品图标）。
- `first_person_offset`：第一人称默认向右/下/前偏移，模拟原版手持，可整体关闭。
- `switch.in/out`：切入/切出动画（覆盖原版切换动画）。
- `keys`：`lc` 左键 / `rc` 右键 / `mc` 中键 / `h` 持续按住（检视）；`clicks` 支持多段循环，最后一段播完再点回到第一段。
- 动画名可写短名（`idle`）或全名（`animation.example.idle`），客户端会自动模糊匹配。
- **未配置任何动作时，基岩物品不播放任何原版挥动/拉弓/格挡**（第一人称整段被替换）。

### 手臂定位组

模型里定义唯一定位组后，玩家的手会跟随定位块移动（不改变手部纹理）：

- `lefthand_pos`：左手，尺寸 x4 × y12 × z4
- `righthand_pos`：右手

### 命令播放动画（服务端发起）

```
/playbedrockanimation <玩家> <命名空间:名字> <动画名>
```
别名 `/pba`。一个 index/geo 只对应一个同名 `.animation.json`，因此命令里只传动画名。

### 支持范围与限制

- 支持：骨骼层级、盒子（盒子 UV 与**每面 UV per-face UV**、每面 UV 旋转 0/90/180/270）、`inflate`、`mirror`、
  骨骼/盒子的 `pivot` 与 `rotation`、position/rotation/scale 关键帧、循环、
  **声音关键帧**（`sound_effects`，3D 定位音，pitch 随机）、
  **粒子关键帧**（`particle_effects`，支持 `effect` + `locator` 骨骼定位）。
- **不支持**：Molang 表达式、`catmull-rom` 插值、带参数粒子（dust 等需额外数据的粒子类型）。
- 第三人称手持的旋转/缩放、第一人称偏移目前为经验值，若实测位置或朝向不对可在
  `BedrockItemRenderer` 中调整。
- 已**取消**方块/生物的基岩实体模型（方块仍支持第 5 节的原版方块模型）。

---

## 5. 资源包与自动加载

- 资源包已**内置在插件 jar** 中（`resourcepack/capi-pack.zip`），插件启动时自动解压、
  计算 SHA-1，并由内置 HTTP 服务（默认端口 **8732**）托管；
- 玩家加入服务器后**自动推送**资源包；
- 资源包一旦加载**不支持运行时卸载**（Minecraft 资源包机制限制），
  更新资源包需更换 jar 并完整重启；
- 配置见 `plugins/ComponentsAPI/config.yml`：

```yaml
sync-interval-ticks: 20
resource-pack:
  enabled: true
  port: 8732
  host: auto            # 服务器在 NAT/内网外时请填写玩家可访问的地址
  require-download: false   # true 时拒绝/下载失败资源包会被踢出；false 仅提示
```

### 添加自己的方块模型

按**原版方块模型 JSON 格式**放入资源包：

```
assets/capi/models/block/<name>.json
assets/capi/textures/block/<name>.png
```

客户端在资源加载时自动扫描 `assets/capi/models/block/` 并参与官方烘焙，
`capi:block_model` 值填 `capi:block/<name>` 即可引用。

---

## 6. 网络同步

插件通过插件消息通道 `componentsapi:sync` 周期性（默认每 20 刻）向客户端推送：

- 当前维度内带组件方块的位置、模型与光照（含 `blocks_fallback.json` 中的纯方块）；
- 带组件生物的网络 id 与模型。

报文为二进制快照，玩家加入 / 维度切换后自动重新获取。

---

## 7. 构建

根目录提供两个一键脚本：

```
.\build-all.ps1            # 同时构建插件 + 模组
.\build-all.ps1 -Plugin    # 只构建插件
.\build-all.ps1 -Mod       # 只构建模组
.\sync-pack.ps1            # 改了 mod/assets 资源后：同步资源并重打 plugin/pack/capi-pack.zip
```

### 插件（需要 JDK 25、Maven）

```
mvn -f plugin/pom.xml clean package
```

产物：`plugin/target/ComponentsAPI-1.5.0.jar`。

资源包的权威源是 `mod/src/main/resources/assets/`；改完资源跑 `sync-pack.ps1`，会同步到
`resourcepack/` 并重新生成 `plugin/pack/capi-pack.zip`（zip 根目录必须直接是 `pack.mcmeta`，
条目使用正斜杠），随后重新构建插件即可内置。

### 模组（需要 JDK 25、Gradle 9.7）

```
cd mod
gradlew build
```

产物：`mod/build/libs/ComponentsAPI-mod-1.5.0.jar`（official 命名在 libs）。

Minecraft 26.2 起 Mojang 发行 jar 本身即为官方命名（版本 JSON 中没有 client_mappings），
工程在 `gradle.properties` 中设置 `fabric.loom.disableObfuscation=true`，
不使用 Yarn / Mojang mappings，也不创建 `modImplementation`，依赖统一用 `implementation`。
Gradle 发行版若下载缓慢，可将 `gradle/wrapper/gradle-wrapper.properties` 的地址改为国内镜像，
例如 `https://mirrors.cloud.tencent.com/gradle/gradle-9.7.0-bin.zip`。

---

## 8. 已知限制

1. 纯方块的组件只能用 `/capi block` 管理（兜底索引），不能用 `/data` 指令；
2. 基岩动画不支持 Molang、catmull-rom；物品基岩模型的第三人称/第一人称偏移为经验值，未在所有情况下实测；
3. 基岩模型只服务**物品**，方块/生物不再使用基岩实体模型；
4. 内置资源包不支持运行时卸载；
5. ComponentsAPI 插件必须与 SSCustomItems 同时安装；
6. 更新插件 / 模组 jar 后请**完整重启**服务端与客户端，不建议使用 PlugManX 等热加载工具。

---

## 9. 使用豆包进行许愿式编程

本项目（ComponentsAPI + SSCustomItems）的全部代码均通过**豆包（Doubao）AI 助手以"许愿式编程"方式开发**：开发者用自然语言描述需求（"许愿"），AI 负责实现、编译、调试、文档与发布。

### 什么是许愿式编程

不手写代码，而是用自然语言向 AI 描述：

- 想要什么功能（"让基岩版实体同时支持手持物品、第三人称显示、方块显示"）
- 具体规则（"每面 UV 支持 uv_size 与 rotation，声音关键帧用 3D 定位音"）
- 格式与约束（"组件键用 capi: 命名空间，符合原版体系，可用 /data merge block 修改"）

AI 负责：代码编写、编译验证、错误修复、文档撰写、Git 提交、GitHub 发布。

### 高效许愿的技巧

1. **一次只许一个愿**：每个需求聚焦一个功能点，避免 AI 上下文过载。
2. **给出精确格式**：涉及 JSON、SNBT、Mixin 注入点时直接写出示例或要求 AI 用 javap 核实签名。
3. **指定版本与环境**：明确 Paper 版本、JDK 版本、Fabric/Loom 版本，避免 AI 用过时 API。
4. **要求编译验证**：每次实现后要求 AI 实际运行 Gradle/Maven 构建，不要只看代码。
5. **迭代修正**：AI 第一次实现可能有偏差，用"改成 X""Y 不对，应该是 Z"逐步修正。
6. **保留交接文档**：长项目中 AI 会丢失上下文，要求 AI 维护 HANDOFF.md，记录已完成功能、已知坑、环境路径，新会话先读文档再继续。

### 许愿式编程的局限与缓解

| 问题 | 缓解方式 |
|------|---------|
| AI 幻觉不存在的 API / 字段 | 要求 AI 用 javap 核实 26.2 客户端类签名，不信口头承诺 |
| 上下文丢失导致重复劳动 | 维护 HANDOFF.md，每次会话开始先读取 |
| 编译通过但运行时崩溃 | 要求 AI 写明"仅编译通过、未运行时实测"，诚实标注 |
| AI 擅自扩展范围 | 明确"只改 X，不动 Y"，验收时核对 diff |
| Mixin 注入点偏移 | 要求 AI 先 javap 核实方法签名再写注入，失败时换 TAIL/HEAD/AROUND |

### 本项目的许愿记录

SSCustomItems 仓库 `docs/output.md` 整理了开发者历次原始许愿（需求提示词），按版本归档，可作为许愿式编程的完整案例参考。
