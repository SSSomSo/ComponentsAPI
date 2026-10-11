package com.componentsapi.light;

import com.componentsapi.component.ComponentRegistry;
import com.componentsapi.component.ComponentRegistry.LightSpec;
import com.componentsapi.nms.NmsBridge;
import org.bukkit.block.Block;

import java.util.Map;

/**
 * 把 capi:block_light 组件应用到 StarLight：
 * 扫描到组件时写入对应 nibble；组件移除/变 0 时清除。
 */
public final class LightManager {

    private final NmsBridge nms;

    public LightManager(NmsBridge nms) {
        this.nms = nms;
    }

    /** 根据方块当前组件同步其光照状态。 */
    public void apply(Block block, Map<String, String> components) {
        String snbt = components.get(ComponentRegistry.BLOCK_LIGHT);
        if (snbt == null) {
            return;
        }
        LightSpec spec = ComponentRegistry.parseBlockLight(snbt);
        nms.setBlockLight(block.getWorld(), block.getX(), block.getY(), block.getZ(),
                spec.light(), spec.eatEnvironmentLight());
    }
}
