package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.AnimationIndex.KeyAction;
import com.componentsapi.client.bedrock.AnimationIndex.ModelConfig;
import com.componentsapi.client.bedrock.BedrockAnimation.BoneAnimation;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.List;
import java.util.Map;

/**
 * 第一人称物品基岩模型动画状态。
 *
 * 管理：
 * - 当前手持物品的基岩模型（由 custom_data capi_bedrock_model 标识）；
 * - 默认动画（index default_animation）；
 * - 切换动画（switch.in / switch.out）；
 * - 按键绑定（lc/rc/mc 点击，clicks 多段循环；h 持续）；
 * - 服务端 /pba 强制播放。
 */
public final class AnimationState {

    private static final AnimationState INSTANCE = new AnimationState();

    /** 当前手持物品模型 id（namespace:name），null=无。 */
    private String handModel;
    /** 当前动画（namespace:name 关联的动画集合内）。 */
    private ActiveAnimation current;
    /** 上一帧手持模型（用于检测切换）。 */
    private String prevModel;
    /** 切出动画待播放（模型切换瞬间）。 */
    private ActiveAnimation switchOutPending;
    /** 各按键当前点击次数（多段循环）。 */
    private final int[] clickCounts = new int[256];
    /** h 键是否按住。 */
    private boolean holdingH;
    /** 第一人称是否模拟原版手持（toggle，按键切换）。 */
    private boolean vanillaFpMode;
    /** 原版模拟状态下是否压制其他按键动画（默认 true）。 */
    private boolean fpBlockAnimations = true;
    /** fp 切换过渡动画（非 null 时正在播放基岩↔原版的关键帧过渡）。 */
    private ActiveAnimation fpTransition;
    /** fp 过渡方向：true=基岩→原版（结束后交原版），false=原版→基岩（结束后回默认）。 */
    private boolean fpTransitionToVanilla;

    public static AnimationState get() {
        return INSTANCE;
    }

    /** 每帧调用：更新手持模型，处理切换动画。 */
    public void setHandModel(String modelKey) {
        this.handModel = modelKey;
        if (modelKey == null) {
            // 物品被切出：如果有切出动画，播放
            if (prevModel != null && !prevModel.equals(modelKey)) {
                ModelConfig cfg = AnimationIndex.get().get(prevModel);
                if (cfg != null && cfg.switchOut != null) {
                    switchOutPending = build(prevModel, cfg.switchOut, false);
                }
            }
            if (switchOutPending == null) {
                current = null;
            }
            prevModel = null;
            resetClicks();
            return;
        }
        // 模型切换：先切出旧模型，再切入新模型
        if (prevModel != null && !prevModel.equals(modelKey)) {
            ModelConfig oldCfg = AnimationIndex.get().get(prevModel);
            if (oldCfg != null && oldCfg.switchOut != null) {
                switchOutPending = build(prevModel, oldCfg.switchOut, false);
            }
            resetClicks();
        }
        prevModel = modelKey;
        if (current == null) {
            startDefault(modelKey);
        }
    }

    /** 播放默认动画（切入动画优先）。 */
    private void startDefault(String modelKey) {
        ModelConfig cfg = AnimationIndex.get().get(modelKey);
        if (cfg != null && cfg.switchIn != null && switchOutPending == null) {
            ActiveAnimation in = build(modelKey, cfg.switchIn, true);
            if (in != null) {
                current = in;
                return;
            }
        }
        String animName = cfg != null ? cfg.defaultAnimation : null;
        if (animName == null) {
            // 回退到 idle
            Map<String, BedrockAnimation> set = BedrockModelManager.get().animationsForModel(modelKey);
            if (set != null) {
                for (BedrockAnimation a : set.values()) {
                    if (a.name.toLowerCase().contains("idle")) {
                        animName = a.name;
                        break;
                    }
                }
            }
        }
        if (animName != null) {
            current = build(modelKey, animName, true);
        }
    }

