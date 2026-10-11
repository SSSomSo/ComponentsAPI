package com.componentsapi.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * 由 Mixin 注入到 LivingEntityRenderState 的自定义字段访问接口。
 * model：capi:entity_model 组件内容（如 "bedrock/example"），null 表示无覆盖。
 * main/off：主手 / 副手物品的渲染状态，在 extractRenderState 时统一填充，
 *           使非人形生物（基岩模型）也能渲染手持物品与手持方块。
 */
public interface CapiRenderState {

    String capi$getModel();

    void capi$setModel(String model);

    ItemStackRenderState capi$getMain();

    ItemStackRenderState capi$getOff();
}
