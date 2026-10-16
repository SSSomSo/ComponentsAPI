package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.AnimationState;
import com.componentsapi.client.bedrock.BedrockItemRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手持渲染：
 * 1) 若物品携带 capi_bedrock_model，则用基岩渲染器渲染整个模型（含偏移、动画、手持挂点），
 *    取消原版物品/手臂渲染；
 * 2) 否则若有动画在播放，应用手臂骨骼变换（兼容普通物品）。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonRendererMixin {

    private static final float UNIT = 1f / 16f;

    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD"),
            cancellable = true)
    private void capi$renderBedrockItem(AbstractClientPlayer player, float partialTick,
                                        float pitch, InteractionHand hand, float swingProgress,
                                        ItemStack stack, float equipProgress, PoseStack matrices,
                                        SubmitNodeCollector collector, int light,
                                        CallbackInfo ci) {
        // 检测基岩物品模型
        String modelKey = BedrockItemRenderer.modelOf(stack);
        if (modelKey != null) {
            // 更新动画状态（仅主手控制模型，副手单独渲染）
            if (hand == InteractionHand.MAIN_HAND) {
                AnimationState.get().setHandModel(modelKey);
                // 每帧驱动声音/粒子关键帧
                AnimationState.get().tickEffects();
                // 第一人称切换为模拟原版手持时：不 cancel，交由原版渲染
                if (AnimationState.get().isVanillaFpMode()) {
                    return;
                }
            }
            // 用基岩渲染器渲染，取消原版
            BedrockItemRenderer.renderFirstPerson(modelKey, matrices, collector, light);
            ci.cancel();
        }
    }
}
