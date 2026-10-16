package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.BedrockItemRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 玩家手持层兜底：处理"把物品举到眼前"路径（望远镜等使用中物品，
 * PlayerItemInHandLayer.renderItemHeldToEye 内的独立 submit）。
 * 常规手持走 super.submitArmWithItem，已由 ItemInHandLayerMixin 重定向覆盖。
 */
@Mixin(PlayerItemInHandLayer.class)
public class PlayerItemInHandLayerMixin {

    @Redirect(
            method = "renderItemHeldToEye",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 0)
    private void capi$redirectHeldToEyeSubmit(
            ItemStackRenderState rs, PoseStack ps, SubmitNodeCollector collector,
            int light, int overlay, int outline,
            AvatarRenderState state, HumanoidArm arm,
            PoseStack matrices, SubmitNodeCollector collectorOuter, int lightOuter) {
        ItemStack stack = arm == HumanoidArm.RIGHT
                ? state.rightHandItemStack
                : state.leftHandItemStack;
        String model = BedrockItemRenderer.modelOf(stack);
        if (model != null) {
            BedrockItemRenderer.renderThirdPerson(model, ps, collector, light,
                    arm == HumanoidArm.RIGHT, state.ageInTicks);
        } else {
            rs.submit(ps, collector, light, overlay, outline);
        }
    }
}
