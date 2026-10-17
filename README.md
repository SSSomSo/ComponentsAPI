# ComponentsAPI

让**方块**与**物品**使用符合原版体系的**自定义数据组件**与**客户端渲染能力**，并通过配套客户端模组完成模型渲染等客户端工作。

- 作者：**Mc_SomSo**
- 许可证：**CC BY-SA 4.0**
- 服务端：Paper 26.2（Java 25，**已适配 build 134 的新序列化体系**，同时兼容 build 12x），插件 `ComponentsAPI-1.11.0.jar`（**强依赖 SSCustomItems**，缺失时不会启动）
- 客户端：Fabric（Minecraft 26.2，Fabric API 0.161.0+26.2），模组 `ComponentsAPI-mod-1.11.0.jar`（1.10.1 修复启动崩溃；1.10.2 修复 Sodium GUI 偏色；1.11.0 新增跑步动作/随机动画/强制播放禁止攻击）

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
  "enchantment_glint": false,
  "ignored_bones": ["cape", "tail"],
  "first_person_offset": { "enabled": true, "x": 0.56, "y": -0.52, "z": 0.72 },
  "switch": { "in": "equip_in", "out": "equip_out" },
  "keys": {
    "lc": [ { "clicks": 1, "animation": "swing" } ],
    "rc": [ { "clicks": 1, "animation": "block" } ],
    "mc": [],
    "h":  [ { "animation": "inspect" } ],
    "fp": [ { "toggle_first_person": true, "block_animations": true,
              "to_vanilla_animation": "fp_hide", "to_bedrock_animation": "fp_show" } ]
  },
  "animations": {
    "idle":    { "interruptible": true },
    "inspect": { "interruptible": false, "force": true }
  }
}
```

- `texture`：3D 模型贴图（可省略，用默认 `textures/geo_models/<名字>.png`）；`slot_texture`：物品栏图标（可省略，省略则显示原版物品图标）。
- `enchantment_glint`：是否允许基岩物品显示附魔光效，**默认 `false`（关闭以节省资源）**；开启后只保留原版 `item_model` 路径（掉落物、展示框）的光效，基岩模型自身叠加的光效层暂未实现。
- `ignored_bones`：按**骨骼组名称**列出的组默认不渲染（含其全部子层级 cubes）；基岩模型同名组在单文件内唯一，直接填组名即可。
- `first_person_offset`：第一人称默认向右/下/前偏移，模拟原版手持，可整体关闭。
- `switch.in/out`：切入/切出动画（覆盖原版切换动画）。
- `keys`：`lc` 左键 / `rc` 右键 / `mc` 中键 / `fp` 第一人称切换（默认 **R 键**）；**此外支持键盘上几乎所有按键**——字母 `a-z`、数字 `0-9`、功能键 `f1-f25`、方向键、空格/回车/退格、符号键、小键盘、左右修饰键等，完整键名清单见示例 `index/showcase.json.txt` 与源码 `KeyNames.java`。
  - **点击 vs 按住**：动作里写了 `clicks` 即“点击序列”（多段循环，最后一段播完再点回到第一段）；没写 `clicks` 的键盘键视为“按住（hold）”——按下播放动画、松开回默认（`h` 检视即典型按住键）。鼠标键 `lc/rc/mc` 始终是点击。
  - `fp` 的动作用 `toggle_first_person:true` 标记：按下后切换第一人称是否**模拟原版手持**（开启后第一人称交回原版渲染，模型/贴图仍在）；
  - `block_animations`（默认 `true`）：开启原版模拟时**压制其他按键动画**（此时按 lc/rc/mc/h 不会出现对应动画，再按一次 fp 关闭后恢复）；设为 `false` 则不冲突。
  - `to_vanilla_animation` / `to_bedrock_animation`：切换时播放的**关键帧过渡动画**——切入模拟（基岩→原版）时先播 `to_vanilla_animation`（基岩模型退场，动画结束后才交原版手持渲染）；切回（原版→基岩）时先恢复基岩渲染并播 `to_bedrock_animation`（进场）。不写则回退 `switch.out` / `switch.in`，都没有则瞬间切换。过渡期间强制，普通按键（lc/rc/mc/h 及再次按 fp）都不能打断，只有切换物品、丢弃、死亡可打断。
- 动画名可写短名（`idle`）或全名（`animation.example.idle`），客户端会自动模糊匹配。
- **强制播放**：在 `animations.<名>` 写 `"force": true`（兼容 `force_play`），该动画播放过程中普通按键（lc/rc/mc/h）无法打断；循环动画在停播前一直不可打断，非循环动画**播放结束后**才可打断。切换物品、丢弃物品、死亡仍可打断（它们直接重置手持模型）。
- **未配置任何动作时，基岩物品不播放任何原版挥动/拉弓/格挡**（第一人称整段被替换）。

### 动画音效（走原版 sounds 体系）

动画 `sound_effects` 的 `effect` 写原版音效事件 id，可直接写原版事件（如 `minecraft:entity.experience_orb.pickup`），
也可自定义命名空间事件（如 `capi:draw`）。自定义事件的音频位置与原版完全一致：

```
assets/<命名空间>/sounds.json     # 声明音效事件 → ogg 文件映射（同原版 sounds.json）
assets/<命名空间>/sounds/<x>.ogg  # 音频文件
```

因此既能在动画中自动播放，也能手动用 `/playsound <命名空间>:<事件> player @a` 播放。
示例资源包注册了 `capi:draw`、`capi:swing`（默认复用原版音频文件，可替换为自己的 ogg）。
声音支持 `volume`、`pitch`（固定值或 `[min,max]` 随机区间）。

### 手臂定位组

模型里定义唯一定位组后，玩家的手会跟随定位块移动（不改变手部纹理）：

- `lefthand_pos`：左手，尺寸 x4 × y12 × z4
- `righthand_pos`：右手

**第三人称手持跟随**：其他玩家/生物手持基岩物品时，模型会跟随手臂摆动（走路摆臂、挥动、拉弓/格挡持物姿势、举到眼前等），并通过手部锚点 FK 让定位组（`righthand_pos`/`lefthand_pos`）在动画中带动手臂。第三人称的旋转补偿与缩放目前为经验值（集中在 `BedrockItemRenderer`），若朝向/位置不对可在此微调。

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

### 示例包与占位文件

内置资源包除了可直接加载的 `example`（geo/animation/index + 真实贴图）外，还附带一组**全功能占位示例** `showcase.*`，
覆盖盒子 UV、每面 UV、手臂定位组、位移/旋转/缩放关键帧、声音与粒子关键帧、全字段 index、全键盘绑定、方块模型等。
这些文件以 `.json.txt` / `.png.txt` / `.ogg.txt` 结尾（因扩展名不是 `.json/.png/.ogg`，加载器不会解析、也不会报错），
内部可自由写 `//` 注释，供你参考；**实际使用时去掉 `.txt` 后缀并删除注释**。

