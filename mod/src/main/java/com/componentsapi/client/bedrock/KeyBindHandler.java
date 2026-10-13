package com.componentsapi.client.bedrock;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定动画触发器。
 *
 * lc=左键（attack），rc=右键（use），mc=中键（pick item），h=键盘 H 键（检视，可按住）。
 * 每帧检测按键的上升沿（点击）与按住状态，转发给 AnimationState。
 * 直接用 GLFW 检测 H 键，避免额外的 keybinding 模块依赖。
 */
public final class KeyBindHandler {

    private static boolean prevLc, prevRc, prevMc, prevH;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(KeyBindHandler::onTick);
    }

    private static boolean isHeld(Minecraft mc, int key) {
        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    private static void onTick(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        boolean lc = mc.options.keyAttack.isDown();
        boolean rc = mc.options.keyUse.isDown();
        boolean mcDown = mc.options.keyPickItem.isDown();
        boolean h = isHeld(mc, GLFW.GLFW_KEY_H);

        if (lc && !prevLc) {
            AnimationState.get().onClick("lc");
        }
        if (rc && !prevRc) {
            AnimationState.get().onClick("rc");
        }
        if (mcDown && !prevMc) {
            AnimationState.get().onClick("mc");
        }
        if (h != prevH) {
            AnimationState.get().setHoldH(h);
        }
        // 无基岩物品时清空手持模型
        if (!lc && !rc && !mcDown && !h) {
            String model = BedrockItemRenderer.modelOf(mc.player.getMainHandItem());
            if (model == null) {
                AnimationState.get().setHandModel(null);
            }
        }

        prevLc = lc;
        prevRc = rc;
        prevMc = mcDown;
        prevH = h;
    }

    private KeyBindHandler() {}
}
