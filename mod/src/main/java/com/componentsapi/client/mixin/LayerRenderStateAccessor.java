package com.componentsapi.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 LayerRenderState 的私有 quads 字段，
 * 用于自定义附魔颜色传递（以 quads 列表的 identityHashCode 为 key）。
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {

    @Accessor("quads")
    List<BakedQuad> capi$getQuads();
}