### 加密资源包（可选，`.dat`）

用于把自定义资源包内容加密后分发：解密后的资源**只在内存中**存在，运行时不向磁盘（包括临时目录）写出，
加密后的 `.dat` 必须**放进模组 jar**（`mod/src/main/resources/capi_packs/`）才会加载。

- 加密为自定义容器：**AES-256-GCM 认证加密**（不是 ZIP 自带加密，无法用压缩软件打开）；
  密钥材料为 **64 字节（512 位）** 随机数据，以 base64 存在 `config/capi/config.toml` 的 `resource_pack_key`，
  经每包随机 salt + SHA-512 派生出 32 字节 AES 密钥；GCM 同时保证机密性与完整性（密钥错误 / 被篡改即解密失败）。
- 工作流：
  1. 启动一次客户端，自动生成 `config/capi/config.toml`（含 512 位密钥）；
  2. 把待加密资源包 `.zip` 放入 `config/capi/rp/`；
  3. 再次启动，模组自动把它们加密成 `config/capi/rp/dat/<同名>.dat`；
  4. 把 `.dat` 放入 `mod/src/main/resources/capi_packs/`，重新构建模组 jar 即可。
- 密钥请妥善保管，丢失后已发布的 `.dat` 无法解密；同一套资源包请用同一把密钥。

> 说明：内置推送资源包 `capi-pack.zip`（第 5 节）与加密 `.dat` 是两条并行通道——前者由插件服务端推送、内容不加密；
> 后者用于随模组 jar 分发、需要加密保护的内容。

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

产物：`plugin/target/ComponentsAPI-1.11.0.jar`。

资源包的权威源是 `mod/src/main/resources/assets/`；改完资源跑 `sync-pack.ps1`，会同步到
`resourcepack/` 并重新生成 `plugin/pack/capi-pack.zip`（zip 根目录必须直接是 `pack.mcmeta`，
条目使用正斜杠），随后重新构建插件即可内置。

### 模组（需要 JDK 25、Gradle 9.7）

```
cd mod
gradlew build
```

产物：`mod/build/libs/ComponentsAPI-mod-1.11.0.jar`（official 命名在 libs）。

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
