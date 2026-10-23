package com.componentsapi.client.bedrock;

import com.componentsapi.client.bedrock.AnimationIndex.ModelConfig;
import com.componentsapi.client.bedrock.BedrockAnimation.BoneAnimation;
import com.componentsapi.client.bedrock.BedrockModel.Bone;
import com.componentsapi.client.bedrock.BedrockModel.Cube;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Vector3f;

import java.util.Map;

/**
 * 物品基岩模型渲染。
 *
 * <p>物品在 custom_data 中携带 {@code {"capi_bedrock_model":"namespace:name"}}，
 * 客户端在手持渲染时检测该标识，用基岩渲染器渲染整个模型（含骨骼动画、手臂定位组）。
 *
 * <p><b>手臂定位组（1.14.0 重写）</b>：geo 中的 {@code lefthand_pos} /
 * {@code righthand_pos} 定位组，其参考 cube 仅用于 Blockbench 内预览，游戏中
 * <b>不绘制</b>。第一人称下，在该定位组的骨骼坐标系里直接渲染玩家的<b>真实手臂</b>
 * （{@link AvatarRenderer#renderRightHand} / {@code renderLeftHand}），因此玩家的
 * 手臂会完全跟随定位组的骨骼（含动画）移动，而不会改变手部纹理。
 *
 * <p>第三人称：在 {@code ItemInHandLayer} 已定位到手部（含挥动/持物姿势）的
 * PoseStack 中渲染模型，并用 {@link BedrockAnchor} 计算定位组锚点，把模型的"手"
 * 对齐到玩家手部；第三人称不替换手臂（手臂由原版渲染）。
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
     * @param hand      本次渲染的是主手还是副手（决定渲染哪只手臂定位组）
     */
    public static void renderFirstPerson(String modelKey, PoseStack matrices,
                                         SubmitNodeCollector collector, int light,
                                         InteractionHand hand) {
        BedrockModel model = BedrockModelManager.get().model(modelKey);
        if (model == null) {
            return;
        }
        AnimationState state = AnimationState.get();
        ModelConfig cfg = state.getConfig();

        // 把 InteractionHand 换算成左右（兼容左撇子）。
        HumanoidArm mainArm = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getMainArm() : HumanoidArm.RIGHT;
        boolean rightHand = (hand == InteractionHand.MAIN_HAND)
                ? (mainArm == HumanoidArm.RIGHT)
                : (mainArm == HumanoidArm.LEFT);

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

        for (Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverseAnimated(model, bone, matrices, texture, collector, light,
                        modelKey, true, 0f, true, rightHand);
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
        // 用对应手的定位组锚点，把模型的"手"放到坐标系原点（原点=玩家手部，已随手臂挥动）。
        // 模型与手持坐标系同为 Java Y 向下体系，无需整体翻转。
        Vector3f anchor = BedrockAnchor.handAnchor(model, useGlobal ? state : null, rightHand);
        if (anchor != null) {
            matrices.translate(-anchor.x, -anchor.y, -anchor.z);
        }
        matrices.scale(THIRD_PERSON_SCALE, THIRD_PERSON_SCALE, THIRD_PERSON_SCALE);

        for (Bone bone : model.bones) {
            if (bone.parent == null || model.bones.stream()
                    .noneMatch(b -> b.name.equals(bone.parent))) {
                traverseAnimated(model, bone, matrices, texture, collector, light,
                        modelKey, useGlobal, ageInTicks, false, rightHand);
            }
        }
        matrices.popPose();
    }

    /**
     * 遍历骨骼并应用动画后渲染（第一/第三人称共用）。
     *
     * @param firstPerson true=第一人称（定位组渲染玩家手臂）；false=第三人称（定位组跳过）
     * @param rightHand   当前持有的手是否为右手（第一人称用于只渲染对应手的手臂定位组）
     */
    private static void traverseAnimated(BedrockModel model, Bone bone,
                                         PoseStack matrices, Identifier texture,
                                         SubmitNodeCollector collector, int light,
                                         String modelKey, boolean useGlobal, float ageInTicks,
                                         boolean firstPerson, boolean rightHand) {
        // 忽略该骨骼组及其全部子层级（不 pushPose、不渲染）
        if (AnimationIndex.get().isIgnoredBone(modelKey, bone.name)) {
            return;
        }
        matrices.pushPose();
        float aPx = 0f, aPy = 0f, aPz = 0f;
        float aRx = 0f, aRy = 0f, aRz = 0f;
        float sx = 1f, sy = 1f, sz = 1f;
        AnimationState.BoneTransform t = sampleBone(modelKey, bone.name, useGlobal, ageInTicks);
        if (t != null) {
            aPx = t.posX; aPy = t.posY; aPz = t.posZ;
            aRx = t.rotX; aRy = t.rotY; aRz = t.rotZ;
            sx = t.scaleX; sy = t.scaleY; sz = t.scaleZ;
        }
        // 标准骨骼变换：translate(局部 pos) → Z → Y → X → scale（无 pivot 补偿）。
        BedrockRenderer.applyBoneTransform(matrices, bone,
                aPx, aPy, aPz, aRx, aRy, aRz, sx, sy, sz);

        // 手臂定位组：参考 cube 不绘制。
        if (bone.handPos) {
            if (firstPerson) {
                // 只渲染"当前持有的手"对应的定位组：右手物品→右手，左手物品→左手。
                // 避免主手物品把两只手臂都渲染出来。
                boolean isRightArm = bone.handPosArm != 2;
                if (isRightArm == rightHand) {
                    renderPlayerArm(matrices, collector, light, isRightArm);
                }
            }
            // 第三人称直接跳过；定位组通常是叶子骨骼，不继续递归。
            matrices.popPose();
            return;
        }

        for (Cube cube : bone.cubes) {
            matrices.pushPose();
            if (cube.hasRot) {
                matrices.translate(cube.offX, cube.offY, cube.offZ);
                matrices.mulPose(Axis.ZP.rotationDegrees(cube.rotZ));
                matrices.mulPose(Axis.YP.rotationDegrees(cube.rotY));
                matrices.mulPose(Axis.XP.rotationDegrees(cube.rotX));
            }
            collector.submitCustomGeometry(matrices,
                    RenderTypes.itemCutout(texture),
                    (pose, consumer) -> BedrockRenderer.emitCubePublic(model, cube, pose, consumer, light));
            matrices.popPose();
        }

        for (Bone child : model.bones) {
            if (bone.name.equals(child.parent)) {
                traverseAnimated(model, child, matrices, texture, collector, light,
                        modelKey, useGlobal, ageInTicks, firstPerson, rightHand);
            }
        }
        matrices.popPose();
    }

    /**
     * 在当前定位组坐标系渲染玩家真实手臂。
     *
     * <p>定位组的骨骼变换已经把矩阵带到 Blockbench 中的手臂根（肩膀）位置；
     * 基岩模型手臂方向与原版第一人称手臂相差 180° 绕 Z，故先做 ZP(180)。
     * 手臂模型使用玩家自身皮肤，不改变手部纹理。
     */
    private static void renderPlayerArm(PoseStack matrices, SubmitNodeCollector collector,
                                        int light, boolean right) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) {
            return;
        }
        AvatarRenderer<AbstractClientPlayer> renderer =
                mc.getEntityRenderDispatcher().getPlayerRenderer(player);
        if (renderer == null) {
            return;
        }
        Identifier skinTexture = player.getSkin().body().texturePath();
        matrices.pushPose();
        matrices.mulPose(Axis.ZP.rotationDegrees(180f));
        // showSleeve 暂用 false（皮肤外层袖子）；基础手臂内层已完整。
        if (right) {
            renderer.renderRightHand(matrices, collector, light, skinTexture, false);
        } else {
            renderer.renderLeftHand(matrices, collector, light, skinTexture, false);
        }
        // 中和 26.2 AvatarRenderer#renderHand 写入的 leftArm.zRot=-0.1 / rightArm.zRot=+0.1
        // 轻微倾斜（原版让手臂有点外撇），让手臂笔直对齐定位组；两条手臂都清（原版每次调用
        // 会污染两条）。renderRightHand 是向 collector 提交延迟任务，flush 前清 zRot 即可生效。
        PlayerModel playerModel = renderer.getModel();
        if (playerModel != null) {
            playerModel.leftArm.zRot = 0f;
            playerModel.rightArm.zRot = 0f;
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
