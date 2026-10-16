package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.BedrockAnimation.ParticleEvent;
import com.componentsapi.client.bedrock.BedrockAnimation.SoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * 物品基岩动画的声音/粒子关键帧播放器（本地玩家主手）。
 *
 * 声音走原版音效注册体系：动画 sound_effects 里的 effect 写原版音效事件 id
 * （形如 capi:draw），资源包在 assets/&lt;ns&gt;/sounds.json 声明事件、
 * 音频文件放 assets/&lt;ns&gt;/sounds/...（与原版位置完全一致），
 * 因此既由动画自动播放，也能用 /playsound &lt;ns&gt;:&lt;事件&gt; 手动播放。
 */
public final class BedrockItemEffects {

    /** 检测 [from,to] 区间内的声音/粒子关键帧并播放（循环回绕时分段调用）。 */
    public static void fireRange(BedrockAnimation animation, float from, float to) {
        for (SoundEvent sound : animation.sounds) {
            if (sound.time > from && sound.time <= to) {
                playSound(sound);
            }
        }
        for (ParticleEvent particle : animation.particles) {
            if (particle.time > from && particle.time <= to) {
                spawnParticle(particle);
            }
        }
    }

    private static void playSound(SoundEvent sound) {
        Identifier id = Identifier.tryParse(sound.effect);
        if (id == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return;
        }
        float pitch = sound.pitch;
        if (sound.pitchMax > sound.pitchMin) {
            pitch = sound.pitchMin
                    + level.getRandom().nextFloat() * (sound.pitchMax - sound.pitchMin);
        }
        // 本地玩家动作音：在玩家位置播放，自己可清晰听到（非 UI 音，受距离/分类控制）
        level.playLocalSound(
                player.getX(), player.getEyeY(), player.getZ(),
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),
                SoundSource.PLAYERS, sound.volume, pitch, false);
    }

    private static void spawnParticle(ParticleEvent particle) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return;
        }
        Identifier id = Identifier.tryParse(particle.effect);
        if (id == null) {
            return;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id)
                .map(ref -> ref.value())
                .orElse(null);
        if (type instanceof ParticleOptions options) {
            level.addParticle(options,
                    player.getX(), player.getEyeY() - 0.1, player.getZ(),
                    0.0, 0.0, 0.0);
        }
    }

    private BedrockItemEffects() {}
}
