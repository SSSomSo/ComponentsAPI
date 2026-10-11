package com.componentsapi.client.mixin;

import com.componentsapi.client.CapiDisplayState;
import com.componentsapi.client.ClientState;
import com.componentsapi.client.bedrock.BedrockRenderer;
import com.componentsapi.client.bedrock.BedrockSoundTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 展示实体（方块展示 / 物品展示 / 文本展示）模型覆盖：
 * 1) extractRenderState 时按实体网络 id 查 capi:entity_model 并写入状态、更新声音关键帧；
 * 2) submitInner 时若为 bedrock/ 模型，改走基岩渲染器并取消原版提交。
 */
@Mixin(DisplayRenderer.class)
public class DisplayRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Display;Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;F)V",
            at = @At("TAIL"))
    private void capi$stashModel(Display entity, DisplayEntityRenderState state,
                                 float partialTicks, CallbackInfo ci) {
        String model = ClientState.get().entityModel(entity.getId());
        ((CapiDisplayState) state).capi$setModel(model);
        BedrockSoundTracker.update(entity, model, false,
                entity.tickCount / 20f);
    }

    @Inject(
            method = "submitInner(Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void capi$submitBedrock(DisplayEntityRenderState state,
                                    PoseStack poseStack,
                                    SubmitNodeCollector collector,
                                    int packedLight, float partialTicks,
                                    CallbackInfo ci) {
        String model = ((CapiDisplayState) state).capi$getModel();
        if (model != null && model.startsWith("bedrock/")) {
            BedrockRenderer.submitGeneric(
                    model.substring("bedrock/".length()),
                    state, state.entityYRot, poseStack, collector);
            ci.cancel();
        }
    }
}
