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
 * 基岩模型/动画注册表。每次资源重载时重建（含服务端资源包到达后的重载）。
 *
 * 资源约定（资源包内）：
 *   assets/capi/bedrock/&lt;name&gt;.geo.json
 *   assets/capi/bedrock/&lt;name&gt;.animation.json
 *   assets/capi/textures/bedrock/&lt;name&gt;.png
 */
public final class BedrockModelManager {

    private static final BedrockModelManager INSTANCE = new BedrockModelManager();

    private final Map<String, BedrockModel> models = new HashMap<>();
    private final Map<String, Map<String, BedrockAnimation>> animations = new HashMap<>();
    /** 完整文件路径（namespace:path）→ 动画名 → 动画。用于第一人称动画按文件查找。 */
    private final Map<String, Map<String, BedrockAnimation>> animationsByFile = new HashMap<>();

    public static BedrockModelManager get() {
        return INSTANCE;
    }

    public void load(ResourceManager resourceManager) {
        models.clear();
        animations.clear();
        animationsByFile.clear();

        // 扫描所有命名空间的 bedrock/ 和 animations/ 目录
        Map<Identifier, List<Resource>> files =
                resourceManager.listResourceStacks("bedrock", id -> true);
        files.putAll(resourceManager.listResourceStacks("animations", id -> true));

        for (Map.Entry<Identifier, List<Resource>> entry : files.entrySet()) {
            Identifier resId = entry.getKey();
            String path = resId.getPath();
            String ns = resId.getNamespace();
            Resource resource = entry.getValue().get(0);
            try (InputStreamReader reader = new InputStreamReader(
                    resource.open(), StandardCharsets.UTF_8)) {
                if (path.endsWith(".geo.json")) {
                    String key = ns + ":" + baseName(path, ".geo.json");
                    models.put(key, BedrockModel.parse(GsonHelper.parse(reader)));
                } else if (path.endsWith(".animation.json")) {
                    String key = ns + ":" + baseName(path, ".animation.json");
                    Map<String, BedrockAnimation> parsed = BedrockAnimation.parseAll(
                            JsonParser.parseReader(reader).getAsJsonObject());
                    animations.put(key, parsed);
                    // 同时按完整文件路径存储（namespace:animations/xxx.animation.json）
                    String fileKey = ns + ":" + path;
                    animationsByFile.put(fileKey, parsed);
                }
            } catch (Exception e) {
                com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                        "解析基岩资源失败 " + ns + ":" + path + ": " + e.getMessage());
            }
        }
    }

    /** 按完整文件路径查找动画集合（如 capi:animations/test.animation.json）。 */
    public Map<String, BedrockAnimation> animationsForFile(String namespace, String path) {
        return animationsByFile.get(namespace + ":" + path);
    }

    private static String baseName(String path, String suffix) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.substring(0, name.length() - suffix.length());
    }

    public BedrockModel model(String key) {
        return models.get(key);
    }

    /** 选择动画：优先 index 配置的 default_animation；否则移动时选 walk，静止时选 idle。 */
    public BedrockAnimation pickAnimation(String key, boolean moving) {
        Map<String, BedrockAnimation> set = animations.get(key);
        if (set == null) {
            return null;
        }
        // index 配置的默认动画优先
        String defaultAnim = AnimationIndex.get().defaultAnimation(key);
        if (defaultAnim != null) {
            BedrockAnimation anim = set.get(defaultAnim);
            if (anim != null) {
                return anim;
            }
        }
        BedrockAnimation fallback = null;
        for (BedrockAnimation animation : set.values()) {
            String name = animation.name.toLowerCase();
            if (moving && name.contains("walk")) {
                return animation;
            }
            if (!moving && name.contains("idle")) {
                return animation;
            }
            fallback = animation;
        }
        return fallback;
    }

    private BedrockModelManager() {}
}
