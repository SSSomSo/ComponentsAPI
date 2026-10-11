package com.componentsapi.entity;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生物组件存储：实体 PDC（PublicBukkitValues）。
 * 组件键为 capi:xxx 命名空间键，值为 SNBT 字符串。
 */
public final class EntityComponents {

    public Map<String, String> getComponents(Entity entity) {
        Map<String, String> result = new LinkedHashMap<>();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if ("capi".equals(key.getNamespace())) {
                String value = pdc.get(key, PersistentDataType.STRING);
                if (value != null) {
                    result.put(key.asString(), value);
                }
            }
        }
        return result;
    }

    public void setComponent(Entity entity, String componentId, String snbt) {
        NamespacedKey key = NamespacedKey.fromString(componentId);
        if (key == null) {
            throw new IllegalArgumentException("无效组件 id: " + componentId);
        }
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, snbt);
    }

    public boolean removeComponent(Entity entity, String componentId) {
        NamespacedKey key = NamespacedKey.fromString(componentId);
        if (key == null) {
            return false;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(key)) {
            return false;
        }
        pdc.remove(key);
        return true;
    }
}
