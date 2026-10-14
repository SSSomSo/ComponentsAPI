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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称手持物品：玩家层（按 F5 看到的自己/其他玩家）。
 * 玩家层覆盖了 submitArmWithItem，因此需要单独注入。
 */
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandLayerMixin {

    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void capi$renderBedrockHeldPlayer(AvatarRenderState state,
                                              ItemStackRenderState itemState,
                                              ItemStack stack,
                                              HumanoidArm arm,
                                              PoseStack matrices,
                                              SubmitNodeCollector collector,
                                              int light,
                                              CallbackInfo ci) {
        String model = BedrockItemRenderer.modelOf(stack);
        if (model != null) {
            // AvatarRenderState 不直接持有 mainArm，主副手由调用顺序决定，这里默认按手臂渲染即可
            boolean mainHand = arm == HumanoidArm.RIGHT;
            BedrockItemRenderer.renderThirdPerson(model, matrices, collector, light, mainHand);
            ci.cancel();
        }
    }
}
