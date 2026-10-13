package com.componentsapi.client;

import com.componentsapi.client.bedrock.AnimationState;
import com.componentsapi.client.bedrock.BedrockReloadListener;
import com.componentsapi.client.bedrock.KeyBindHandler;
import com.componentsapi.client.block.BlockModelRegistry;
import com.componentsapi.client.payload.AnimationPayload;
import com.componentsapi.client.payload.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ComponentsAPI 客户端入口：
 * 1) 注册 componentsapi:sync 报文并刷新 ClientState；
 * 2) 注册自定义方块模型 ExtraModel 烘焙（assets/capi/models/block/*.json）；
 * 3) 注册资源重载监听器，扫描基岩模型/动画（assets/capi/bedrock/...）。
 */
public final class ComponentsApiClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ComponentsAPI");

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> ClientState.get().handle(payload.data())));

        PayloadTypeRegistry.clientboundPlay().register(AnimationPayload.TYPE, AnimationPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(AnimationPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> AnimationState.get().handlePacket(payload.data())));

        BlockModelRegistry.register();

        // 按键绑定（lc/rc/mc/H 键动画触发）
        KeyBindHandler.register();

        ReloadableResourceManager resourceManager =
                (ReloadableResourceManager) (Object)
                        net.minecraft.client.Minecraft.getInstance().getResourceManager();
        resourceManager.registerReloadListener(new BedrockReloadListener());

        LOGGER.info("ComponentsAPI 客户端已初始化。作者 Mc_SomSo，CC BY-SA 4.0。");
    }
}
