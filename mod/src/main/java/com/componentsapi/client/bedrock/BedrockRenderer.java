package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.BedrockAnimation.BoneAnimation;
import com.componentsapi.client.bedrock.BedrockModel.Bone;
import com.componentsapi.client.bedrock.BedrockModel.Cube;
import com.componentsapi.client.bedrock.BedrockModel.Polygon;
import com.componentsapi.client.CapiRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 基岩模型渲染器（26.2 submit 管线版）。
 *
 * <p>本类与 {@link BedrockModel} 一起在 1.14.0 重写为标准 {@code ModelPart}
 * （Y 轴向下局部坐标）体系：
 * <ul>
 *   <li>骨骼变换 = translate(局部位置) → rotateZ → rotateY → rotateX → scale，
 *       不再做 pivot 补偿（cube 顶点已烘焙为相对 bone / cube pivot 的局部坐标）。</li>
 *   <li>每个 cube 的 6 个面在解析阶段烘焙好（坐标/UV/法线），本类直接写顶点。</li>
 *   <li>定位组（lefthand_pos / righthand_pos）的参考 cube 不绘制；第一人称在
 *       {@link BedrockItemRenderer} 中替换为玩家真实手臂。</li>
 * </ul>
 *
 * 能力：骨骼层级 + 动画采样、盒子 UV / 每面 UV、生物手持物品（hand 挂点）。
 */
public final class BedrockRenderer {

    private static final float UNIT = 1f / 16f;

    /** 定位组参考 cube 沿 Y 向下的长度（标准为 12 像素），手持物品挂在末端。 */
    private static final float HAND_POS_LENGTH = 12f * UNIT;

