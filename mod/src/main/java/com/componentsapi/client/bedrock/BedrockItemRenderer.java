package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.AnimationIndex.ModelConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 物品基岩模型渲染：
 * 物品在 custom_data 中携带 {"capi_bedrock_model": "namespace:name"}，
 * 客户端在手持渲染时检测该标识，用基岩渲染器渲染整个模型（含骨骼动画、手持挂点）。
 */
public final class BedrockItemRenderer {

    private static final float UNIT = 1f / 16f;

    /** 从物品 custom_data 读取 capi_bedrock_model，null=无。 */
    public static String modelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                return null;
            }
            CompoundTag tag = customData.copyTag();
            if (tag != null && tag.contains("capi_bedrock_model")) {
                return tag.getString("capi_bedrock_model").orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 第一人称渲染基岩物品模型。
     *
     * @param modelKey  模型 id（namespace:name）
     * @param matrices  第一人称 PoseStack
     * @param collector 提交器
     * @param light     光照
     */
    public static void renderFirstPerson(String modelKey, PoseStack matrices,
                                         SubmitNodeCollector collector, int light) {
        BedrockModel model = BedrockModelManager.get().model(modelKey);
        if (model == null) {
            return;
        }
        AnimationState state = AnimationState.get();
        ModelConfig cfg = state.getConfig();

        Identifier texture = BedrockModelManager.texture(modelKey);

        matrices.pushPose();
        // 默认第一人称偏移（右下前，模拟原版），可由 index 配置
        if (cfg == null || cfg.fpEnabled) {
            float ox = cfg != null ? cfg.fpX : 0.56f;
            float oy = cfg != null ? cfg.fpY : -0.52f;
            float oz = cfg != null ? cfg.fpZ : 0.72f;
            matrices.translate(ox, oy, oz);
        }
        // 缩放到手持尺寸（基岩模型按实体大小，手持通常缩小到约 0.6）
        matrices.scale(0.6f, 0.6f, 0.6f);

        // 遍历骨骼渲染（应用第一人称动画）
        for (BedrockModel.Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverseFirstPerson(model, bone, matrices, texture, collector, light, modelKey);
            }
        }
        matrices.popPose();
    }

    /** 第三人称手持基岩模型（挂在手部位置）。 */
    public static void renderThirdPerson(String modelKey, PoseStack matrices,
                                         SubmitNodeCollector collector, int light,
                                         boolean mainHand) {
        BedrockModel model = BedrockModelManager.get().model(modelKey);
        if (model == null) {
            return;
        }
        Identifier texture = BedrockModelManager.texture(modelKey);

        matrices.pushPose();
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        matrices.scale(0.7f, 0.7f, 0.7f);

        for (BedrockModel.Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverseThirdPerson(model, bone, matrices, texture, collector, light);
            }
        }
        matrices.popPose();
    }

    private static void traverseFirstPerson(BedrockModel model, BedrockModel.Bone bone,
                                            PoseStack matrices, Identifier texture,
                                            SubmitNodeCollector collector, int light,
                                            String modelKey) {
        matrices.pushPose();
        // 第一人称动画：尝试多种骨骼命名
        AnimationState.BoneTransform t = null;
        if (AnimationState.get().isPlaying()) {
            for (String candidate : boneCandidates(bone.name)) {
                t = AnimationState.get().sample(candidate);
                if (t != null) break;
            }
        }
        float px = bone.pivotX, py = bone.pivotY, pz = bone.pivotZ;
        float rx = bone.rotX, ry = bone.rotY, rz = bone.rotZ;
        float sx = 1f, sy = 1f, sz = 1f;
        if (t != null) {
            px += t.posX; py += t.posY; pz += t.posZ;
            rx += t.rotX; ry += t.rotY; rz += t.rotZ;
            sx = t.scaleX; sy = t.scaleY; sz = t.scaleZ;
        }

        matrices.translate(px * UNIT, py * UNIT, pz * UNIT);
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rx));
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(ry));
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rz));
        matrices.scale(sx, sy, sz);
        matrices.translate(-bone.pivotX * UNIT, -bone.pivotY * UNIT, -bone.pivotZ * UNIT);

        for (BedrockModel.Cube cube : bone.cubes) {
            matrices.pushPose();
            matrices.translate(cube.pivotX * UNIT, cube.pivotY * UNIT, cube.pivotZ * UNIT);
            matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(cube.rotX));
            matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cube.rotY));
            matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(cube.rotZ));
            matrices.translate(-cube.pivotX * UNIT, -cube.pivotY * UNIT, -cube.pivotZ * UNIT);
            collector.submitCustomGeometry(matrices,
                    RenderTypes.entityCutout(texture),
                    (pose, consumer) -> BedrockRenderer.emitCubePublic(model, cube, pose, consumer, light));
            matrices.popPose();
        }

        for (BedrockModel.Bone child : model.bones) {
            if (bone.name.equals(child.parent)) {
                traverseFirstPerson(model, child, matrices, texture, collector, light, modelKey);
            }
        }
        matrices.popPose();
    }

    private static void traverseThirdPerson(BedrockModel model, BedrockModel.Bone bone,
                                            PoseStack matrices, Identifier texture,
                                            SubmitNodeCollector collector, int light) {
        matrices.pushPose();
        matrices.translate(bone.pivotX * UNIT, bone.pivotY * UNIT, bone.pivotZ * UNIT);
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(bone.rotX));
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bone.rotY));
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bone.rotZ));
        matrices.translate(-bone.pivotX * UNIT, -bone.pivotY * UNIT, -bone.pivotZ * UNIT);

        for (BedrockModel.Cube cube : bone.cubes) {
            matrices.pushPose();
            matrices.translate(cube.pivotX * UNIT, cube.pivotY * UNIT, cube.pivotZ * UNIT);
            matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(cube.rotX));
            matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cube.rotY));
            matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(cube.rotZ));
            matrices.translate(-cube.pivotX * UNIT, -cube.pivotY * UNIT, -cube.pivotZ * UNIT);
            collector.submitCustomGeometry(matrices,
                    RenderTypes.entityCutout(texture),
                    (pose, consumer) -> BedrockRenderer.emitCubePublic(model, cube, pose, consumer, light));
            matrices.popPose();
        }

        for (BedrockModel.Bone child : model.bones) {
            if (bone.name.equals(child.parent)) {
                traverseThirdPerson(model, child, matrices, texture, collector, light);
            }
        }
        matrices.popPose();
    }

    /** 为骨骼名生成候选名（兼容多种命名）。 */
    private static String[] boneCandidates(String name) {
        String n = name.toLowerCase();
        return new String[]{name, n};
    }

    private BedrockItemRenderer() {}
}
