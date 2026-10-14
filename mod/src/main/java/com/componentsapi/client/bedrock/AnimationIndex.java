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
 *     "h":  [{"animation": "inspect"}]
 *   },
 *   "animations": {
 *     "idle": {"interruptible": true}
 *   }
 * }
 *
 * texture=模型 3D 贴图；slot_texture=物品栏 2D 图标（放 textures/item/ 下）
 * 按键：lc=左键, rc=右键, mc=中键, h=持续按键(inspect/检视)
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
        if (root.has("default_animation")) {
            config.defaultAnimation = root.get("default_animation").getAsString();
        }
        // 模型 3D 贴图（可含命名空间）
        if (root.has("texture") && !root.get("texture").isJsonNull()) {
            config.modelTexture = root.get("texture").getAsString();
        }
        // 物品栏 2D 图标贴图（可含命名空间）
        if (root.has("slot_texture") && !root.get("slot_texture").isJsonNull()) {
            config.slotTexture = root.get("slot_texture").getAsString();
        }
        // 第一人称偏移
        if (root.has("first_person_offset")) {
            JsonObject off = root.getAsJsonObject("first_person_offset");
            config.fpEnabled = !off.has("enabled") || off.get("enabled").getAsBoolean();
            if (off.has("x")) config.fpX = off.get("x").getAsFloat();
            if (off.has("y")) config.fpY = off.get("y").getAsFloat();
            if (off.has("z")) config.fpZ = off.get("z").getAsFloat();
        }
        // 切换动画
        if (root.has("switch")) {
            JsonObject sw = root.getAsJsonObject("switch");
            if (sw.has("in")) config.switchIn = sw.get("in").getAsString();
            if (sw.has("out")) config.switchOut = sw.get("out").getAsString();
        }
        // 按键绑定
        if (root.has("keys")) {
            JsonObject keys = root.getAsJsonObject("keys");
            for (String key : new String[]{"lc", "rc", "mc", "h"}) {
                if (keys.has(key)) {
                    List<KeyAction> actions = new ArrayList<>();
                    for (JsonElement el : keys.getAsJsonArray(key)) {
                        JsonObject ao = el.getAsJsonObject();
                        KeyAction action = new KeyAction();
                        if (ao.has("clicks")) action.clicks = ao.get("clicks").getAsInt();
                        action.animation = ao.get("animation").getAsString();
                        actions.add(action);
                    }
                    config.keyActions.put(key, actions);
                }
            }
        }
        // 动画可打断性
        if (root.has("animations")) {
            JsonObject anims = root.getAsJsonObject("animations");
            for (String name : anims.keySet()) {
                JsonObject animObj = anims.getAsJsonObject(name);
                boolean interruptible = true;
                if (animObj.has("interruptible")) {
                    interruptible = animObj.get("interruptible").getAsBoolean();
                }
                config.interruptible.put(name, interruptible);
            }
        }
        return config;
    }

    public ModelConfig get(String modelKey) {
        return configs.get(modelKey);
    }

    public String defaultAnimation(String modelKey) {
        ModelConfig c = configs.get(modelKey);
        return c != null ? c.defaultAnimation : null;
    }

    public boolean isInterruptible(String modelKey, String animName) {
        ModelConfig c = configs.get(modelKey);
        return c == null || c.interruptible.getOrDefault(animName, true);
    }

    /** 单个按键绑定动作。 */
    public static final class KeyAction {
        public int clicks = 1;
        public String animation;
    }

    /** 模型完整配置。 */
    public static final class ModelConfig {
        public String defaultAnimation;
        /** 模型 3D 贴图，形如 capi:textures/geo_models/example.png；null=用默认路径。 */
        public String modelTexture;
        /** 物品栏 2D 图标贴图，形如 capi:textures/item/example.png；null=不替换。 */
        public String slotTexture;
        public boolean fpEnabled = true;
        public float fpX = 0.56f;   // 右（模拟原版第一人称手持偏移）
        public float fpY = -0.52f; // 下
        public float fpZ = 0.72f;  // 前
        public String switchIn;
        public String switchOut;
        public final Map<String, List<KeyAction>> keyActions = new HashMap<>();
        public final Map<String, Boolean> interruptible = new HashMap<>();
    }

    private AnimationIndex() {}
}
