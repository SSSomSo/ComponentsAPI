package com.componentsapi.client.block;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockModelRegistry {

    private static final Map<String, ExtraModelKey<BlockStateModel>> KEYS = new ConcurrentHashMap<>();

    public static void register() {
        PreparableModelLoadingPlugin.register(
                (sharedState, executor) -> java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> scan(sharedState.resourceManager()), executor),
                BlockModelRegistry::initialize);
    }

    private static void initialize(Set<Identifier> modelIds, ModelLoadingPlugin.Context context) {
        KEYS.clear();
        for (Identifier modelId : modelIds) {
            ExtraModelKey<BlockStateModel> key = ExtraModelKey.create();
            KEYS.put(modelId.toString(), key);
            context.addModel(key, SimpleUnbakedExtraModel.blockStateModel(modelId));
        }
    }

    private static Set<Identifier> scan(ResourceManager resourceManager) {
        Map<Identifier, List<Resource>> files = resourceManager.listResourceStacks(
                "models/block", id -> "capi".equals(id.getNamespace()) && id.getPath().endsWith(".json"));
        Set<Identifier> modelIds = new HashSet<>();
        for (Identifier resourceId : files.keySet()) {
            String path = resourceId.getPath();
            String modelPath = path.substring(0, path.length() - ".json".length());
            modelIds.add(Identifier.fromNamespaceAndPath("capi", modelPath));
        }
        return modelIds;
    }

    public static BlockStateModel get(String modelId) {
        if (modelId == null) return null;
        ExtraModelKey<BlockStateModel> key = KEYS.get(modelId);
        if (key == null) return null;
        FabricModelManager modelManager = (FabricModelManager) (Object) Minecraft.getInstance().getModelManager();
        return modelManager.getModel(key);
    }

    private BlockModelRegistry() {}
}
