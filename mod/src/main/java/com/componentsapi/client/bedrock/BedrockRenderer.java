package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.BedrockAnimation.BoneAnimation;
import com.componentsapi.client.bedrock.BedrockModel.Bone;
import com.componentsapi.client.bedrock.BedrockModel.Cube;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.componentsapi.client.CapiRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * 基岩模型渲染器（26.2 submit 管线版）。
 *
 * 能力：
 * - 骨骼层级 + 动画采样（PoseStack），盒子 UV / 每面 UV；
 * - 手持物品：标记为 hand 的骨骼处提交对应手的 ItemStackRenderState
 *   （方块物品自动以 3D 方块形式显示）；
 * - 通用入口 {@link #submitGeneric} 供非生物（方块展示实体等）使用。
 *
 * 说明：坐标/旋转符号若实测整体反向，只需调整本类中 YP 旋转与各面顶点顺序。
 */
public final class BedrockRenderer {

    private static final float UNIT = 1f / 16f;

    // 面索引与 BedrockModel.FACE_NAMES 一致：0 north,1 south,2 east,3 west,4 up,5 down
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
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-state.bodyRot));

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
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot));
        for (Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverse(model, bone, animation, animTime, matrices,
                        texture, collector, state);
            }
        }
        matrices.popPose();
    }

    private static void traverse(BedrockModel model, Bone bone,
                                 BedrockAnimation animation, float animTime,
                                 PoseStack matrices, Identifier texture,
                                 SubmitNodeCollector collector,
                                 EntityRenderState state) {
        float posX = 0f, posY = 0f, posZ = 0f;
        float rotX = bone.rotX, rotY = bone.rotY, rotZ = bone.rotZ;
        float scaleX = 1f, scaleY = 1f, scaleZ = 1f;

        if (animation != null) {
            BoneAnimation channel = animation.bones.get(bone.name);
            if (channel != null) {
                float[] out = BedrockAnimation.sample(channel.position, animTime,
                        new float[]{0, 0, 0});
                posX = out[0]; posY = out[1]; posZ = out[2];
                out = BedrockAnimation.sample(channel.rotation, animTime,
                        new float[]{0, 0, 0});
                rotX += out[0]; rotY += out[1]; rotZ += out[2];
                out = BedrockAnimation.sample(channel.scale, animTime,
                        new float[]{1, 1, 1});
                scaleX = out[0]; scaleY = out[1]; scaleZ = out[2];
            }
        }

        matrices.pushPose();
        matrices.translate((bone.pivotX + posX) * UNIT,
                (bone.pivotY + posY) * UNIT,
                (bone.pivotZ + posZ) * UNIT);
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotX));
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotY));
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotZ));
        matrices.scale(scaleX, scaleY, scaleZ);
        matrices.translate(-bone.pivotX * UNIT, -bone.pivotY * UNIT, -bone.pivotZ * UNIT);

        for (Cube cube : bone.cubes) {
            matrices.pushPose();
            matrices.translate(cube.pivotX * UNIT, cube.pivotY * UNIT, cube.pivotZ * UNIT);
            matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(cube.rotX));
            matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cube.rotY));
            matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(cube.rotZ));
            matrices.translate(-cube.pivotX * UNIT, -cube.pivotY * UNIT, -cube.pivotZ * UNIT);

            collector.submitCustomGeometry(matrices,
                    RenderTypes.entityCutout(texture),
                    (pose, consumer) -> emitCube(model, cube, pose,
                            consumer, state.lightCoords));
            matrices.popPose();
        }

        // 手持物品挂点：当前骨骼局部原点即挂点（手部骨骼 pivot 放在手腕）。
        // 手臂定位组（lefthand_pos/righthand_pos）：pivot 在肩膀，偏移到手臂末端（-12 像素）。
        // 物品状态来自 CapiRenderState（所有生物统一支持，不限人形）。
        if (bone.hand != 0 && state instanceof CapiRenderState capi) {
            ItemStackRenderState handState = bone.hand == 1
                    ? capi.capi$getMain() : capi.capi$getOff();
            if (!handState.isEmpty()) {
                matrices.pushPose();
                if (bone.handPos) {
                    // 定位组标准尺寸 x4 y12 z4，末端在 pivot 下方 12 像素
                    matrices.translate(0f, -12f * UNIT, 0f);
                }
                matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
                matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                handState.submit(matrices, collector, state.lightCoords,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        state.outlineColor);
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

    private static void emitCube(BedrockModel model, Cube cube,
                                 PoseStack.Pose pose, VertexConsumer consumer, int light) {
        float i = cube.inflate;
        float x0 = (cube.originX - i) * UNIT;
        float y0 = (cube.originY - i) * UNIT;
        float z0 = (cube.originZ - i) * UNIT;
        float x1 = (cube.originX + cube.sizeX + i) * UNIT;
        float y1 = (cube.originY + cube.sizeY + i) * UNIT;
        float z1 = (cube.originZ + cube.sizeZ + i) * UNIT;

        float w = cube.sizeX + 2f * i;
        float h = cube.sizeY + 2f * i;
        float d = cube.sizeZ + 2f * i;
        float u = cube.uvU, v = cube.uvV;
        float tw = model.textureWidth, th = model.textureHeight;

        // 盒子 UV 各面（像素 → 归一化）
        float[][] box = new float[6][];
        if (!cube.perFace) {
            box[4] = rect(u + d, v, u + d + w, v + d, tw, th);
            box[5] = rect(u + d + w, v, u + d + 2f * w, v + d, tw, th);
            box[0] = rect(u + d, v + d, u + d + w, v + d + h, tw, th);
            box[1] = rect(u + d + w + d, v + d, u + d + 2f * w + d, v + d + h, tw, th);
            box[3] = rect(u, v + d, u + d, v + d + h, tw, th);
            box[2] = rect(u + d + w, v + d, u + d + w + d, v + d + h, tw, th);
            if (cube.mirror) {
                swapU(box[3]); swapU(box[2]);
            }
        }
        // 每面 UV 覆盖
        float[][] uv = new float[6][];
        for (int f = 0; f < 6; f++) {
            if (cube.faceUV[f] != null) {
                float[] p = cube.faceUV[f];
                uv[f] = new float[]{p[0] / tw, p[1] / th, p[2] / tw, p[3] / th};
                if (cube.mirror && (f == 2 || f == 3)) {
                    swapU(uv[f]);
                }
            } else {
                uv[f] = box[f];
            }
        }

        if (uv[4] != null) {
            face(pose, consumer, light, 0f, 1f, 0f,
                    new float[]{x0, y1, z0}, new float[]{x1, y1, z0},
                    new float[]{x1, y1, z1}, new float[]{x0, y1, z1}, uv[4],
                    cube.faceUVRotation[4]);
        }
        if (uv[5] != null) {
            face(pose, consumer, light, 0f, -1f, 0f,
                    new float[]{x0, y0, z1}, new float[]{x1, y0, z1},
                    new float[]{x1, y0, z0}, new float[]{x0, y0, z0}, uv[5],
                    cube.faceUVRotation[5]);
        }
        if (uv[0] != null) {
            face(pose, consumer, light, 0f, 0f, -1f,
                    new float[]{x1, y1, z0}, new float[]{x0, y1, z0},
                    new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, uv[0],
                    cube.faceUVRotation[0]);
        }
        if (uv[1] != null) {
            face(pose, consumer, light, 0f, 0f, 1f,
                    new float[]{x0, y1, z1}, new float[]{x1, y1, z1},
                    new float[]{x1, y0, z1}, new float[]{x0, y0, z1}, uv[1],
                    cube.faceUVRotation[1]);
        }
        if (uv[3] != null) {
            face(pose, consumer, light, -1f, 0f, 0f,
                    new float[]{x0, y1, z0}, new float[]{x0, y1, z1},
                    new float[]{x0, y0, z1}, new float[]{x0, y0, z0}, uv[3],
                    cube.faceUVRotation[3]);
        }
        if (uv[2] != null) {
            face(pose, consumer, light, 1f, 0f, 0f,
                    new float[]{x1, y1, z1}, new float[]{x1, y1, z0},
                    new float[]{x1, y0, z0}, new float[]{x1, y0, z1}, uv[2],
                    cube.faceUVRotation[2]);
        }
    }

    private static float[] rect(float u0, float v0, float u1, float v1,
                                float tw, float th) {
        return new float[]{u0 / tw, v0 / th, u1 / tw, v1 / th};
    }

    private static void swapU(float[] uv) {
        float t = uv[0];
        uv[0] = uv[2];
        uv[2] = t;
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer, int light,
                             float nx, float ny, float nz,
                             float[] c0, float[] c1, float[] c2, float[] c3,
                             float[] uv, int rotation) {
        // 默认四角 UV：0=(u0,v0) 1=(u1,v0) 2=(u1,v1) 3=(u0,v1)。
        float[][] cornerUV = {
                new float[]{uv[0], uv[1]},
                new float[]{uv[2], uv[1]},
                new float[]{uv[2], uv[3]},
                new float[]{uv[0], uv[3]}
        };
        // UV 旋转（与原版 uvlock 同向）：旋转角 → 每角取原角索引。
        int[] perm;
        switch (rotation) {
            case 90:  perm = new int[]{3, 0, 1, 2}; break;
            case 180: perm = new int[]{2, 3, 0, 1}; break;
            case 270: perm = new int[]{1, 2, 3, 0}; break;
            default:  perm = new int[]{0, 1, 2, 3}; break;
        }
        float[][] corners = {c0, c1, c2, c3};
        org.joml.Matrix4f poseMat = pose.pose();
        org.joml.Matrix3f normalMat = pose.normal();
        org.joml.Vector3f tp = new org.joml.Vector3f();
        org.joml.Vector3f tn = new org.joml.Vector3f();
        normalMat.transform(nx, ny, nz, tn);
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            float[] cuv = cornerUV[perm[i]];
            poseMat.transformPosition(c[0], c[1], c[2], tp);
            // 一次性写入所有顶点元素：位置, 颜色(ARGB), UV, overlay, light, 法线
            consumer.addVertex(tp.x, tp.y, tp.z,
                    0xFFFFFFFF,
                    cuv[0], cuv[1],
                    OverlayTexture.NO_OVERLAY,
                    light,
                    tn.x, tn.y, tn.z);
        }
    }

    private BedrockRenderer() {}
}
