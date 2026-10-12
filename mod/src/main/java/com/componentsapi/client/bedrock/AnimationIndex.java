package com.componentsapi.client.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基岩模型动作索引配置。
 *
 * 资源约定：assets/<namespace>/index/<model>.json
 * 格式：
 * {
 *   "default_animation": "idle",
 *   "animations": {
 *     "idle":  {"interruptible": true},
 *     "draw":  {"interruptible": false},
 *     "shoot": {"interruptible": true}
 *   }
 * }
 *
 * 用途：
 * - default_animation：模型默认播放的动作（覆盖按 walk/idle 命名的自动选择）
 * - interruptible：该动作是否可被新动作打断（默认 true）
 */
public final class AnimationIndex {

    private static final AnimationIndex INSTANCE = new AnimationIndex();

    /** modelKey (namespace:name) → 配置 */
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
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                ModelConfig config = new ModelConfig();
                if (root.has("default_animation")) {
                    config.defaultAnimation = root.get("default_animation").getAsString();
                }
                if (root.has("animations")) {
                    JsonObject anims = root.getAsJsonObject("animations");
                    for (String name : anims.keySet()) {
                        JsonObject animObj = anims.getAsJsonObject(name);
                        boolean interruptible = true;
                        if (animObj.has("interruptible")) {
                            interruptible = animObj.get("interruptible").getAsBoolean();
                        }
                        config.animations.put(name, interruptible);
                    }
                }
                configs.put(modelKey, config);
            } catch (Exception e) {
                com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                        "解析动画索引失败 " + resId + ": " + e.getMessage());
            }
        }
    }

    /** 获取模型的默认动画名，null=无配置（按 walk/idle 自动选择）。 */
    public String defaultAnimation(String modelKey) {
        ModelConfig config = configs.get(modelKey);
        return config != null ? config.defaultAnimation : null;
    }

    /** 检查指定动画是否可被打断（默认 true）。 */
    public boolean isInterruptible(String modelKey, String animationName) {
        ModelConfig config = configs.get(modelKey);
        if (config == null) {
            return true;
        }
        return config.animations.getOrDefault(animationName, true);
    }

    private static final class ModelConfig {
        String defaultAnimation;
        final Map<String, Boolean> animations = new HashMap<>();
    }

    private AnimationIndex() {}
}
