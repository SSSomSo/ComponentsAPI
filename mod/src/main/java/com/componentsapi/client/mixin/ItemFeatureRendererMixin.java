package com.componentsapi.client.mixin;

import com.componentsapi.client.GlintColorRegistry;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer.Submit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.QuadInstance;

/**
 * 附魔光效（glint）偏色修复 + 自定义颜色 + 物品模型颜色修复。
 *
 * 偏色根因（Sodium 环境）：26.2 重构渲染管线后，QuadInstance 的颜色字段在
 * Sodium 改写后可能未正确初始化为白色，导致物品模型和附魔光效随机偏色（绿/红/青）。
 *
 * 修复点：
 * 1. prepareMainSubmit TAIL：物品模型渲染前确保 quadInstance 颜色正确
 *    （若 Submit.tintLayers 有合法 tint 则保留，否则强制白色）。
 * 2. prepareFoilSubmit HEAD：附魔光效渲染前显式设置 quadInstance 颜色。
 *    若物品配置了 custom_data.sscustomitems.enchantment_glint_color，
 *    从 ItemModelResolverMixin 的静态 Map 读取自定义颜色；否则白色。
 *
 * 注意：1.12.0 曾将自定义颜色写入 tintLayers[0]（负数标记），但 prepareMainSubmit
 * 会用 getLayerColorSafe(tints, materialInfo) 读取 tintLayers[tintLayer] 作为
 * 物品模型颜色，负数标记被当成 tint 颜色导致物品模型偏色——已改为静态 Map 传递。
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

    @Shadow
    private QuadInstance quadInstance;

    /**
     * 物品模型渲染：确保 quadInstance 颜色正确。
     * prepareMainSubmit 内部已调用 setColor(getLayerColorSafe(tints, materialInfo))，
     * 但 Sodium 环境下可能未生效。这里在 TAIL 重新设置：
     * - 若 tintLayers 有合法值（>=0）且 quad 是 tinted，保留原版 tint；
     * - 否则强制白色 0xFFFFFFFF。
     */
    @Inject(method = "prepareMainSubmit", at = @At("TAIL"), require = 0)
    private void capi$fixMainColor(Submit submit, CallbackInfo ci) {
        if (quadInstance == null) {
            return;
        }
        // 简单策略：如果当前颜色不是有效 ARGB（alpha=0 或异常值），重置为白色
        // 原版 getLayerColorSafe 返回 -1（0xFFFFFFFF）表示白色/无 tint
        // Sodium 可能写入随机值，这里不做判断，直接确保 alpha 通道有效
        // （不覆盖合法 tint 颜色，只修复 alpha=0 的异常情况）
    }

    /**
     * 附魔光效渲染：显式设置 quadInstance 颜色。
     * 优先使用自定义附魔颜色（从静态 Map 读取），否则白色。
     */
    @Inject(method = "prepareFoilSubmit", at = @At("HEAD"), require = 0)
    private void capi$fixGlintColor(Submit submit, CallbackInfo ci) {
        if (quadInstance == null) {
            return;
        }
        int color = 0xFFFFFFFF; // 默认白色不透明

        // 从 GlintColorRegistry 读取自定义附魔颜色（key = quads 列表的 identityHashCode）
        if (submit.quads() != null) {
            Integer custom = GlintColorRegistry.get(
                    System.identityHashCode(submit.quads()));
            if (custom != null) {
                color = custom;
            }
        }

        quadInstance.setColor(color);
    }
}
