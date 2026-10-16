package com.componentsapi.client.bedrock;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 基岩模型手部定位组锚点计算（前向运动学 FK）。
 *
 * 基岩 geometry 中所有 bone 的 pivot 都是相对模型原点的全局坐标（不是相对父 bone），
 * 因此子 bone 相对父 bone 的局部偏移 = child.pivot - parent.pivot。
 *
 * 模型空间中某 bone 的世界矩阵：
 *   World[root] = T(root.pivot) * R(root) * S
 *   World[bone] = World[parent] * T(bone.pivot - parent.pivot) * R(bone) * S
 *
 * 定位组（righthand_pos / lefthand_pos）的 pivot 即手部锚点，
 * 取 World[target] 平移分量；动画的 position/rotation/scale 一并参与，
 * 从而播放动画时锚点（玩家手部）会跟随定位组移动。
 */
public final class BedrockAnchor {

    private static final float UNIT = 1f / 16f;

    /** 定位组 bone 名。 */
    public static final String RIGHT = "righthand_pos";
    public static final String LEFT = "lefthand_pos";

    /**
     * 计算指定手的定位组在模型空间（单位：方块）的锚点位置。
     *
     * @param model     基岩模型
     * @param state     动画状态（可 null，null 时只算静态骨架）
     * @param rightHand true=右手 righthand_pos，false=左手 lefthand_pos
     * @return 锚点（x,y,z，单位方块）；找不到定位组时返回 null（调用方退化为模型原点）
     */
    public static Vector3f handAnchor(BedrockModel model, AnimationState state, boolean rightHand) {
        String targetName = rightHand ? RIGHT : LEFT;
        BedrockModel.Bone target = null;
        for (BedrockModel.Bone b : model.bones) {
            if (b.name.equalsIgnoreCase(targetName)) {
                target = b;
                break;
            }
        }
        if (target == null) {
            return null;
        }
        Matrix4f world = new Matrix4f();
        accumulate(model, target, state, world);
        // 该 bone 原点（pivot）在模型空间的位置 = 世界矩阵的平移分量
        // （局部原点经 S、R 仍为原点，仅累积各级 T）
        Vector3f anchor = world.getTranslation(new Vector3f());
        return anchor;
    }

    /** 沿 root→bone 链累积世界矩阵（递归到父级）。 */
    private static void accumulate(BedrockModel model, BedrockModel.Bone bone,
                                   AnimationState state, Matrix4f world) {
        BedrockModel.Bone parent = findBone(model, bone.parent);
        if (parent != null) {
            accumulate(model, parent, state, world);
        }
        // 基岩 pivot 为全局坐标 → 相对父 bone 的局部偏移
        float px = bone.pivotX;
        float py = bone.pivotY;
        float pz = bone.pivotZ;
        if (parent != null) {
            px -= parent.pivotX;
            py -= parent.pivotY;
            pz -= parent.pivotZ;
        }
        float rx = bone.rotX, ry = bone.rotY, rz = bone.rotZ;
        float sx = 1f, sy = 1f, sz = 1f;
        if (state != null && state.isPlaying()) {
            AnimationState.BoneTransform t = state.sample(bone.name);
            if (t != null) {
                px += t.posX;
                py += t.posY;
                pz += t.posZ;
                rx += t.rotX;
                ry += t.rotY;
                rz += t.rotZ;
                sx = t.scaleX;
                sy = t.scaleY;
                sz = t.scaleZ;
            }
        }
        world.translate(px * UNIT, py * UNIT, pz * UNIT);
        world.rotateZ((float) Math.toRadians(rz));
        world.rotateY((float) Math.toRadians(ry));
        world.rotateX((float) Math.toRadians(rx));
        world.scale(sx, sy, sz);
    }

    private static BedrockModel.Bone findBone(BedrockModel model, String name) {
        if (name == null) {
            return null;
        }
        for (BedrockModel.Bone b : model.bones) {
            if (b.name.equals(name)) {
                return b;
            }
        }
        return null;
    }

    private BedrockAnchor() {}
}
