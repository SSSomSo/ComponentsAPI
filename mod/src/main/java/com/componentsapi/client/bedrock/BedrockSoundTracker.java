package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.BedrockAnimation.SoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public final class BedrockSoundTracker {

    private static final Map<Integer, Float> LAST_TIME = new HashMap<>();

    public static void update(Entity entity, String fullModelId, boolean moving, float animTime) {
        if (fullModelId == null || !fullModelId.startsWith("bedrock/")) {
            LAST_TIME.remove(entity.getId());
            return;
        }
        String modelKey = fullModelId.substring("bedrock/".length());
        BedrockAnimation animation = BedrockModelManager.get().pickAnimation(modelKey, moving);
        Float previous = LAST_TIME.get(entity.getId());
        if (animation != null && previous != null) {
            float from = previous, to = animTime;
            if (animation.loop && to < from) {
                fireRange(animation, from, animation.length, entity);
                fireRange(animation, 0f, to, entity);
            } else { fireRange(animation, from, to, entity); }
        }
        LAST_TIME.put(entity.getId(), animTime);
    }

    private static void fireRange(BedrockAnimation animation, float from, float to, Entity entity) {
        for (SoundEvent sound : animation.sounds)
            if (sound.time > from && sound.time <= to) play(sound, entity);
    }

    private static void play(SoundEvent sound, Entity entity) {
        Identifier id = Identifier.tryParse(sound.effect);
        if (id == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        float pitch = sound.pitch;
        if (sound.pitchMax > sound.pitchMin)
            pitch = sound.pitchMin + level.getRandom().nextFloat() * (sound.pitchMax - sound.pitchMin);
        level.playLocalSound(entity.getX(), entity.getY() + 0.5, entity.getZ(),
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),
                SoundSource.NEUTRAL, sound.volume, pitch, false);
    }

    private BedrockSoundTracker() {}
}
