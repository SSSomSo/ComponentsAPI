package com.componentsapi.client.mixin;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer.Submit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.QuadInstance;

import it.unimi.dsi.fastutil.ints.IntList;

/**
 * 附魔光效（glint）偏色修复 + 自定义颜色。
 *
 * 偏色修复：Sodium 改写渲染管线后，QuadInstance 的颜色可能未正确初始化为白色，
 * 导致附魔光效偏色（绿/红）。在 prepareFoilSubmit 开头显式设置为白色。
 *
 * 自定义颜色：若 Submit.tintLayers[0] 为负数（ItemModelResolverMixin 写入的标记），
 * 则还原为自定义 ARGB 颜色并使用。标记格式：-color-1，还原：-(v+1)。
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

    @Shadow
    private QuadInstance quadInstance;

    @Inject(method = "prepareFoilSubmit", at = @At("HEAD"), require = 0)
    private void capi$fixGlintColor(Submit submit, CallbackInfo ci) {
        if (quadInstance == null) {
            return;
        }
        int color = 0xFFFFFFFF; // 默认白色不透明

        int[] tints = submit.tintLayers();
        if (tints != null && tints.length > 0) {
            int v = tints[0];
            if (v < 0) {
                // 自定义附魔颜色标记：还原
                color = -(v + 1);
                // 移除标记：数组前移
                System.arraycopy(tints, 1, tints, 0, tints.length - 1);
                tints[tints.length - 1] = -1;
            }
        }

        quadInstance.setColor(color);
    }
}
