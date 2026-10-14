package com.componentsapi.client.mixin;

import com.componentsapi.client.bedrock.BedrockItemRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiItemMixin {

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
        // 整张 16x16 贴图，UV 覆盖 0..1
        gui.blit(texture, x, y, 16, 16, 0f, 0f, 1f, 1f);
        ci.cancel();
    }
}
