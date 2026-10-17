package com.componentsapi.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基岩模型动作索引配置。
 *
 * 资源约定：assets/<namespace>/index/<name>.json
 * 同名文件规则：一个 <name> 对应三个文件
 *   geo_models/<name>.geo.json
 *   animations/<name>.animation.json
 *   index/<name>.json
 *
 * 格式：
 * {
 *   "default_animation": "idle",
 *   "texture": "capi:textures/geo_models/example.png",
 *   "slot_texture": "capi:textures/item/example.png",
 *   "first_person_offset": {"enabled": true, "x": 0.5, "y": -0.5, "z": 1.0},
 *   "switch": {"in": "equip_in", "out": "equip_out"},
 *   "keys": {
 *     "lc": [{"clicks": 1, "animation": "swing"}, {"clicks": 2, "animation": "double_swing"}],
 *     "rc": [{"clicks": 1, "animation": "block"}],
 *     "mc": [],
 *     "h":  [{"animation": "inspect"}],
 *     "fp":  [{"toggle_first_person": true, "block_animations": true,
 *              "to_vanilla_animation": "fp_hide", "to_bedrock_animation": "fp_show"}]
 *   },
 *   "animations": {
 *     "idle": {"interruptible": true}
 *   }
 * }
 *
 * texture=模型 3D 贴图；slot_texture=物品栏 2D 图标（放 textures/item/ 下）
 * 按键：lc=左键, rc=右键, mc=中键, h=持续按键(inspect/检视), fp=第一人称模拟切换(默认 R 键)
 * fp 切换：to_vanilla_animation 为基岩→原版退场关键帧，to_bedrock_animation 为原版→基岩进场关键帧；
 *          未写则回退 switch.out / switch.in；过渡期间强制，不可被普通按键打断
 * clicks 多段：按点击次数依次播放，最后一段完成后再点回到第一段
 */
public final class AnimationIndex {

    private static final AnimationIndex INSTANCE = new AnimationIndex();

    private final Map<String, ModelConfig> configs = new HashMap<>();

    public static AnimationIndex get() {
        return INSTANCE;
    }

    public void load(ResourceManager resourceManager) {
        configs.clear();
        Map<Identifier, List<Resource>> files =
                resourceManager.listResourceStacks("index", id -> true);
        for (Map.Entry<Identifier, List<Resource>> entry : files.entrySet()) {
            Identifier resId = entry.getKey();
            String path = resId.getPath();
            if (!path.endsWith(".json")) {
                continue;
            }
            String modelKey = resId.getNamespace() + ":"
                    + path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            try (InputStreamReader reader = new InputStreamReader(
                    entry.getValue().get(0).open(), StandardCharsets.UTF_8)) {
                configs.put(modelKey, parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                        "解析动画索引失败 " + resId + ": " + e.getMessage());
            }
        }
    }

