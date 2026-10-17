package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.AnimationIndex.ModelConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * 按键绑定动画触发器。
 *
 * <p>动态读取当前手持基岩物品 index 中 {@code keys} 声明的全部键：
 * <ul>
 *   <li>{@code lc/rc/mc}：鼠标左/右/中键；</li>
 *   <li>{@code fp}：第一人称模拟切换（默认 R 键）；</li>
 *   <li>其余键名见 {@link KeyNames}：字母、数字、F1-F25、方向键、符号、小键盘、
 *       修饰键等键盘上几乎所有按键。</li>
 * </ul>
 *
 * <p>带 {@code clicks} 的动作为点击序列（多段循环）；未写 {@code clicks} 的键盘键
 * 为“按住（hold）”：按下播放动画、松开回到默认。
 */
public final class KeyBindHandler {

    /** 每个键上一帧的按下状态。 */
    private static final Map<String, Boolean> prev = new HashMap<>();
    private static String prevModel = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(KeyBindHandler::onTick);
    }

    private static boolean isDown(Minecraft mc, String key) {
        return switch (key) {
            case "lc" -> mc.options.keyAttack.isDown();
            case "rc" -> mc.options.keyUse.isDown();
            case "mc" -> mc.options.keyPickItem.isDown();
            default -> {
                int code = KeyNames.glfwKey(key);
                yield code >= 0 && InputConstants.isKeyDown(mc.getWindow(), code);
            }
        };
    }

    private static void onTick(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        String model = BedrockItemRenderer.modelOf(mc.player.getMainHandItem());
        AnimationState.get().setHandModel(model);
        // 疾跑状态机：每帧检测疾跑状态变化
        AnimationState.get().updateSprint(mc.player.isSprinting());

        if (model == null) {
            prev.clear();
            prevModel = null;
            return;
        }
        ModelConfig cfg = AnimationIndex.get().get(model);
        if (cfg == null) {
            prev.clear();
            prevModel = model;
            return;
        }

        // 切换手持物品后的第一帧：仅记录当前按键状态，不触发事件（避免切换瞬间误触）
        if (!model.equals(prevModel)) {
            prev.clear();
            for (String key : cfg.keyActions.keySet()) {
                prev.put(key, isDown(mc, key));
            }
            prevModel = model;
            return;
        }

        for (String key : cfg.keyActions.keySet()) {
            boolean down = isDown(mc, key);
            boolean was = prev.getOrDefault(key, false);
            if (down == was) {
                continue;
            }
            if (cfg.isHoldKey(key)) {
                AnimationState.get().setHold(key, down);
            } else if (down) {
                AnimationState.get().onClick(key);
            }
            prev.put(key, down);
        }
    }

    private KeyBindHandler() {}
}
