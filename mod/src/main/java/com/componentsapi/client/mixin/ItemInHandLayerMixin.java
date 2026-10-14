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
 * 第三人称手持物品：普通生物层。
 * 若手持物品携带 capi_bedrock_model，则改用基岩模型渲染，取消原版物品。
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void capi$renderBedrockHeld(ArmedEntityRenderState state,
                                        ItemStackRenderState itemState,
                                        ItemStack stack,
                                        HumanoidArm arm,
                                        PoseStack matrices,
                                        SubmitNodeCollector collector,
                                        int light,
                                        CallbackInfo ci) {
        String model = BedrockItemRenderer.modelOf(stack);
        if (model != null) {
            boolean mainHand = state.mainArm == arm;
            BedrockItemRenderer.renderThirdPerson(model, matrices, collector, light, mainHand);
            ci.cancel();
        }
    }
}
