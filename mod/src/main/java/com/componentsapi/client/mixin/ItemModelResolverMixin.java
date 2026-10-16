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
        String model = BedrockItemRenderer.modelOf(stack);
        if (model == null) {
            return;
        }
        // index 显式开启附魔光效则保留
        if (BedrockItemRenderer.glintAllowed(model)) {
            return;
        }
        ItemStackRenderStateAccessor acc = (ItemStackRenderStateAccessor) state;
        int count = acc.capi$getActiveLayerCount();
        ItemStackRenderState.LayerRenderState[] layers = acc.capi$getLayers();
        if (layers == null) {
            return;
        }
        for (int i = 0; i < count && i < layers.length; i++) {
            if (layers[i] != null) {
                layers[i].setFoilType(FoilType.NONE);
            }
        }
    }
}
