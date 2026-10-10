package com.componentsapi.light;

import com.componentsapi.component.ComponentRegistry;
import com.componentsapi.component.ComponentRegistry.LightSpec;
import com.componentsapi.nms.NmsBridge;
import org.bukkit.block.Block;
import java.util.Map;

public final class LightManager {

    private final NmsBridge nms;

    public LightManager(NmsBridge nms) { this.nms = nms; }

    public void apply(Block block, Map<String, String> components) {
        String snbt = components.get(ComponentRegistry.BLOCK_LIGHT);
        if (snbt == null) return;
        LightSpec spec = ComponentRegistry.parseBlockLight(snbt);
        nms.setBlockLight(block.getWorld(), block.getX(), block.getY(), block.getZ(),
                spec.light(), spec.eatEnvironmentLight());
    }
}