    public static void submit(String modelKey, LivingEntityRenderState state,
                              PoseStack matrices, SubmitNodeCollector collector) {
        BedrockModel model = BedrockModelManager.get().model(modelKey);
        if (model == null) {
            return;
        }

        boolean moving = state.walkAnimationSpeed > 1.0e-4f;
        BedrockAnimation animation = BedrockModelManager.get()
                .pickAnimation(modelKey, moving);
        float animTime = state.ageInTicks / 20f;

        Identifier texture = Identifier.fromNamespaceAndPath("capi",
                "textures/bedrock/" + modelKey + ".png");

        matrices.pushPose();
        if (state.isBaby) {
            matrices.scale(0.5f, 0.5f, 0.5f);
            matrices.translate(0f, 1.5f, 0f);
        }
        matrices.mulPose(Axis.YP.rotationDegrees(-state.bodyRot));

        for (Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverse(model, bone, animation, animTime, matrices,
                        texture, collector, state);
            }
        }
        matrices.popPose();
    }

    /** 非生物入口：不做 baby/手持处理，朝向由调用方给定（如方块展示实体的 entityYRot）。 */
    public static void submitGeneric(String modelKey, EntityRenderState state,
                                     float yRot, PoseStack matrices,
                                     SubmitNodeCollector collector) {
        BedrockModel model = BedrockModelManager.get().model(modelKey);
        if (model == null) {
            return;
        }
        BedrockAnimation animation = BedrockModelManager.get()
                .pickAnimation(modelKey, false);
        float animTime = state.ageInTicks / 20f;

        Identifier texture = Identifier.fromNamespaceAndPath("capi",
                "textures/bedrock/" + modelKey + ".png");

        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(-yRot));
        for (Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverse(model, bone, animation, animTime, matrices,
                        texture, collector, state);
            }
        }
        matrices.popPose();
    }

    /**
     * 应用一个骨骼的标准变换（实体与物品渲染共用）。
     *
     * @param aPx/aPy/aPz 动画平移（基岩像素，Y 向上）；内部对 Y 取反
     * @param aRx/aRy/aRz 动画旋转叠加（度）
     */
    public static void applyBoneTransform(PoseStack matrices, Bone bone,
                                          float aPx, float aPy, float aPz,
                                          float aRx, float aRy, float aRz,
                                          float sx, float sy, float sz) {
        // 局部位置（已为单位方块）+ 动画平移（Y 向上 → Y 向下，取反）。
        matrices.translate(bone.posX + aPx * UNIT,
                bone.posY - aPy * UNIT,
                bone.posZ + aPz * UNIT);
        // 旋转顺序 Z → Y → X（与 TaCZ / ModelPart 一致）。
        float rz = bone.rotZ + aRz;
        float ry = bone.rotY + aRy;
        float rx = bone.rotX + aRx;
        matrices.mulPose(Axis.ZP.rotationDegrees(rz));
        matrices.mulPose(Axis.YP.rotationDegrees(ry));
        matrices.mulPose(Axis.XP.rotationDegrees(rx));
        matrices.scale(sx, sy, sz);
    }

    private static void traverse(BedrockModel model, Bone bone,
                                 BedrockAnimation animation, float animTime,
                                 PoseStack matrices, Identifier texture,
                                 SubmitNodeCollector collector,
                                 EntityRenderState state) {
        float aPx = 0f, aPy = 0f, aPz = 0f;
        float aRx = 0f, aRy = 0f, aRz = 0f;
        float sx = 1f, sy = 1f, sz = 1f;

        if (animation != null) {
            BoneAnimation channel = animation.bones.get(bone.name);
            if (channel != null) {
                float[] out = BedrockAnimation.sample(channel.position, animTime,
                        new float[]{0, 0, 0});
                aPx = out[0]; aPy = out[1]; aPz = out[2];
                out = BedrockAnimation.sample(channel.rotation, animTime,
                        new float[]{0, 0, 0});
                aRx = out[0]; aRy = out[1]; aRz = out[2];
                out = BedrockAnimation.sample(channel.scale, animTime,
                        new float[]{1, 1, 1});
                sx = out[0]; sy = out[1]; sz = out[2];
            }
        }

        matrices.pushPose();
        applyBoneTransform(matrices, bone, aPx, aPy, aPz, aRx, aRy, aRz, sx, sy, sz);

        // 定位组：不绘制参考 cube（第一人称由玩家手臂替代，实体路径不画几何）。
        if (!bone.handPos) {
            for (Cube cube : bone.cubes) {
                matrices.pushPose();
                if (cube.hasRot) {
                    matrices.translate(cube.offX, cube.offY, cube.offZ);
                    matrices.mulPose(Axis.ZP.rotationDegrees(cube.rotZ));
                    matrices.mulPose(Axis.YP.rotationDegrees(cube.rotY));
                    matrices.mulPose(Axis.XP.rotationDegrees(cube.rotX));
                }
                collector.submitCustomGeometry(matrices,
                        RenderTypes.entityCutout(texture),
                        (pose, consumer) -> emitCube(model, cube, pose,
                                consumer, state.lightCoords));
                matrices.popPose();
            }
        }

        // 生物手持物品挂点（仅普通 hand 骨骼；定位组是手臂替换，不挂物品）。
        if (bone.hand != 0 && !bone.handPos && state instanceof CapiRenderState capi) {
            ItemStackRenderState handState = bone.hand == 1
                    ? capi.capi$getMain() : capi.capi$getOff();
            if (!handState.isEmpty()) {
                matrices.pushPose();
                matrices.mulPose(Axis.XP.rotationDegrees(-90f));
                matrices.mulPose(Axis.YP.rotationDegrees(180f));
                handState.submit(matrices, collector, state.lightCoords,
                        OverlayTexture.NO_OVERLAY, state.outlineColor);
                matrices.popPose();
            }
        }

        for (Bone child : model.bones) {
            if (bone.name.equals(child.parent)) {
                traverse(model, child, animation, animTime, matrices,
                        texture, collector, state);
            }
        }
        matrices.popPose();
    }

    /** 公开的立方体发射方法，供物品基岩渲染器复用。 */
    public static void emitCubePublic(BedrockModel model, Cube cube,
                                      PoseStack.Pose pose, VertexConsumer consumer, int light) {
        emitCube(model, cube, pose, consumer, light);
    }

    /**
     * 发射一个 cube 的全部可见面。顶点坐标 / UV / 法线在 {@link BedrockModel}
     * 已烘焙为标准 ModelPart 数据；这里只做矩阵变换并写入 VertexConsumer。
     * 法线已先乘一次法线矩阵，随后用一次性 addVertex 写「裸法线」（26.2 的一次性
     * addVertex 不会再次 transformNormal，等价于链式 setNormal(fff)，与 TaCZ 一致）。
     */
    public static void emitCube(BedrockModel model, Cube cube,
                                PoseStack.Pose pose, VertexConsumer consumer, int light) {
        Matrix4f poseMat = pose.pose();
        Matrix3f normalMat = pose.normal();
        Vector3f tp = new Vector3f();
        Vector3f tn = new Vector3f();
        for (Polygon poly : cube.polys) {
            if (poly == null || !poly.visible) {
                continue;
            }
            normalMat.transform(poly.nx, poly.ny, poly.nz, tn);
            for (int k = 0; k < 4; k++) {
                poseMat.transformPosition(poly.x[k], poly.y[k], poly.z[k], tp);
                consumer.addVertex(tp.x, tp.y, tp.z,
                        0xFFFFFFFF,
                        poly.u[k], poly.v[k],
                        OverlayTexture.NO_OVERLAY,
                        light,
                        tn.x, tn.y, tn.z);
            }
        }
    }

    private BedrockRenderer() {}
}
