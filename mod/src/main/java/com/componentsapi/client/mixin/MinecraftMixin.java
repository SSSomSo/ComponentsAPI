package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.AnimationState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 强制播放动画时禁止攻击/使用物品等服务端动作。
 *
 * <p>当 {@link AnimationState#isForcePlaying()} 为 true（当前动画标记为 force_play
 * 且尚未结束）时，拦截左键攻击、持续攻击、右键使用物品，防止玩家在播放强制
 * 动画期间触发服务端动作（如攻击、使用物品、放置方块等）。
 *
 * <p>切换物品、丢弃物品、死亡等操作不受此限制（这些是动画的合法中断条件）。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    /** 拦截单次左键攻击（点击瞬间）。 */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void capi$blockAttack(CallbackInfoReturnable<Boolean> ci) {
        if (AnimationState.get().isForcePlaying()) {
            ci.setReturnValue(false);
        }
    }

    /** 拦截持续左键攻击（按住左键连续攻击）。 */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void capi$blockContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (AnimationState.get().isForcePlaying()) {
            ci.cancel();
        }
    }

    /** 拦截右键使用物品（含放置方块、吃食物、喝药水等）。 */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void capi$blockUseItem(CallbackInfo ci) {
        if (AnimationState.get().isForcePlaying()) {
            ci.cancel();
        }
    }
}
