package com.componentsapi.client.bedrock;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基岩模型/动画注册表。每次资源重载时重建。
 *
 * 同名文件规则：一个 modelKey（namespace:name）对应：
 *   assets/<namespace>/geo_models/<name>.geo.json
 *   assets/<namespace>/animations/<name>.animation.json
 *   assets/<namespace>/index/<name>.json
 *   assets/<namespace>/textures/geo_models/<name>.png（或 textures/bedrock/<name>.png）
 *
 * 同时兼容旧的 bedrock/ 目录。
 */
public final class BedrockModelManager {

    private static final BedrockModelManager INSTANCE = new BedrockModelManager();

    private final Map<String, BedrockModel> models = new HashMap<>();
    private final Map<String, Map<String, BedrockAnimation>> animations = new HashMap<>();

    public static BedrockModelManager get() {
        return INSTANCE;
    }

    public void load(ResourceManager resourceManager) {
        models.clear();
        animations.clear();

        // 新规范 geo_models/ + 旧 bedrock/ 兼容
        Map<Identifier, List<Resource>> geoFiles =
                resourceManager.listResourceStacks("geo_models", id -> true);
        geoFiles.putAll(resourceManager.listResourceStacks("bedrock", id -> true));
        // 动画
        Map<Identifier, List<Resource>> animFiles =
                resourceManager.listResourceStacks("animations", id -> true);

        for (Map.Entry<Identifier, List<Resource>> entry : geoFiles.entrySet()) {
            Identifier resId = entry.getKey();
            String path = resId.getPath();
            if (!path.endsWith(".geo.json")) continue;
            String ns = resId.getNamespace();
            String key = ns + ":" + baseName(path, ".geo.json");
            try (InputStreamReader reader = new InputStreamReader(
                    entry.getValue().get(0).open(), StandardCharsets.UTF_8)) {
                models.put(key, BedrockModel.parse(GsonHelper.parse(reader)));
            } catch (Exception e) {
                com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                        "解析基岩模型失败 " + key + ": " + e.getMessage());
            }
        }

        for (Map.Entry<Identifier, List<Resource>> entry : animFiles.entrySet()) {
            Identifier resId = entry.getKey();
            String path = resId.getPath();
            if (!path.endsWith(".animation.json")) continue;
            String ns = resId.getNamespace();
            // 同名规则：animations/<name>.animation.json → key = namespace:name
            String key = ns + ":" + baseName(path, ".animation.json");
            try (InputStreamReader reader = new InputStreamReader(
                    entry.getValue().get(0).open(), StandardCharsets.UTF_8)) {
                Map<String, BedrockAnimation> parsed = BedrockAnimation.parseAll(
                        JsonParser.parseReader(reader).getAsJsonObject());
                animations.put(key, parsed);
            } catch (Exception e) {
                com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                        "解析基岩动画失败 " + key + ": " + e.getMessage());
            }
        }
        // 旧 bedrock/ 目录下的动画也加载
        for (Map.Entry<Identifier, List<Resource>> entry :
                resourceManager.listResourceStacks("bedrock", id -> true).entrySet()) {
            Identifier resId = entry.getKey();
            String path = resId.getPath();
            if (!path.endsWith(".animation.json")) continue;
            String ns = resId.getNamespace();
            String key = ns + ":" + baseName(path, ".animation.json");
            if (animations.containsKey(key)) continue;
            try (InputStreamReader reader = new InputStreamReader(
                    entry.getValue().get(0).open(), StandardCharsets.UTF_8)) {
                animations.put(key, BedrockAnimation.parseAll(
                        JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception ignored) {
            }
        }
    }

    /** 按 modelKey（namespace:name）查找动画集合。 */
    public Map<String, BedrockAnimation> animationsForModel(String modelKey) {
        return animations.get(modelKey);
    }

    private static String baseName(String path, String suffix) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.substring(0, name.length() - suffix.length());
    }

    public BedrockModel model(String key) {
        return models.get(key);
    }

    /** 获取模型贴图（优先 geo_models/，回退 bedrock/）。 */
    public static Identifier texture(String modelKey) {
        int colon = modelKey.indexOf(':');
        String ns = colon > 0 ? modelKey.substring(0, colon) : "capi";
        String name = colon > 0 ? modelKey.substring(colon + 1) : modelKey;
        return Identifier.fromNamespaceAndPath(ns, "textures/geo_models/" + name + ".png");
    }

    /** 选择动画：优先 index default_animation；否则 walk/idle。 */
    public BedrockAnimation pickAnimation(String key, boolean moving) {
        Map<String, BedrockAnimation> set = animations.get(key);
        if (set == null) {
            return null;
        }
        String defaultAnim = AnimationIndex.get().defaultAnimation(key);
        if (defaultAnim != null) {
            BedrockAnimation anim = set.get(defaultAnim);
            if (anim != null) {
                return anim;
            }
        }
        BedrockAnimation fallback = null;
        for (BedrockAnimation animation : set.values()) {
            String n = animation.name.toLowerCase();
            if (moving && n.contains("walk")) {
                return animation;
            }
            if (!moving && n.contains("idle")) {
                return animation;
            }
            fallback = animation;
        }
        return fallback;
    }

    private BedrockModelManager() {}
}
