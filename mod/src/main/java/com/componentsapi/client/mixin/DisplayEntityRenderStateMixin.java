package com.componentsapi.client.mixin;

import com.componentsapi.client.CapiDisplayState;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给展示实体渲染状态注入 capi 模型字段。 */
@Mixin(DisplayEntityRenderState.class)
public class DisplayEntityRenderStateMixin implements CapiDisplayState {

    @Unique
    private String capi$model;

    @Override
    public String capi$getModel() {
        return capi$model;
    }

    @Override
    public void capi$setModel(String model) {
        this.capi$model = model;
    }
}
