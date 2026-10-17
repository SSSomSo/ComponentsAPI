package com.componentsapi.client.bedrock;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * 资源重载监听器：每次资源包（含服务端下发资源包）重载后重新扫描基岩模型与动画。
 * 实现 IdentifiableResourceReloadListener 以适配新版 Fabric ResourceManagerHelper。
 */
public final class BedrockReloadListener extends SimplePreparableReloadListener<Void>
        implements IdentifiableResourceReloadListener {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("componentsapi", "bedrock_reload");

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        BedrockModelManager.get().load(resourceManager);
        AnimationIndex.get().load(resourceManager);
    }
}
