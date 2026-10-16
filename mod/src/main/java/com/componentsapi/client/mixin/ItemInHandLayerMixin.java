package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.BedrockItemRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 第三人称手持基岩物品渲染。
 *
 * 关键：不能在 submitArmWithItem 的 HEAD 注入（那时还没做 translateToHand，
 * PoseStack 停在实体原点，模型不会跟随手臂）。
 *
 * 这里重定向方法末尾的 ItemStackRenderState.submit —— 此时 PoseStack 已包含
 * translateToHand（手部定位）+ X-90/Y180 物品朝向 + 攻击挥动(attackTime) +
 * 持物姿势(ArmPose.animateUseItem)，基岩模型在该坐标系渲染即可自然跟随手臂摆动。
 *
 * 玩家层 PlayerItemInHandLayer 常规手持走 super.submitArmWithItem，也会进入本类；
 * 仅"举到眼前"（望远镜等）走独立路径，由 PlayerItemInHandLayerMixin 兜底。
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {

    @Redirect(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 0)
    private void capi$redirectHeldItemSubmit(
            // 被重定向调用：ItemStackRenderState.submit(PoseStack, SubmitNodeCollector, int, int, int)
            ItemStackRenderState rs, PoseStack ps, SubmitNodeCollector collector,
            int light, int overlay, int outline,
            // 外层 submitArmWithItem 的全部形参
            ArmedEntityRenderState state, ItemStackRenderState rsOuter, ItemStack stack,
            HumanoidArm arm, PoseStack matrices, SubmitNodeCollector collectorOuter,
            int lightOuter) {
        String model = BedrockItemRenderer.modelOf(stack);
        if (model != null) {
            // 替换原版物品渲染为基岩模型（PoseStack 已在手部位姿）
            BedrockItemRenderer.renderThirdPerson(model, ps, collector, light,
                    arm == HumanoidArm.RIGHT, state.ageInTicks);
        } else {
            rs.submit(ps, collector, light, overlay, outline);
        }
    }
}
