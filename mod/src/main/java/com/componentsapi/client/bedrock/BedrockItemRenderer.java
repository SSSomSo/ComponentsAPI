package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.AnimationIndex.ModelConfig;
import com.componentsapi.client.bedrock.BedrockAnimation.BoneAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Vector3f;

import java.util.Map;

/**
 * 物品基岩模型渲染：
 * 物品在 custom_data 中携带 {"capi_bedrock_model": "namespace:name"}，
 * 客户端在手持渲染时检测该标识，用基岩渲染器渲染整个模型（含骨骼动画、手臂定位组）。
 *
 * 第三人称：通过 Mixin 重定向 ItemInHandLayer.submitArmWithItem 内的 ItemStackRenderState.submit，
 * 此时 PoseStack 已包含 translateToHand（手部定位）+ 攻击挥动(attackTime) + 持物姿势(armPose)，
 * 因此模型会自然跟随手臂摆动；模型再用 righthand_pos/lefthand_pos 定位组把"手"锚定到坐标系原点。
 */
public final class BedrockItemRenderer {

    private static final float UNIT = 1f / 16f;
    /** 第三人称手持整体缩放（可按手感调整）。 */
    private static final float THIRD_PERSON_SCALE = 0.7f;
    /** 第一人称手持整体缩放。 */
    private static final float FIRST_PERSON_SCALE = 0.6f;

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

    /** 解析模型贴图：优先 index.texture，否则用默认 textures/geo_models/<name>.png。 */
    public static Identifier textureFor(String modelKey) {
        AnimationIndex.ModelConfig cfg = AnimationIndex.get().get(modelKey);
        if (cfg != null && cfg.modelTexture != null && !cfg.modelTexture.isBlank()) {
            return parseId(cfg.modelTexture);
        }
        return BedrockModelManager.texture(modelKey);
    }

    /** 解析 index 里声明的 slot 图标贴图，null=未配置。 */
    public static Identifier slotTextureFor(String modelKey) {
        AnimationIndex.ModelConfig cfg = AnimationIndex.get().get(modelKey);
        if (cfg != null && cfg.slotTexture != null && !cfg.slotTexture.isBlank()) {
            return parseId(cfg.slotTexture);
        }
        return null;
    }

    /** index 是否允许附魔光效（默认 false，节省资源）。 */
    public static boolean glintAllowed(String modelKey) {
        AnimationIndex.ModelConfig cfg = AnimationIndex.get().get(modelKey);
        return cfg != null && cfg.enchantmentGlint;
    }

