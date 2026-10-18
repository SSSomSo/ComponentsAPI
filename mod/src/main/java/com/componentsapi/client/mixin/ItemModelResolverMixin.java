package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.BedrockItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
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

/**
 * 附魔光效（glint）总开关。
 *
 * 基岩物品在第一/第三人称手持、GUI 已被自定义渲染替换，本身不产生 glint；
 * 但掉落物、展示框、物品展示框等仍走原版物品模型路径，会显示附魔光效。
 *
 * 在 ItemModelResolver 填充 ItemStackRenderState 之后（三条解析路径都覆盖）：
 * 若物品是基岩物品且 index.enchantment_glint 未开启（默认 false），把所有层
 * foilType 置为 NONE，彻底关闭附魔光效以节省资源。
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    @Inject(method = "updateForLiving", at = @At("TAIL"), require = 0)
    private void capi$stripGlintLiving(ItemStackRenderState state, ItemStack stack,
                                      ItemDisplayContext context, LivingEntity entity,
                                      CallbackInfo ci) {
        capi$stripGlint(state, stack);
    }

    @Inject(method = "updateForNonLiving", at = @At("TAIL"), require = 0)
    private void capi$stripGlintNonLiving(ItemStackRenderState state, ItemStack stack,
                                         ItemDisplayContext context, Entity entity,
                                         CallbackInfo ci) {
        capi$stripGlint(state, stack);
    }

    @Inject(method = "updateForTopItem", at = @At("TAIL"), require = 0)
    private void capi$stripGlintTop(ItemStackRenderState state, ItemStack stack,
                                   ItemDisplayContext context, Level level,
                                   ItemOwner owner, int light, CallbackInfo ci) {
        capi$stripGlint(state, stack);
    }

    private static void capi$stripGlint(ItemStackRenderState state, ItemStack stack) {
        // 自定义附魔颜色：读取 custom_data.sscustomitems.enchantment_glint_color
        // 写入 LayerRenderState.tintLayers 的第 0 位（用负数值标记，避免与原版 tint 冲突）
        Integer customGlint = capi$readCustomGlintColor(stack);

        String model = BedrockItemRenderer.modelOf(stack);
        ItemStackRenderStateAccessor acc = (ItemStackRenderStateAccessor) state;
        int count = acc.capi$getActiveLayerCount();
        ItemStackRenderState.LayerRenderState[] layers = acc.capi$getLayers();
        if (layers == null) {
            return;
        }

        boolean stripGlint = model != null && !BedrockItemRenderer.glintAllowed(model);

        for (int i = 0; i < count && i < layers.length; i++) {
            if (layers[i] == null) {
                continue;
            }
            if (stripGlint) {
                layers[i].setFoilType(FoilType.NONE);
            }
            // 写入自定义附魔颜色标记（负数：-color-1，读取时还原）
            if (customGlint != null) {
                it.unimi.dsi.fastutil.ints.IntList tints = layers[i].tintLayers();
                if (tints != null) {
                    // 用 index 0 存储标记值；若已有 tint 则先暂存
                    tints.add(0, -customGlint - 1);
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
