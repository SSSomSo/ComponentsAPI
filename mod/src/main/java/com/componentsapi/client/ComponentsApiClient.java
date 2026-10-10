package com.componentsapi.client;

import com.componentsapi.client.bedrock.BedrockReloadListener;
import com.componentsapi.client.block.BlockModelRegistry;
import com.componentsapi.client.payload.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ComponentsApiClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ComponentsAPI");

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientState.get().handle(payload.data())));
        BlockModelRegistry.register();
        ReloadableResourceManager resourceManager =
                (ReloadableResourceManager) (Object) net.minecraft.client.Minecraft.getInstance().getResourceManager();
        resourceManager.registerReloadListener(new BedrockReloadListener());
        LOGGER.info("ComponentsAPI 客户端已初始化。作者 Mc_SomSo，CC BY-SA 4.0。");
    }
}
