package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.BedrockAnimation.BoneAnimation;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 第一人称手臂动画播放状态。
 * 由服务端 /playbedrockanimation 触发，仅影响本地玩家的第一人称视角，
 * 不影响第三人称实体渲染。
 *
 * 动画文件约定：assets/<namespace>/animations/<name>.animation.json
 * （与基岩实体动画格式相同，骨骼名通常为 rightArm/leftArm 或 righthand_pos/lefthand_pos）。
 */
public final class AnimationState {

    private static final AnimationState INSTANCE = new AnimationState();

    /** 当前播放的动画（null=无）。 */
    private volatile ActiveAnimation current;

    public static AnimationState get() {
        return INSTANCE;
    }

    /** 处理服务端发来的动画报文。 */
    public void handlePacket(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte type = in.readByte();
            if (type == 1) {
                // stop
                current = null;
                return;
            }
            String file = in.readUTF();   // namespace:animations/xxx.animation.json
            String name = in.readUTF();   // 动画名
            int duration = in.readInt();  // -1=循环

            // 解析 namespace 和路径
            String namespace;
            String path;
            int colon = file.indexOf(':');
            if (colon > 0) {
                namespace = file.substring(0, colon);
                path = file.substring(colon + 1);
            } else {
                namespace = "capi";
                path = file;
            }

            // 从 BedrockModelManager 加载动画（如果还没加载，尝试从资源包读取）
            Map<String, BedrockAnimation> anims = BedrockModelManager.get().animationsForFile(namespace, path);
            BedrockAnimation anim = anims != null ? anims.get(name) : null;
            if (anim == null) {
                com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                        "第一人称动画未找到: " + file + " -> " + name);
                return;
            }

            current = new ActiveAnimation(anim, namespace, path, name,
                    System.currentTimeMillis(), duration);
        } catch (Exception e) {
            com.componentsapi.client.ComponentsApiClient.LOGGER.warn(
                    "处理动画报文失败: " + e.getMessage());
        }
    }

    /** 获取指定骨骼在当前动画中的变换（局部坐标，像素单位）。null=无动画。 */
    public BoneTransform sample(String boneName, float partialTicks) {
        ActiveAnimation anim = current;
        if (anim == null) {
            return null;
        }
        float elapsed = (System.currentTimeMillis() - anim.startMs) / 1000f;
        float animTime;
        if (anim.animation.loop) {
            animTime = elapsed % anim.animation.length;
        } else {
            animTime = Math.min(elapsed, anim.animation.length);
            if (elapsed >= anim.animation.length && anim.duration != -1) {
                current = null;
                return null;
            }
        }

        BoneAnimation channel = anim.animation.bones.get(boneName);
        if (channel == null) {
            return null;
        }
        float[] pos = BedrockAnimation.sample(channel.position, animTime, new float[]{0, 0, 0});
        float[] rot = BedrockAnimation.sample(channel.rotation, animTime, new float[]{0, 0, 0});
        float[] scale = BedrockAnimation.sample(channel.scale, animTime, new float[]{1, 1, 1});
        return new BoneTransform(pos[0], pos[1], pos[2], rot[0], rot[1], rot[2],
                scale[0], scale[1], scale[2]);
    }

    public boolean isPlaying() {
        return current != null;
    }

    public void stop() {
        current = null;
    }

    /** 骨骼变换结果（像素单位，角度）。 */
    public static final class BoneTransform {
        public final float posX, posY, posZ;
        public final float rotX, rotY, rotZ;
        public final float scaleX, scaleY, scaleZ;

        public BoneTransform(float px, float py, float pz,
                             float rx, float ry, float rz,
                             float sx, float sy, float sz) {
            this.posX = px; this.posY = py; this.posZ = pz;
            this.rotX = rx; this.rotY = ry; this.rotZ = rz;
            this.scaleX = sx; this.scaleY = sy; this.scaleZ = sz;
        }
    }

    private static final class ActiveAnimation {
        final BedrockAnimation animation;
        final String namespace;
        final String filePath;
        final String animName;
        final long startMs;
        final int duration;

        ActiveAnimation(BedrockAnimation animation, String namespace, String filePath,
                        String animName, long startMs, int duration) {
            this.animation = animation;
            this.namespace = namespace;
            this.filePath = filePath;
            this.animName = animName;
            this.startMs = startMs;
            this.duration = duration;
        }
    }

    private AnimationState() {}
}
