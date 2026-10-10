package com.componentsapi.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BedrockAnimation {

    public static final class Keyframe {
        public float time;
        public float x, y, z;
        public boolean step;
    }

    public static final class BoneAnimation {
        public final List<Keyframe> position = new ArrayList<>();
        public final List<Keyframe> rotation = new ArrayList<>();
        public final List<Keyframe> scale = new ArrayList<>();
    }

    public static final class SoundEvent {
        public float time;
        public String effect;
        public float volume = 1f;
        public float pitch = 1f;
        public float pitchMin;
        public float pitchMax;
    }

    public String name;
    public float length;
    public boolean loop;
    public final java.util.Map<String, BoneAnimation> bones = new java.util.HashMap<>();
    public final List<SoundEvent> sounds = new ArrayList<>();

    public static java.util.Map<String, BedrockAnimation> parseAll(JsonObject root) {
        java.util.Map<String, BedrockAnimation> result = new java.util.HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject ao = entry.getValue().getAsJsonObject();
            BedrockAnimation animation = new BedrockAnimation();
            animation.name = entry.getKey();
            animation.length = ao.has("animation_length") ? ao.get("animation_length").getAsFloat() : 0f;
            animation.loop = ao.has("loop") && (ao.get("loop").getAsBoolean()
                    || "hold_on_last_frame".equals(ao.get("loop").getAsString()));
            if (ao.has("bones")) {
                JsonObject bonesObject = ao.getAsJsonObject("bones");
                for (Map.Entry<String, JsonElement> boneEntry : bonesObject.entrySet()) {
                    JsonObject bo = boneEntry.getValue().getAsJsonObject();
                    BoneAnimation bone = new BoneAnimation();
                    readChannel(bo, "position", bone.position, 0f, 0f, 0f);
                    readChannel(bo, "rotation", bone.rotation, 0f, 0f, 0f);
                    readChannel(bo, "scale", bone.scale, 1f, 1f, 1f);
                    animation.bones.put(boneEntry.getKey(), bone);
                }
            }
            if (ao.has("sound_effects")) {
                JsonObject soundsObject = ao.getAsJsonObject("sound_effects");
                for (Map.Entry<String, JsonElement> soundEntry : soundsObject.entrySet()) {
                    float time = Float.parseFloat(soundEntry.getKey());
                    java.util.List<JsonObject> objects = new ArrayList<>();
                    JsonElement valueElement = soundEntry.getValue();
                    if (valueElement.isJsonArray()) {
                        for (JsonElement element : valueElement.getAsJsonArray()) objects.add(element.getAsJsonObject());
                    } else { objects.add(valueElement.getAsJsonObject()); }
                    for (JsonObject so : objects) {
                        SoundEvent sound = new SoundEvent();
                        sound.time = time;
                        sound.effect = so.get("effect").getAsString();
                        if (so.has("volume")) sound.volume = so.get("volume").getAsFloat();
                        if (so.has("pitch")) {
                            JsonElement pitchElement = so.get("pitch");
                            if (pitchElement.isJsonArray()) {
                                JsonArray pitchArray = pitchElement.getAsJsonArray();
                                sound.pitchMin = pitchArray.get(0).getAsFloat();
                                sound.pitchMax = pitchArray.get(Math.min(1, pitchArray.size() - 1)).getAsFloat();
                                sound.pitch = sound.pitchMin;
                            } else { sound.pitch = pitchElement.getAsFloat(); }
                        }
                        animation.sounds.add(sound);
                    }
                }
                animation.sounds.sort((a, b) -> Float.compare(a.time, b.time));
            }
            result.put(animation.name, animation);
        }
        return result;
    }

    private static void readChannel(JsonObject boneObject, String channel,
                                    List<Keyframe> frames, float dx, float dy, float dz) {
        if (!boneObject.has(channel)) return;
        JsonElement element = boneObject.get(channel);
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            Keyframe frame = new Keyframe();
            frame.x = array.get(0).getAsFloat() + dx;
            frame.y = array.get(1).getAsFloat() + dy;
            frame.z = array.get(2).getAsFloat() + dz;
            frames.add(frame);
            return;
        }
        JsonObject keyframes = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> keyframeEntry : keyframes.entrySet()) {
            float time = Float.parseFloat(keyframeEntry.getKey());
            JsonElement value = keyframeEntry.getValue();
            boolean step = false;
            JsonArray array;
            if (value.isJsonObject()) {
                JsonObject wrapper = value.getAsJsonObject();
                array = wrapper.getAsJsonArray("vector");
                if (wrapper.has("lerp_mode")) step = "step".equals(wrapper.get("lerp_mode").getAsString());
            } else { array = value.getAsJsonArray(); }
            Keyframe frame = new Keyframe();
            frame.time = time;
            frame.x = array.get(0).getAsFloat() + dx;
            frame.y = array.get(1).getAsFloat() + dy;
            frame.z = array.get(2).getAsFloat() + dz;
            frame.step = step;
            frames.add(frame);
        }
        frames.sort((a, b) -> Float.compare(a.time, b.time));
    }

    public static float[] sample(List<Keyframe> frames, float time, float[] out) {
        if (frames.isEmpty()) return out;
        Keyframe first = frames.get(0);
        if (time <= first.time) { out[0] = first.x; out[1] = first.y; out[2] = first.z; return out; }
        for (int i = 0; i < frames.size() - 1; i++) {
            Keyframe a = frames.get(i);
            Keyframe b = frames.get(i + 1);
            if (time >= a.time && time <= b.time) {
                if (b.step) { out[0] = a.x; out[1] = a.y; out[2] = a.z; }
                else {
                    float t = (time - a.time) / Math.max(1e-6f, b.time - a.time);
                    out[0] = a.x + (b.x - a.x) * t;
                    out[1] = a.y + (b.y - a.y) * t;
                    out[2] = a.z + (b.z - a.z) * t;
                }
                return out;
            }
        }
        Keyframe last = frames.get(frames.size() - 1);
        out[0] = last.x; out[1] = last.y; out[2] = last.z;
        return out;
    }
}
