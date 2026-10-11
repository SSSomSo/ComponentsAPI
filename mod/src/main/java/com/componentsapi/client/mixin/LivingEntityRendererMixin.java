package com.componentsapi.client.mixin;

import com.componentsapi.client.CapiRenderState;
import com.componentsapi.client.ClientState;
import com.componentsapi.client.bedrock.BedrockRenderer;
import com.componentsapi.client.bedrock.BedrockSoundTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 生物模型覆盖：
 * 1) extractRenderState 时把该实体的 capi:entity_model 写入渲染状态；
 * 2) submit 时若存在 bedrock/ 模型，改走 ComponentsAPI 基岩渲染器并取消原版提交。
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void capi$stashModel(LivingEntity entity, LivingEntityRenderState state,
                                 float partialTicks, CallbackInfo ci) {
        String model = ClientState.get().entityModel(entity.getId());
        CapiRenderState capi = (CapiRenderState) state;
        capi.capi$setModel(model);

        // 统一填充主/副手物品渲染状态（方块物品在 THIRD_PERSON 上下文自动渲染为 3D 方块）。
        ItemStackRenderState mainState = capi.capi$getMain();
        ItemStackRenderState offState = capi.capi$getOff();
        mainState.clear();
        offState.clear();
        ItemStack mainItem = entity.getMainHandItem();
        ItemStack offItem = entity.getOffhandItem();
        if (!mainItem.isEmpty()) {
            Minecraft.getInstance().getItemModelResolver().updateForLiving(
                    mainState, mainItem, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        }
        if (!offItem.isEmpty()) {
            Minecraft.getInstance().getItemModelResolver().updateForLiving(
                    offState, offItem, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
        }

        BedrockSoundTracker.update(entity, model,
                state.walkAnimationSpeed > 1.0e-4f, state.ageInTicks / 20f);
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void capi$submitBedrock(LivingEntityRenderState state, PoseStack poseStack,
                                    SubmitNodeCollector collector, CameraRenderState cameraState,
                                    CallbackInfo ci) {
        String modelId = ((CapiRenderState) state).capi$getModel();
        if (modelId == null) {
            return;
        }
        if (modelId.startsWith("bedrock/")) {
            BedrockRenderer.submit(
                    modelId.substring("bedrock/".length()),
                    state, poseStack, collector);
            ci.cancel();
        }
    }
}