    /** 按键点击（lc/rc/mc/fp）。 */
    public void onClick(String key) {
        if (handModel == null) return;
        ModelConfig cfg = AnimationIndex.get().get(handModel);
        if (cfg == null) return;
        List<KeyAction> actions = cfg.keyActions.get(key);
        if (actions == null || actions.isEmpty()) return;

        int idx = keyToIndex(key);
        clickCounts[idx]++;
        if (clickCounts[idx] > actions.size()) {
            clickCounts[idx] = 1;
        }
        // 找到对应 clicks 的动作（clicks 从 1 开始）
        KeyAction target = null;
        for (KeyAction a : actions) {
            if (a.clicks == clickCounts[idx]) {
                target = a;
                break;
            }
        }
        if (target == null) {
            target = actions.get(clickCounts[idx] - 1);
        }
        if (target == null) return;

        // fp 切换过渡进行中：过渡是强制的，忽略所有按键（含再次 toggle）
        if (fpTransition != null) {
            return;
        }

        // 切换第一人称是否模拟原版手持（即使在 force 播放中也允许，用于退出）
        if (target.toggleFirstPerson) {
            fpBlockAnimations = target.blockAnimations;
            if (!vanillaFpMode) {
                // 基岩 → 原版：先播退场关键帧（基岩仍渲染），结束后再交原版
                String outAnim = target.toVanillaAnimation != null
                        ? target.toVanillaAnimation : cfg.switchOut;
                ActiveAnimation t = outAnim != null ? build(handModel, outAnim, false) : null;
                if (t != null) {
                    fpTransition = t;
                    fpTransitionToVanilla = true;
                    current = t;
                } else {
                    vanillaFpMode = true; // 无过渡动画：瞬间切换
                }
            } else {
                // 原版 → 基岩：立即恢复基岩渲染，再播进场关键帧
                vanillaFpMode = false;
                String inAnim = target.toBedrockAnimation != null
                        ? target.toBedrockAnimation : cfg.switchIn;
                ActiveAnimation t = inAnim != null ? build(handModel, inAnim, false) : null;
                if (t != null) {
                    fpTransition = t;
                    fpTransitionToVanilla = false;
                    current = t;
                } else {
                    startDefault(handModel); // 无过渡动画：瞬间回默认
                }
            }
            return;
        }

        // 处于原版模拟且压制其他动画时，忽略其他按键效果
        if (vanillaFpMode && fpBlockAnimations) {
            return;
        }
        // 当前动画强制播放中，普通按键无法打断
        if (!canInterruptCurrent()) {
            return;
        }
        if (target.animation != null) {
            ActiveAnimation anim = build(handModel, target.animation, true);
            if (anim != null) {
                current = anim;
            }
        }
    }

    /** h 持续按键状态。 */
    public void setHoldH(boolean held) {
        this.holdingH = held;
        if (handModel == null) return;
        ModelConfig cfg = AnimationIndex.get().get(handModel);
        if (cfg == null) return;
        List<KeyAction> actions = cfg.keyActions.get("h");
        if (actions == null || actions.isEmpty()) return;

        // fp 过渡进行中：检视按键不参与（held 与松开都不打断过渡）
        if (fpTransition != null) return;

        if (held) {
            KeyAction a = actions.get(0);
            // 原版模拟压制 / 强制播放阻断
            if (vanillaFpMode && fpBlockAnimations) return;
            if (!canInterruptCurrent()) return;
            if (a.animation != null) {
                current = build(handModel, a.animation, true);
            }
        } else {
            // 松开后回到默认
            startDefault(handModel);
        }
    }

    /** 当前动画是否可被普通按键打断（force 强制播放且未结束时不可）。 */
    private boolean canInterruptCurrent() {
        ActiveAnimation a = current;
        if (a == null) return true;
        if (!AnimationIndex.get().isForcePlay(a.modelKey, a.animName)) {
            return true;
        }
        // 强制动画：循环动画在停播前不可打断；非循环动画播放结束后可打断
        if (a.loop) return false;
        float elapsed = (System.currentTimeMillis() - a.startMs) / 1000f;
        return elapsed >= a.animation.length;
    }

