package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.BedrockAnimation.ParticleEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * 粒子关键帧跟踪器。
 * 在 extractRenderState（每帧每实体一次）时调用，记录每个实体上一次的动画时间，
 * 当动画时间跨过粒子关键帧时，在实体位置（或指定骨骼 locator 偏移处）生成粒子；
 * 循环动画处理回绕。
 */
public final class BedrockParticleTracker {

    private static final Map<Integer, Float> LAST_TIME = new HashMap<>();

    public static void update(Entity entity, String fullModelId,
                              boolean moving, float animTime) {
        if (fullModelId == null || !fullModelId.startsWith("bedrock/")) {
            LAST_TIME.remove(entity.getId());
            return;
        }
        String modelKey = fullModelId.substring("bedrock/".length());
        BedrockAnimation animation =
                BedrockModelManager.get().pickAnimation(modelKey, moving);
        if (animation == null || animation.particles.isEmpty()) {
            LAST_TIME.remove(entity.getId());
            return;
        }

        Float previous = LAST_TIME.get(entity.getId());
        if (previous != null) {
            float from = previous;
            float to = animTime;
            if (animation.loop && to < from) {
                fireRange(animation, from, animation.length, entity, modelKey);
                fireRange(animation, 0f, to, entity, modelKey);
            } else {
                fireRange(animation, from, to, entity, modelKey);
            }
        }
        LAST_TIME.put(entity.getId(), animTime);
    }

    private static void fireRange(BedrockAnimation animation, float from, float to,
                                  Entity entity, String modelKey) {
        for (ParticleEvent particle : animation.particles) {
            if (particle.time > from && particle.time <= to) {
                spawn(particle, entity, modelKey);
            }
        }
    }

    private static void spawn(ParticleEvent particle, Entity entity, String modelKey) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Identifier id = Identifier.tryParse(particle.effect);
        if (id == null) {
            return;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id)
                .map(ref -> ref.value())
                .orElse(null);
        if (type == null) {
            return;
        }

        // 计算生成位置：默认实体中心；有 locator 时用骨骼 pivot 作偏移。
        double px = entity.getX();
        double py = entity.getY() + 0.5;
        double pz = entity.getZ();
        if (particle.locator != null) {
            BedrockModel model = BedrockModelManager.get().model(modelKey);
            if (model != null) {
                for (BedrockModel.Bone bone : model.bones) {
                    if (bone.name.equals(particle.locator)) {
                        // 基岩像素 → 方块单位，y 轴向上
                        px += bone.pivotX / 16.0;
                        py += bone.pivotY / 16.0;
                        pz += bone.pivotZ / 16.0;
                        break;
                    }
                }
            }
        }

        // 简单粒子类型（SimpleParticleType）本身即 ParticleOptions；
        // 复杂粒子（dust 等）需要额外参数，暂不支持，会被跳过。
        if (type instanceof ParticleOptions options) {
            level.addParticle(options, px, py, pz, 0, 0, 0);
        }
    }

    private BedrockParticleTracker() {}
}
