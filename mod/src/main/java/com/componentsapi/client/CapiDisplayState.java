package com.componentsapi.client;

/** 展示实体（Display）渲染状态的组件模型存取接口，由 Mixin 实现。 */
public interface CapiDisplayState {

    String capi$getModel();

    void capi$setModel(String model);
}