    /** 服务端 /pba 强制播放。 */
    public void handlePacket(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte type = in.readByte();
            if (type == 1) {
                current = null;
                if (handModel != null) startDefault(handModel);
                return;
            }
            String modelKey = in.readUTF(); // namespace:name（不再有 .animation.json）
            String animName = in.readUTF();
            in.readInt(); // duration（保留兼容）
            current = build(modelKey, animName, true);
        } catch (Exception e) {
            com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                    "处理 pba 报文失败: " + e.getMessage());
        }
    }

    private ActiveAnimation build(String modelKey, String animName, boolean loop) {
        Map<String, BedrockAnimation> set = BedrockModelManager.get().animationsForModel(modelKey);
        if (set == null) return null;
        BedrockAnimation anim = BedrockModelManager.findAnimation(set, animName);
        if (anim == null) return null;
        return new ActiveAnimation(anim, modelKey, anim.name,
                System.currentTimeMillis(), loop);
    }

    /** 采样指定骨骼的当前动画变换。 */
    public BoneTransform sample(String boneName) {
        ActiveAnimation anim = current;
        if (anim == null) {
            // 检查切出动画
            anim = switchOutPending;
            if (anim == null) return null;
        }
        float elapsed = (System.currentTimeMillis() - anim.startMs) / 1000f;
        float animTime;
        if (anim.loop) {
            animTime = elapsed % anim.animation.length;
        } else {
            animTime = Math.min(elapsed, anim.animation.length);
            if (elapsed >= anim.animation.length) {
                // 非循环动画结束
                if (anim == switchOutPending) {
                    switchOutPending = null;
                    if (handModel != null) {
                        startDefault(handModel);
                    }
                } else if (current == anim) {
                    if (anim == fpTransition) {
                        // fp 切换过渡结束
                        fpTransition = null;
                        if (fpTransitionToVanilla) {
                            // 基岩→原版退场结束：交原版手持渲染
                            vanillaFpMode = true;
                            current = null;
                        } else {
                            // 原版→基岩进场结束：回基岩默认
                            startDefault(handModel);
                        }
                    } else {
                        // 普通按键动画结束后回到默认
                        if (handModel != null) {
                            startDefault(handModel);
                        } else {
                            current = null;
                        }
                    }
                }
                return null;
            }
        }
        BoneAnimation channel = anim.animation.bones.get(boneName);
        if (channel == null) return null;
        float[] pos = BedrockAnimation.sample(channel.position, animTime, new float[]{0, 0, 0});
        float[] rot = BedrockAnimation.sample(channel.rotation, animTime, new float[]{0, 0, 0});
        float[] scale = BedrockAnimation.sample(channel.scale, animTime, new float[]{1, 1, 1});
        return new BoneTransform(pos[0], pos[1], pos[2], rot[0], rot[1], rot[2],
                scale[0], scale[1], scale[2]);
    }

    public boolean isPlaying() {
        return current != null || switchOutPending != null;
    }

    private float lastEffectTime = -1f;
    private ActiveAnimation lastEffectAnim;

    /**
     * 每帧（第一人称主手渲染时）调用：检测当前动画跨越的声音/粒子关键帧并播放。
     * 声音走原版 sounds 注册体系（可被 /playsound 识别）。
     */
    public void tickEffects() {
        ActiveAnimation anim = current != null ? current : switchOutPending;
        if (anim == null) {
            lastEffectTime = -1f;
            lastEffectAnim = null;
            return;
        }
        float elapsed = (System.currentTimeMillis() - anim.startMs) / 1000f;
        float animTime = anim.loop
                ? elapsed % anim.animation.length
                : Math.min(elapsed, anim.animation.length);
        if (lastEffectAnim == anim && lastEffectTime >= 0f) {
            float from = lastEffectTime;
            float to = animTime;
            if (anim.loop && to < from) {
                // 循环回绕：分两段触发
                BedrockItemEffects.fireRange(anim.animation, from, anim.animation.length);
                BedrockItemEffects.fireRange(anim.animation, 0f, to);
            } else if (to > from) {
                BedrockItemEffects.fireRange(anim.animation, from, to);
            }
        }
        lastEffectAnim = anim;
        lastEffectTime = animTime;
    }

    public boolean isVanillaFpMode() {
        return vanillaFpMode;
    }

    public String getHandModel() {
        return handModel;
    }

    public AnimationIndex.ModelConfig getConfig() {
        return handModel != null ? AnimationIndex.get().get(handModel) : null;
    }

    private void resetClicks() {
        java.util.Arrays.fill(clickCounts, 0);
        holdingH = false;
        vanillaFpMode = false;
        fpBlockAnimations = true;
        fpTransition = null;
        fpTransitionToVanilla = false;
    }

    private static int keyToIndex(String key) {
        return switch (key) {
            case "lc" -> 0;
            case "rc" -> 1;
            case "mc" -> 2;
            case "fp" -> 4;
            default -> 3;
        };
    }

    public static final class BoneTransform {
        public final float posX, posY, posZ;
        public final float rotX, rotY, rotZ;
        public final float scaleX, scaleY, scaleZ;
        public BoneTransform(float px, float py, float pz, float rx, float ry, float rz,
                             float sx, float sy, float sz) {
            this.posX = px; this.posY = py; this.posZ = pz;
            this.rotX = rx; this.rotY = ry; this.rotZ = rz;
            this.scaleX = sx; this.scaleY = sy; this.scaleZ = sz;
        }
    }

    private static final class ActiveAnimation {
        final BedrockAnimation animation;
        final String modelKey;
        final String animName;
        final long startMs;
        final boolean loop;
        ActiveAnimation(BedrockAnimation animation, String modelKey, String animName,
                        long startMs, boolean loop) {
            this.animation = animation;
            this.modelKey = modelKey;
            this.animName = animName;
            this.startMs = startMs;
            this.loop = loop;
        }
    }

    private AnimationState() {}
}
