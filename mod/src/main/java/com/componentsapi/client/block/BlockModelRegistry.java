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

/**
 * 自定义方块模型注册表。
 *
 * 资源约定：资源包内 assets/capi/models/block/&lt;name&gt;.json（原版方块模型格式）。
 * 模型标识为 capi:block/&lt;name&gt;，即 capi:block_model 组件要填写的字符串。
 *
 * 资源加载准备阶段扫描模型文件，通过 Fabric model-loading-api 的 ExtraModel
 * 机制参与官方烘焙；烘焙完成后按标识取回 BlockStateModel。
 */
public final class BlockModelRegistry {

    /** 模型标识 → ExtraModelKey（每次资源重载重建）。 */
    private static final Map<String, ExtraModelKey<BlockStateModel>> KEYS = new ConcurrentHashMap<>();

    public static void register() {
        PreparableModelLoadingPlugin.register(
                (sharedState, executor) ->
                        java.util.concurrent.CompletableFuture.supplyAsync(
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

    /** 扫描 assets/capi/models/block/*.json，返回模型标识 capi:block/<name>。 */
    private static Set<Identifier> scan(ResourceManager resourceManager) {
        Map<Identifier, List<Resource>> files = resourceManager.listResourceStacks(
                "models/block",
                id -> "capi".equals(id.getNamespace())
                        && id.getPath().endsWith(".json"));
        Set<Identifier> modelIds = new HashSet<>();
        for (Identifier resourceId : files.keySet()) {
            String path = resourceId.getPath(); // models/block/<name>.json
            String modelPath = path.substring(0, path.length() - ".json".length());
            modelIds.add(Identifier.fromNamespaceAndPath("capi", modelPath));
        }
        return modelIds;
    }

    /** 取已烘焙的方块模型；不存在或尚未烘焙完成时返回 null。 */
    public static BlockStateModel get(String modelId) {
        if (modelId == null) {
            return null;
        }
        ExtraModelKey<BlockStateModel> key = KEYS.get(modelId);
        if (key == null) {
            return null;
        }
        FabricModelManager modelManager =
                (FabricModelManager) (Object) Minecraft.getInstance().getModelManager();
        return modelManager.getModel(key);
    }

    private BlockModelRegistry() {}
}
