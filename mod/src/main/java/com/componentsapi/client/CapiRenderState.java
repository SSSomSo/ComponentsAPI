package com.componentsapi.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;

public interface CapiRenderState {
    String capi$getModel();
    void capi$setModel(String model);
    ItemStackRenderState capi$getMain();
    ItemStackRenderState capi$getOff();
}
