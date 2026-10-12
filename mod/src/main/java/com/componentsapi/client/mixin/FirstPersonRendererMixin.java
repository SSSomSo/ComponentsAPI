package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.AnimationState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手臂动画：当 AnimationState 中有正在播放的动画时，
 * 在渲染手持物品手臂前应用基岩动画的骨骼变换。
 *
 * 26.2 新渲染管线：ItemInHandRenderer.submitArmWithItem（私有方法）。
 * 动画仅影响本地玩家的第一人称视角，不影响第三人称。
 * 骨骼名约定：rightArm / righthand_pos（右手），leftArm / lefthand_pos（左手），
 * 兼容 TaCZ/YSM 的 rightArm/leftArm/armRight/armLeft 等命名。
 */
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public abstract class FirstPersonRendererMixin {

    private static final float UNIT = 1f / 16f;

    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD"))
    private void capi$applyArmAnimation(AbstractClientPlayer player, float partialTick,
                                        float pitch, InteractionHand hand, float swingProgress,
                                        ItemStack stack, float equipProgress, PoseStack matrices,
                                        SubmitNodeCollector collector, int light,
                                        CallbackInfo ci) {
        if (!AnimationState.get().isPlaying()) {
            return;
        }
        // 尝试多种骨骼命名（兼容原版/TaCZ/YSM/基岩定位组）
        String[] boneNames = hand == InteractionHand.MAIN_HAND
                ? new String[]{"rightArm", "righthand_pos", "RightArm", "right_arm", "armRight", "rightHand"}
                : new String[]{"leftArm", "lefthand_pos", "LeftArm", "left_arm", "armLeft", "leftHand"};

        for (String boneName : boneNames) {
            AnimationState.BoneTransform transform =
                    AnimationState.get().sample(boneName, partialTick);
            if (transform != null) {
                // 应用动画变换（像素 → 方块单位，角度直接用）
                matrices.translate(
                        transform.posX * UNIT,
                        transform.posY * UNIT,
                        transform.posZ * UNIT);
                matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(transform.rotX));
                matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(transform.rotY));
                matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(transform.rotZ));
                matrices.scale(transform.scaleX, transform.scaleY, transform.scaleZ);
                break;
            }
        }
    }
}
