package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.BedrockModel.Bone;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 基岩模型手部定位组锚点计算（前向运动学 FK），1.14.0 改为 Java Y 向下局部体系。
 *
 * <p>{@link BedrockModel} 已把每个 bone 的位置烘焙为相对父骨骼的局部坐标
 * （{@code posX/posY/posZ}，单位方块）。本类沿 root→定位组 链按与渲染完全相同的
 * 顺序（translate → rotateZ → rotateY → rotateX → scale）累积世界矩阵。
 *
 * <p>定位组 {@code righthand_pos} / {@code lefthand_pos} 的 pivot 是<b>手臂根
 * （肩膀）</b>，定位块尺寸 x4 y12 z4，沿 Y 向下延伸 12 像素到手腕。第三人称物品
 * 要对齐到玩家的手（握把），所以锚点取该骨骼空间下 {@code (0, 12, 0)}（手腕）经
 * 世界矩阵变换后的位置。
 */
public final class BedrockAnchor {

    private static final float UNIT = 1f / 16f;
    /** 手臂定位块长度（12 像素），从肩膀到手腕。 */
    private static final float ARM_LENGTH = 12f * UNIT;

    /** 定位组 bone 名。 */
    public static final String RIGHT = "righthand_pos";
    public static final String LEFT = "lefthand_pos";

    /**
     * 计算指定手的定位组手腕在模型空间（单位：方块）的锚点。
     *
     * @param model     基岩模型
     * @param state     动画状态（可 null，null 时只算静态骨架）
     * @param rightHand true=右手 righthand_pos，false=左手 lefthand_pos
     * @return 手腕锚点（x,y,z，单位方块）；找不到定位组时返回 null（调用方退化为模型原点）
     */
    public static Vector3f handAnchor(BedrockModel model, AnimationState state, boolean rightHand) {
        String targetName = rightHand ? RIGHT : LEFT;
        Bone target = null;
        for (Bone b : model.bones) {
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
        // 手腕在骨骼空间为 (0, ARM_LENGTH, 0)（Y 向下），经世界矩阵变换（含旋转）。
        return world.transformPosition(0f, ARM_LENGTH, 0f, new Vector3f());
    }

    /** 沿 root→bone 链累积世界矩阵（与渲染的 applyBoneTransform 完全一致）。 */
    private static void accumulate(BedrockModel model, Bone bone,
                                   AnimationState state, Matrix4f world) {
        Bone parent = findBone(model, bone.parent);
        if (parent != null) {
            accumulate(model, parent, state, world);
        }

        float aPx = 0f, aPy = 0f, aPz = 0f;
        float aRx = 0f, aRy = 0f, aRz = 0f;
        float sx = 1f, sy = 1f, sz = 1f;
        if (state != null && state.isPlaying()) {
            AnimationState.BoneTransform t = state.sample(bone.name);
            if (t != null) {
                aPx = t.posX; aPy = t.posY; aPz = t.posZ;
                aRx = t.rotX; aRy = t.rotY; aRz = t.rotZ;
                sx = t.scaleX; sy = t.scaleY; sz = t.scaleZ;
            }
        }

        // 局部位置（已为单位方块）+ 动画平移（Y 向上 → Y 向下，取反）。
        world.translate(bone.posX + aPx * UNIT,
                bone.posY - aPy * UNIT,
                bone.posZ + aPz * UNIT);
        // 旋转顺序 Z → Y → X。
        world.rotateZ((float) Math.toRadians(bone.rotZ + aRz));
        world.rotateY((float) Math.toRadians(bone.rotY + aRy));
        world.rotateX((float) Math.toRadians(bone.rotX + aRx));
        world.scale(sx, sy, sz);
    }

    private static Bone findBone(BedrockModel model, String name) {
        if (name == null) {
            return null;
        }
        for (Bone b : model.bones) {
            if (b.name.equals(name)) {
                return b;
            }
        }
        return null;
    }

    private BedrockAnchor() {}
}
