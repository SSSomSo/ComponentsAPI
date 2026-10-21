package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.BedrockItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔光效（glint）总开关 + 自定义附魔颜色传递。
 *
 * 基岩物品在第一/第三人称手持、GUI 已被自定义渲染替换，本身不产生 glint；
 * 但掉落物、展示框等仍走原版物品模型路径，会显示附魔光效。
 *
 * 在 ItemModelResolver 填充 ItemStackRenderState 之后（三条解析路径都覆盖）：
 * 1. 若物品是基岩物品且 index.enchantment_glint 未开启（默认 false），把所有层
 *    foilType 置为 NONE，彻底关闭附魔光效以节省资源。
 * 2. 若物品配置了 custom_data.sscustomitems.enchantment_glint_color，将颜色存入
 *    静态 Map（key = LayerRenderState.quads 的 identityHashCode），供
 *    ItemFeatureRendererMixin 在 prepareFoilSubmit 中读取。
 *
 * 注意：**不要**将颜色写入 tintLayers！prepareMainSubmit 会用
 * getLayerColorSafe(tints, materialInfo) 读取 tintLayers[tintLayer] 作为物品
 * 模型颜色，负数标记会被当成 tint 颜色导致物品模型偏色（1.12.0 踩过的坑）。
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    /**
     * 自定义附魔颜色传递表。
     * key = System.identityHashCode(LayerRenderState.quads())
     * value = 打包的 ARGB int（alpha=FF）
     * 由 ItemFeatureRendererMixin.prepareFoilSubmit 读取并使用。
     */
    private static final Map<Integer, Integer> CUSTOM_GLINT_COLORS = new HashMap<>();

    /** 供 ItemFeatureRendererMixin 查询自定义附魔颜色。 */
    public static Integer getCustomGlintColor(int quadsIdentityHash) {
        return CUSTOM_GLINT_COLORS.get(quadsIdentityHash);
    }

    @Inject(method = "updateForLiving", at = @At("TAIL"), require = 0)
    private void capi$stripGlintLiving(ItemStackRenderState state, ItemStack stack,
                                      ItemDisplayContext context, LivingEntity entity,
                                      CallbackInfo ci) {
        capi$process(state, stack);
    }

    @Inject(method = "updateForNonLiving", at = @At("TAIL"), require = 0)
    private void capi$stripGlintNonLiving(ItemStackRenderState state, ItemStack stack,
                                         ItemDisplayContext context, Entity entity,
                                         CallbackInfo ci) {
        capi$process(state, stack);
    }

    @Inject(method = "updateForTopItem", at = @At("TAIL"), require = 0)
    private void capi$stripGlintTop(ItemStackRenderState state, ItemStack stack,
                                   ItemDisplayContext context, Level level,
                                   ItemOwner owner, int light, CallbackInfo ci) {
        capi$process(state, stack);
    }

    private static void capi$process(ItemStackRenderState state, ItemStack stack) {
        String model = BedrockItemRenderer.modelOf(stack);
        ItemStackRenderStateAccessor acc = (ItemStackRenderStateAccessor) state;
        int count = acc.capi$getActiveLayerCount();
        LayerRenderState[] layers = acc.capi$getLayers();
        if (layers == null) {
            return;
        }

        // 读取自定义附魔颜色（所有层共用同一个颜色）
        Integer customGlint = capi$readCustomGlintColor(stack);

        boolean stripGlint = model != null && !BedrockItemRenderer.glintAllowed(model);

        for (int i = 0; i < count && i < layers.length; i++) {
            LayerRenderState layer = layers[i];
            if (layer == null) {
                continue;
            }
            if (stripGlint) {
                layer.setFoilType(FoilType.NONE);
            }
            // 用 quads 列表的 identityHashCode 作为 key 传递自定义颜色
            if (customGlint != null) {
                List<BakedQuad> quads = ((LayerRenderStateAccessor) layer).capi$getQuads();
                if (quads != null) {
                    CUSTOM_GLINT_COLORS.put(System.identityHashCode(quads), customGlint);
                }
            }
        }
    }

    /**
     * 读取物品 custom_data.sscustomitems.enchantment_glint_color（#RRGGBB 或 0xRRGGBB）。
     * 返回打包的 ARGB int（alpha=FF），无配置返回 null。
     */
    private static Integer capi$readCustomGlintColor(ItemStack stack) {
        try {
            net.minecraft.world.item.component.CustomData cd = stack.get(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (cd == null) {
                return null;
            }
            net.minecraft.nbt.CompoundTag tag = cd.copyTag();
            if (!tag.contains("sscustomitems")) {
                return null;
            }
            net.minecraft.nbt.Tag ssciTag = tag.get("sscustomitems");
            if (!(ssciTag instanceof net.minecraft.nbt.CompoundTag ssci)) {
                return null;
            }
            if (!ssci.contains("enchantment_glint_color")) {
                return null;
            }
            String colorStr = ssci.getString("enchantment_glint_color").orElse(null);
            if (colorStr == null || colorStr.isBlank()) {
                return null;
            }
            colorStr = colorStr.trim();
            int rgb;
            if (colorStr.startsWith("#")) {
                rgb = Integer.parseInt(colorStr.substring(1), 16);
            } else if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                rgb = Integer.parseInt(colorStr.substring(2), 16);
            } else {
                rgb = Integer.parseInt(colorStr, 16);
            }
            return 0xFF000000 | (rgb & 0xFFFFFF);
        } catch (Exception e) {
            return null;
        }
    }
}
