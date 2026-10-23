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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称手持基岩物品渲染。
 *
 * <p>原版 {@code submitArmWithItem} 的流程：
 * <pre>
 *   translateToHand(arm)                 // 坐标系定位到手腕（含手臂挥动）
 *   mulPose(XP.rotationDegrees(-90))     // 物品朝向：让物品平躺
 *   mulPose(YP.rotationDegrees(180))
 *   translate(...)                       // 物品微调
 *   ItemStackRenderState.submit(...)
 * </pre>
 *
 * <p>基岩模型本身是 Java Y 向下的实体体系（含手臂定位组），必须在
 * <b>{@code translateToHand} 之后、物品朝向旋转之前</b>渲染，否则会被
 * XP(-90)/YP(180) 翻转，朝向错误且无法用逆旋转干净抵消（中间夹着平移）。
 * 因此这里在 translateToHand 调用之后注入，渲染模型并取消原版后续物品渲染。
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER),
            cancellable = true,
            require = 0)
    private void capi$renderAfterTranslateToHand(
            ArmedEntityRenderState state, ItemStackRenderState rs, ItemStack stack,
            HumanoidArm arm, PoseStack matrices, SubmitNodeCollector collector,
            int light, CallbackInfo ci) {
        String model = BedrockItemRenderer.modelOf(stack);
        if (model != null) {
            // 此时 PoseStack 仅含 translateToHand（手部坐标系，含挥动），未做物品朝向旋转。
            BedrockItemRenderer.renderThirdPerson(model, matrices, collector, light,
                    arm == HumanoidArm.RIGHT, state.ageInTicks);
            ci.cancel();
        }
    }
}