    private ModelConfig parse(JsonObject root) {
        ModelConfig config = new ModelConfig();
        // 默认动画：支持字符串或数组（随机）
        config.defaultAnimations = parseAnimList(root, "default_animation");
        // 模型 3D 贴图（可含命名空间）
        if (root.has("texture") && !root.get("texture").isJsonNull()) {
            config.modelTexture = root.get("texture").getAsString();
        }
        // 物品栏 2D 图标贴图（可含命名空间）
        if (root.has("slot_texture") && !root.get("slot_texture").isJsonNull()) {
            config.slotTexture = root.get("slot_texture").getAsString();
        }
        // 附魔光效开关：默认 false（关闭以节省资源）；index 显式 enchantment_glint:true 才开启
        if (root.has("enchantment_glint") && !root.get("enchantment_glint").isJsonNull()) {
            config.enchantmentGlint = root.get("enchantment_glint").getAsBoolean();
        }
        // 第一人称偏移
        if (root.has("first_person_offset")) {
            JsonObject off = root.getAsJsonObject("first_person_offset");
            config.fpEnabled = !off.has("enabled") || off.get("enabled").getAsBoolean();
            if (off.has("x")) config.fpX = off.get("x").getAsFloat();
            if (off.has("y")) config.fpY = off.get("y").getAsFloat();
            if (off.has("z")) config.fpZ = off.get("z").getAsFloat();
        }
        // 切换动画：支持字符串或数组（随机）；"none" 表示不播
        if (root.has("switch")) {
            JsonObject sw = root.getAsJsonObject("switch");
            config.switchIn = parseAnimList(sw, "in");
            config.switchOut = parseAnimList(sw, "out");
        }
        // 跑步动作：sprint.start / sprint.sprinting / sprint.end
        if (root.has("sprint")) {
            JsonObject sp = root.getAsJsonObject("sprint");
            config.sprintStart = parseAnimList(sp, "start");
            config.sprinting = parseAnimList(sp, "sprinting");
            config.sprintEnd = parseAnimList(sp, "end");
        }
        // 按键绑定：keys 下允许任意键名（鼠标 lc/rc/mc、fp 别名及 KeyNames 支持的全部键盘键）
        if (root.has("keys")) {
            JsonObject keys = root.getAsJsonObject("keys");
            for (String key : keys.keySet()) {
                JsonElement arrEl = keys.get(key);
                if (!arrEl.isJsonArray()) {
                    continue;
                }
                List<KeyAction> actions = new ArrayList<>();
                for (JsonElement el : arrEl.getAsJsonArray()) {
                    JsonObject ao = el.getAsJsonObject();
                    KeyAction action = new KeyAction();
                    if (ao.has("clicks")) {
                        action.clicks = ao.get("clicks").getAsInt();
                        action.hasClicks = true;
                    }
                    if (ao.has("animation")) action.animation = ao.get("animation").getAsString();
                    // 切换第一人称模拟原版手持（toggle）
                    if (ao.has("toggle_first_person")) {
                        action.toggleFirstPerson = ao.get("toggle_first_person").getAsBoolean();
                    }
                    // 开启原版模拟时是否压制其他按键动画（默认 true，冲突）
                    if (ao.has("block_animations")) {
                        action.blockAnimations = ao.get("block_animations").getAsBoolean();
                    }
                    // 切入模拟原版手持（基岩→原版）的过渡关键帧动画
                    if (ao.has("to_vanilla_animation") && !ao.get("to_vanilla_animation").isJsonNull()) {
                        action.toVanillaAnimation = ao.get("to_vanilla_animation").getAsString();
                    }
                    // 切回基岩（原版→基岩）的过渡关键帧动画
                    if (ao.has("to_bedrock_animation") && !ao.get("to_bedrock_animation").isJsonNull()) {
                        action.toBedrockAnimation = ao.get("to_bedrock_animation").getAsString();
                    }
                    actions.add(action);
                }
                config.keyActions.put(key, actions);
            }
        }
        // 默认忽略不渲染的骨骼组（按名称，含其子层级）
        if (root.has("ignored_bones") && root.get("ignored_bones").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("ignored_bones")) {
                config.ignoredBones.add(el.getAsString());
            }
        }
        // 动画可打断性 / 强制播放
        if (root.has("animations")) {
            JsonObject anims = root.getAsJsonObject("animations");
            for (String name : anims.keySet()) {
                JsonObject animObj = anims.getAsJsonObject(name);
                boolean interruptible = true;
                if (animObj.has("interruptible")) {
                    interruptible = animObj.get("interruptible").getAsBoolean();
                }
                config.interruptible.put(name, interruptible);
                boolean force = false;
                if (animObj.has("force") || animObj.has("force_play")) {
                    JsonObject o2 = animObj;
                    force = (o2.has("force") ? o2.get("force") : o2.get("force_play")).getAsBoolean();
                }
                config.forcePlay.put(name, force);
            }
        }
        return config;
    }

    public ModelConfig get(String modelKey) {
        return configs.get(modelKey);
    }

    public String defaultAnimation(String modelKey) {
        ModelConfig c = configs.get(modelKey);
        return c != null ? pickRandom(c.defaultAnimations) : null;
    }

    /**
     * 从动画选项列表中随机选一个。
     * 列表为 null/空返回 null；选项为 "none"（不区分大小写）返回 null（表示不播动画）。
     */
    public static String pickRandom(List<String> options) {
        if (options == null || options.isEmpty()) return null;
        String picked = options.get((int) (Math.random() * options.size()));
        if (picked == null || "none".equalsIgnoreCase(picked)) return null;
        return picked;
    }

    /**
     * 解析 JSON 中的动画字段：支持字符串（单动画）或数组（随机列表）。
     * 字段不存在/null 返回 null；数组为空返回 null。
     */
    private static List<String> parseAnimList(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        List<String> list = new ArrayList<>();
        JsonElement el = obj.get(key);
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                if (!e.isJsonNull()) list.add(e.getAsString());
            }
        } else {
            list.add(el.getAsString());
        }
        return list.isEmpty() ? null : list;
    }

    public boolean isInterruptible(String modelKey, String animName) {
        ModelConfig c = configs.get(modelKey);
        return c == null || c.interruptible.getOrDefault(animName, true);
    }

    /** 该骨骼是否被配置为忽略不渲染。 */
    public boolean isIgnoredBone(String modelKey, String boneName) {
        ModelConfig c = configs.get(modelKey);
        return c != null && c.ignoredBones.contains(boneName);
    }

    /** 该动画是否强制播放（普通操作不可打断）。 */
    public boolean isForcePlay(String modelKey, String animName) {
        ModelConfig c = configs.get(modelKey);
        return c != null && c.forcePlay.getOrDefault(animName, false);
    }

    /** 单个按键绑定动作。 */
    public static final class KeyAction {
        public int clicks = 1;
        /** 配置里是否显式写了 clicks：未写 clicks 的动作视为“按住（hold）”触发。 */
        public boolean hasClicks = false;
        public String animation;
        /** 为 true 时按下该键切换第一人称是否模拟原版手持（不播动画）。 */
        public boolean toggleFirstPerson = false;
        /** 开启原版模拟时是否压制其他按键动画（默认 true）。 */
        public boolean blockAnimations = true;
        /**
         * 切入模拟原版手持（基岩→原版）时的过渡关键帧动画名。
         * 非 null 时先播放该动画（基岩模型退场），结束后才交原版渲染。
         * null=回退到顶层 switch.out；仍无则瞬间切换。
         */
        public String toVanillaAnimation;
        /**
         * 切回基岩（原版→基岩）时的过渡关键帧动画名。
         * 非 null 时先恢复基岩渲染并播放该动画（进场），结束后回到默认。
         * null=回退到顶层 switch.in；仍无则瞬间切换。
         */
        public String toBedrockAnimation;
    }

    /** 模型完整配置。
     *
     * <p>所有动画字段均为 {@code List<String>}：单个字符串配置会被转成单元素列表；
     * 数组配置表示"随机选一个"。列表中出现 {@code "none"}（不区分大小写）表示
     * 该次不播动画（用原版动作/默认姿态）。
     */
    public static final class ModelConfig {
        /** 默认动画（待机），支持随机列表。 */
        public List<String> defaultAnimations;
        /** 模型 3D 贴图，形如 capi:textures/geo_models/example.png；null=用默认路径。 */
        public String modelTexture;
        /** 物品栏 2D 图标贴图，形如 capi:textures/item/example.png；null=不替换。 */
        public String slotTexture;
        /** 是否允许附魔光效（默认 false 关闭以节省资源）；index 显式 enchantment_glint:true 才开启。 */
        public boolean enchantmentGlint = false;
        public boolean fpEnabled = true;
        public float fpX = 0.56f;   // 右（模拟原版第一人称手持偏移）
        public float fpY = -0.52f; // 下
        public float fpZ = 0.72f;  // 前
        /** 切入动画（物品入手时），支持随机列表；null/none=不播切入，直接默认姿态。 */
        public List<String> switchIn;
        /** 切出动画（物品离手时），支持随机列表；null/none=不播切出。 */
        public List<String> switchOut;
        /** 开始疾跑瞬间播放（非循环），支持随机列表；null=不播。 */
        public List<String> sprintStart;
        /** 疾跑持续期间循环播放，支持随机列表（进入疾跑时随机选定一次）；null=不播。 */
        public List<String> sprinting;
        /** 结束疾跑瞬间播放（非循环），结束后回默认，支持随机列表；null=不播。 */
        public List<String> sprintEnd;
        public final Map<String, List<KeyAction>> keyActions = new HashMap<>();

        /**
         * 判断某键是否为“按住”模式：鼠标键 lc/rc/mc 始终是点击；
         * 键盘键当该键所有动作都没有显式 clicks 时视为按住（按下播动画、松开回默认）。
         */
        public boolean isHoldKey(String key) {
            if (KeyNames.MOUSE_KEYS.contains(key)) {
                return false;
            }
            List<KeyAction> actions = keyActions.get(key);
            if (actions == null || actions.isEmpty()) {
                return false;
            }
            for (KeyAction a : actions) {
                if (a.hasClicks) {
                    return false;
                }
            }
            return true;
        }

        public final Map<String, Boolean> interruptible = new HashMap<>();
        /** 默认忽略不渲染的骨骼组名称（含其子层级）。 */
        public final List<String> ignoredBones = new ArrayList<>();
        /** 标记为强制播放的动画名。 */
        public final Map<String, Boolean> forcePlay = new HashMap<>();
    }

    private AnimationIndex() {}
}
