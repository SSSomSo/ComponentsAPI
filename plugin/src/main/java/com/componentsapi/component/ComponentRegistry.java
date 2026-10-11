package com.componentsapi.component;

import java.util.Map;

/**
 * ComponentsAPI 内置组件定义。组件值一律以 SNBT 存储；本类负责内置组件的解析与说明。
 * 未知组件原样存储并同步，供其他插件/模组扩展。
 */
public final class ComponentRegistry {

    public static final String BLOCK_MODEL = "capi:block_model";
    public static final String ENTITY_MODEL = "capi:entity_model";
    public static final String BLOCK_LIGHT = "capi:block_light";
    public static final String SLIPPERINESS = "capi:slipperiness";

    /** 内置组件 → 用法说明（用于补全提示）。 */
    public static final Map<String, String> DOCS = Map.of(
            BLOCK_MODEL, "方块客户端模型，值为资源位置字符串，如 \"capi:ruby_block\"",
            ENTITY_MODEL, "生物客户端模型，值为资源位置字符串，如 \"capi:zombie_king\"",
            BLOCK_LIGHT, "方块光照 {eat_environment_light:false,light:1}，light 为 0-15",
            SLIPPERINESS, "表面滑行系数（0.0-1.0），如 0.98 近似冰面"
    );

    public record LightSpec(boolean eatEnvironmentLight, int light) {}

    /** 极简解析 capi:block_light 的 SNBT。 */
    public static LightSpec parseBlockLight(String snbt) {
        String body = snbt.trim();
        body = body.substring(body.indexOf('{') + 1, body.lastIndexOf('}'));
        boolean eat = false;
        int light = 0;
        for (String part : body.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            if (key.equals("eat_environment_light")) {
                eat = value.equals("1b") || value.equals("true");
            } else if (key.equals("light")) {
                light = Integer.parseInt(value.replaceAll("[^0-9-]", ""));
            }
        }
        light = Math.max(0, Math.min(15, light));
        return new LightSpec(eat, light);
    }

    /** 去除 SNBT 字符串两端引号得到资源位置。 */
    public static String unwrapString(String snbt) {
        String s = snbt.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private ComponentRegistry() {}
}
