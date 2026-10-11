package com.componentsapi.block;

import com.componentsapi.light.LightManager;
import com.componentsapi.nms.NmsBridge;
import org.bukkit.block.Block;

import java.util.Map;

/**
 * 方块组件访问（自动路由）：
 * - 方块实体：组件存于 NBT 的 capi 复合标签，原生 /data merge block 可直接修改；
 * - 非方块实体（纯方块）：组件存于插件 blocks_fallback.json（原版无存储位置）。
 */
public final class BlockComponents {

    private final NmsBridge nms;
    private final LightManager lights;
    private final FallbackBlockStore fallback;

    public BlockComponents(NmsBridge nms, LightManager lights, FallbackBlockStore fallback) {
        this.nms = nms;
        this.lights = lights;
        this.fallback = fallback;
    }

    public Map<String, String> getComponents(Block block) {
        if (nms.hasBlockEntity(block)) {
            return nms.readComponents(block);
        }
        return fallback.getComponents(block);
    }

    public void setComponent(Block block, String componentId, String snbt) {
        Map<String, String> components;
        if (nms.hasBlockEntity(block)) {
            components = nms.readComponents(block);
            components.put(componentId, snbt);
            nms.writeComponents(block, components);
        } else {
            fallback.setComponent(block, componentId, snbt);
            components = fallback.getComponents(block);
        }
        lights.apply(block, components);
    }

    public boolean removeComponent(Block block, String componentId) {
        if (nms.hasBlockEntity(block)) {
            Map<String, String> components = nms.readComponents(block);
            if (!components.containsKey(componentId)) {
                return false;
            }
            components.remove(componentId);
            nms.writeComponents(block, components);
            return true;
        }
        return fallback.removeComponent(block, componentId);
    }
}
