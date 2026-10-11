package com.componentsapi.block;

import com.componentsapi.ComponentsApiPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 非方块实体方块的组件兜底存储：
 * 纯方块没有 NBT/PDC，组件由插件维护在 blocks_fallback.json：
 * key = "世界UUID;x;y;z"，value = 组件 id → SNBT。
 */
public final class FallbackBlockStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<
            LinkedHashMap<String, LinkedHashMap<String, String>>>() {}.getType();

    private final ComponentsApiPlugin plugin;
    private final File file;
    private LinkedHashMap<String, LinkedHashMap<String, String>> data = new LinkedHashMap<>();

    public FallbackBlockStore(ComponentsApiPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blocks_fallback.json");
        load();
    }

    public void load() {
        try {
            if (file.exists()) {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                LinkedHashMap<String, LinkedHashMap<String, String>> parsed = GSON.fromJson(json, MAP_TYPE);
                if (parsed != null) {
                    data = parsed;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("读取 blocks_fallback.json 失败: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(file.toPath(), GSON.toJson(data, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("保存 blocks_fallback.json 失败: " + e.getMessage());
        }
    }

    public Map<String, String> getComponents(Block block) {
        return data.getOrDefault(key(block), new LinkedHashMap<>());
    }

    public void setComponent(Block block, String componentId, String snbt) {
        data.computeIfAbsent(key(block), k -> new LinkedHashMap<>()).put(componentId, snbt);
        save();
    }

    public boolean removeComponent(Block block, String componentId) {
        String key = key(block);
        Map<String, String> components = data.get(key);
        if (components == null || !components.containsKey(componentId)) {
            return false;
        }
        components.remove(componentId);
        if (components.isEmpty()) {
            data.remove(key);
        }
        save();
        return true;
    }

    /** 某世界的全部条目：key → 组件 Map（用于同步扫描）。 */
    public List<Map.Entry<String, Map<String, String>>> entries(World world) {
        String prefix = world.getUID() + ";";
        List<Map.Entry<String, Map<String, String>>> result = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String, String>> entry : data.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(),
                        (Map<String, String>) entry.getValue()));
            }
        }
        return result;
    }

    public static String key(Block block) {
        return block.getWorld().getUID() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    /** 从存储 key 解析坐标。 */
    public static int[] parseKey(String key) {
        String[] parts = key.split(";");
        return new int[]{
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
    }
}
