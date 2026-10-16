package com.componentsapi.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 ItemStackRenderState 的私有 layers 数组与有效层数，
 * 用于按 index 配置关闭附魔光效（foilType）。
 */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] capi$getLayers();

    @Accessor("activeLayerCount")
    int capi$getActiveLayerCount();
}
