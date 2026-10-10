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

public final class BedrockModelManager {

    private static final BedrockModelManager INSTANCE = new BedrockModelManager();
    private final Map<String, BedrockModel> models = new HashMap<>();
    private final Map<String, Map<String, BedrockAnimation>> animations = new HashMap<>();

    public static BedrockModelManager get() { return INSTANCE; }

    public void load(ResourceManager resourceManager) {
        models.clear();
        animations.clear();
        Map<Identifier, List<Resource>> files =
                resourceManager.listResourceStacks("bedrock", id -> "capi".equals(id.getNamespace()));
        for (Map.Entry<Identifier, List<Resource>> entry : files.entrySet()) {
            String path = entry.getKey().getPath();
            Resource resource = entry.getValue().get(0);
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                if (path.endsWith(".geo.json")) {
                    String key = baseName(path, ".geo.json");
                    models.put(key, BedrockModel.parse(GsonHelper.parse(reader)));
                } else if (path.endsWith(".animation.json")) {
                    String key = baseName(path, ".animation.json");
                    animations.put(key, BedrockAnimation.parseAll(JsonParser.parseReader(reader).getAsJsonObject()));
                }
            } catch (Exception e) {
                com.componentsapi.client.ComponentsApiClient.LOGGER.warn("解析基岩资源失败 " + path + ": " + e.getMessage());
            }
        }
    }

    private static String baseName(String path, String suffix) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.substring(0, name.length() - suffix.length());
    }

    public BedrockModel model(String key) { return models.get(key); }

    public BedrockAnimation pickAnimation(String key, boolean moving) {
        Map<String, BedrockAnimation> set = animations.get(key);
        if (set == null) return null;
        BedrockAnimation fallback = null;
        for (BedrockAnimation animation : set.values()) {
            String name = animation.name.toLowerCase();
            if (moving && name.contains("walk")) return animation;
            if (!moving && name.contains("idle")) return animation;
            fallback = animation;
        }
        return fallback;
    }

    private BedrockModelManager() {}
}
