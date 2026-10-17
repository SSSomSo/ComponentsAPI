package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.BedrockItemRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品栏（GUI）中的 2D 图标：
 * 若基岩物品的 index 配置了 slot_texture，则直接用该贴图绘制 16x16 图标，
 * 替代原版物品图标（掉落物、手持等仍由各自逻辑处理）。
 *
 * <p>注意：26.2 重构了 GUI 渲染为 GuiRenderState + BlitRenderState（颜色是每个
 * blit 的 ARGB 字段），便捷版 blit(Identifier,x,y,w,h,u0,v0,u1,v1) 内部虽传
 * 0xFFFFFFFF，但在 Sodium 环境下可能未正确初始化颜色字段，导致后续 GUI 图标/
 * 分隔线随机偏色。这里显式调用带颜色参数的 blit 重载，硬编码 0xFFFFFFFF。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiItemMixin {

    /** 白色不透明 ARGB，显式传入以避免 Sodium 环境下颜色字段未初始化。 */
    private static final int WHITE_ARGB = 0xFFFFFFFF;

    @Inject(
            method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void capi$slotIcon(LivingEntity entity, Level level, ItemStack stack,
                               int x, int y, int seed, CallbackInfo ci) {
        String model = BedrockItemRenderer.modelOf(stack);
        if (model == null) {
            return;
        }
        Identifier texture = BedrockItemRenderer.slotTextureFor(model);
        if (texture == null) {
            return;
        }
        GuiGraphicsExtractor gui = (GuiGraphicsExtractor) (Object) this;
        // 显式带颜色的 blit 重载：(pipeline, texture, x, y, u, v, w, h, texW, texH, color)
        gui.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16, WHITE_ARGB);
        ci.cancel();
    }
}