    /** 解析 "namespace:path" 形式的标识。 */
    private static Identifier parseId(String s) {
        int colon = s.indexOf(':');
        if (colon > 0) {
            return Identifier.fromNamespaceAndPath(s.substring(0, colon), s.substring(colon + 1));
        }
        return Identifier.fromNamespaceAndPath("capi", s);
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

        Identifier texture = textureFor(modelKey);

        matrices.pushPose();
        // 默认第一人称偏移（右下前，模拟原版），可由 index 配置
        if (cfg == null || cfg.fpEnabled) {
            float ox = cfg != null ? cfg.fpX : 0.56f;
            float oy = cfg != null ? cfg.fpY : -0.52f;
            float oz = cfg != null ? cfg.fpZ : 0.72f;
            matrices.translate(ox, oy, oz);
        }
        matrices.scale(FIRST_PERSON_SCALE, FIRST_PERSON_SCALE, FIRST_PERSON_SCALE);

        for (BedrockModel.Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverseAnimated(model, bone, matrices, texture, collector, light,
                        modelKey, true, 0f);
            }
        }
        matrices.popPose();
    }

    /**
     * 第三人称手持基岩模型。在已定位到手部（含挥动/持物姿势）的 PoseStack 中渲染。
     *
     * @param modelKey  模型 id
     * @param matrices  手持坐标系（ItemInHandLayer.submitArmWithItem 提交物品时的 PoseStack）
     * @param collector 提交器
     * @param light     光照
     * @param rightHand 是否右手（决定 righthand_pos/lefthand_pos 锚点）
     * @param ageInTicks 渲染状态年龄（用于其他玩家/生物播放默认动画）
     */
    public static void renderThirdPerson(String modelKey, PoseStack matrices,
                                         SubmitNodeCollector collector, int light,
                                         boolean rightHand, float ageInTicks) {
        BedrockModel model = BedrockModelManager.get().model(modelKey);
        if (model == null) {
            return;
        }
        Identifier texture = textureFor(modelKey);
        AnimationState state = AnimationState.get();
        // 本地玩家主手时使用全局按键动画；其他玩家/生物只播放默认动画
        boolean useGlobal = state.getHandModel() != null && state.getHandModel().equals(modelKey);

        matrices.pushPose();
        // 原版手持坐标系已对物品做 X-90 平躺；补偿 +90 让基岩模型保持 y-up 原生朝向（手持定位组对齐）
        matrices.mulPose(Axis.XP.rotationDegrees(90f));
        // 用对应手的定位组锚点，把模型的"手"放到坐标系原点（原点=玩家手部，已随手臂挥动）
        Vector3f anchor = BedrockAnchor.handAnchor(model, useGlobal ? state : null, rightHand);
        if (anchor != null) {
            matrices.translate(-anchor.x, -anchor.y, -anchor.z);
        }
        matrices.scale(THIRD_PERSON_SCALE, THIRD_PERSON_SCALE, THIRD_PERSON_SCALE);

        for (BedrockModel.Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverseAnimated(model, bone, matrices, texture, collector, light,
                        modelKey, useGlobal, ageInTicks);
            }
        }
        matrices.popPose();
    }

    /** 遍历骨骼并应用动画后渲染（第一/第三人称共用）。 */
    private static void traverseAnimated(BedrockModel model, BedrockModel.Bone bone,
                                         PoseStack matrices, Identifier texture,
                                         SubmitNodeCollector collector, int light,
                                         String modelKey, boolean useGlobal, float ageInTicks) {
        // 忽略该骨骼组及其全部子层级（不 pushPose、不渲染）
        if (AnimationIndex.get().isIgnoredBone(modelKey, bone.name)) {
            return;
        }
        matrices.pushPose();
        float px = bone.pivotX, py = bone.pivotY, pz = bone.pivotZ;
        float rx = bone.rotX, ry = bone.rotY, rz = bone.rotZ;
        float sx = 1f, sy = 1f, sz = 1f;
        AnimationState.BoneTransform t = sampleBone(modelKey, bone.name, useGlobal, ageInTicks);
        if (t != null) {
            px += t.posX; py += t.posY; pz += t.posZ;
            rx += t.rotX; ry += t.rotY; rz += t.rotZ;
            sx = t.scaleX; sy = t.scaleY; sz = t.scaleZ;
        }

        matrices.translate(px * UNIT, py * UNIT, pz * UNIT);
        matrices.mulPose(Axis.XP.rotationDegrees(rx));
        matrices.mulPose(Axis.YP.rotationDegrees(ry));
        matrices.mulPose(Axis.ZP.rotationDegrees(rz));
        matrices.scale(sx, sy, sz);
        matrices.translate(-bone.pivotX * UNIT, -bone.pivotY * UNIT, -bone.pivotZ * UNIT);

        for (BedrockModel.Cube cube : bone.cubes) {
            matrices.pushPose();
            matrices.translate(cube.pivotX * UNIT, cube.pivotY * UNIT, cube.pivotZ * UNIT);
            matrices.mulPose(Axis.XP.rotationDegrees(cube.rotX));
            matrices.mulPose(Axis.YP.rotationDegrees(cube.rotY));
            matrices.mulPose(Axis.ZP.rotationDegrees(cube.rotZ));
            matrices.translate(-cube.pivotX * UNIT, -cube.pivotY * UNIT, -cube.pivotZ * UNIT);
            collector.submitCustomGeometry(matrices,
                    RenderTypes.itemCutout(texture),
                    (pose, consumer) -> BedrockRenderer.emitCubePublic(model, cube, pose, consumer, light));
            matrices.popPose();
        }

        for (BedrockModel.Bone child : model.bones) {
            if (bone.name.equals(child.parent)) {
                traverseAnimated(model, child, matrices, texture, collector, light,
                        modelKey, useGlobal, ageInTicks);
            }
        }
        matrices.popPose();
    }

    /**
     * 采样某骨骼当前动画变换。
     * 本地玩家用全局 AnimationState（按键/切换动画）；其他玩家用 ageInTicks 播放默认/idle 动画。
     */
    private static AnimationState.BoneTransform sampleBone(String modelKey, String boneName,
                                                           boolean useGlobal, float ageInTicks) {
        if (useGlobal) {
            AnimationState state = AnimationState.get();
            if (state.isPlaying()) {
                return state.sample(boneName);
            }
            return null;
        }
        // 其他玩家/生物：默认动画（循环）
        BedrockAnimation anim = defaultAnimation(modelKey);
        if (anim == null) {
            return null;
        }
        BoneAnimation channel = anim.bones.get(boneName);
        if (channel == null) {
            return null;
        }
        float animTime = (ageInTicks / 20f) % anim.length;
        float[] pos = BedrockAnimation.sample(channel.position, animTime, new float[]{0, 0, 0});
        float[] rot = BedrockAnimation.sample(channel.rotation, animTime, new float[]{0, 0, 0});
        float[] scale = BedrockAnimation.sample(channel.scale, animTime, new float[]{1, 1, 1});
        return new AnimationState.BoneTransform(pos[0], pos[1], pos[2],
                rot[0], rot[1], rot[2], scale[0], scale[1], scale[2]);
    }

    /** 缓存默认动画查找结果（按 modelKey）。 */
    private static BedrockAnimation defaultAnimation(String modelKey) {
        Map<String, BedrockAnimation> set = BedrockModelManager.get().animationsForModel(modelKey);
        if (set == null) {
            return null;
        }
        String name = AnimationIndex.get().defaultAnimation(modelKey);
        if (name != null) {
            BedrockAnimation a = BedrockModelManager.findAnimation(set, name);
            if (a != null) {
                return a;
            }
        }
        for (BedrockAnimation a : set.values()) {
            if (a.name.toLowerCase().contains("idle")) {
                return a;
            }
        }
        return null;
    }

    private BedrockItemRenderer() {}
}
