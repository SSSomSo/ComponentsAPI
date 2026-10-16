package com.componentsapi.client;

import com.componentsapi.client.bedrock.AnimationState;
import com.componentsapi.client.bedrock.BedrockReloadListener;
import com.componentsapi.client.bedrock.KeyBindHandler;
import com.componentsapi.client.block.BlockModelRegistry;
import com.componentsapi.client.config.CapiConfig;
import com.componentsapi.client.pack.EncryptedPackLoader;
import com.componentsapi.client.pack.PackEncryptor;
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
 * 3) 注册资源重载监听器，扫描基岩模型/动画（assets/capi/...）；
 * 4) 资源包加密：读取 config/capi/config.toml 密钥，扫描 config/capi/rp/*.zip 加密，
 *    并在启动后把模组 jar 内 capi_packs/*.dat 内存解密注入。
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

        // 按键绑定（lc/rc/mc 及 index 中声明的任意键盘键）
        KeyBindHandler.register();

        ReloadableResourceManager resourceManager =
                (ReloadableResourceManager) (Object)
                        net.minecraft.client.Minecraft.getInstance().getResourceManager();
        resourceManager.registerReloadListener(new BedrockReloadListener());

        // 资源包加密配置：读取/生成密钥，扫描 config/capi/rp 下的 zip 加密为 .dat
        CapiConfig config = new CapiConfig();
        boolean configReady = false;
        try {
            config.loadOrCreate();
            int encrypted = PackEncryptor.encryptAll(config);
            if (encrypted > 0) {
                LOGGER.info("[CAPI] 已加密 {} 个资源包，输出到 config/capi/rp/dat/。", encrypted);
            }
            configReady = true;
        } catch (Exception e) {
            LOGGER.error("[CAPI] 加密配置初始化失败，加密资源包功能将不可用。", e);
        }
        if (configReady) {
            // 启动完成后从 jar 内 capi_packs/*.dat 解密并注入（内存，不落盘）
            EncryptedPackLoader.register(config);
        }

        LOGGER.info("ComponentsAPI 客户端已初始化。作者 Mc_SomSo，CC BY-SA 4.0。");
    }
}
