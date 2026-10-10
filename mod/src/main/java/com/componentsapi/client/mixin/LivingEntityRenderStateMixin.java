package com.componentsapi.client.mixin;

import com.componentsapi.client.CapiRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements CapiRenderState {
    @Unique private String capi$model;
    @Unique private ItemStackRenderState capi$main;
    @Unique private ItemStackRenderState capi$off;

    @Override public String capi$getModel() { return capi$model; }
    @Override public void capi$setModel(String model) { this.capi$model = model; }
    @Override public ItemStackRenderState capi$getMain() { if (capi$main == null) capi$main = new ItemStackRenderState(); return capi$main; }
    @Override public ItemStackRenderState capi$getOff() { if (capi$off == null) capi$off = new ItemStackRenderState(); return capi$off; }
}
