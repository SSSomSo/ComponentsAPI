package com.componentsapi.nms;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NMS 全反射桥接：
 * 1) 方块实体 NBT 中 capi 复合标签的读写（因此支持 /data merge block）；
 * 2) 直接写 StarLight 的方块光照 nibble（Paper 26.2 使用 StarLight/Moonrise）。
 *
 * 序列化体系兼容（saveMode）：
 *   新版（Paper 26.2 build 134 及以后，Mojang 改用 ValueInput/ValueOutput 体系）：
 *     保存 BlockEntity.saveWithFullMetadata(HolderLookup.Provider) -> CompoundTag
 *     加载 BlockEntity.loadWithComponents(ValueInput)，其中 ValueInput 由
 *       TagValueInput.create(ProblemReporter.DISCARDING, provider, compound) 构造
 *   旧版（build 12x 等）：
 *     保存 saveWithFullMetadata(CompoundTag) / saveWithFullMetadata() / save(CompoundTag)
 *     加载 loadWithComponents(CompoundTag, Provider) / load(CompoundTag, Provider)
 */
public final class NmsBridge {

    /** 新版序列化：saveWithFullMetadata(Provider) + loadWithComponents(ValueInput)。 */
    private static final int MODE_NEW_PROVIDER = 0;
    /** 旧版序列化：saveXxx(CompoundTag)。 */
    private static final int MODE_TAG_PARAM = 1;
    /** 旧版序列化：saveXxx() 无参。 */
    private static final int MODE_NOARG = 2;

    private final Method craftWorldGetHandle;
    private final Constructor<?> blockPosCtor;
    private final Constructor<?> compoundCtor;
    private final Method getBlockEntity;
    private final Method saveFull;
    private final int saveMode;
    private final Method registryAccess;
    private final Method tagContains;
    private final Method tagGetCompound;
    private final Method tagPut;
    private final Method tagKeySet;
    private final Method tagGet;
    // 任意 SNBT 值解析（build134 起只有 parseCompoundFully，旧版为 parseTag）
    private final Method parseCompoundFully;
    private final Method parseTag;
    // 新版加载
    private final Method tviCreate;
    private final Object discardingReporter;
    private final Method newLoadWithComponents;
    // 旧版加载
    private final Method oldLoadWithComponents;
    private final Method oldLoad;
    private final Method getChunk;
    private final Method getBlockNibbles;
    private final Method getSkyNibbles;
    private final Constructor<?> swmrCtor;
    private final Method swmrSet;
    private final Method swmrUpdateVisible;
    private final Method getMinSection;
    private final Method getLightEngine;
    private final Method starlightGetInterface;
    private final Method checkBlockEdges;
    private final Method checkChunkEdges;
    private final Method propagateChanges;

    public NmsBridge() {
        try {
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            craftWorldGetHandle = craftWorld.getMethod("getHandle");

            Class<?> blockPos = Class.forName("net.minecraft.core.BlockPos");
            blockPosCtor = blockPos.getConstructor(int.class, int.class, int.class);

            Class<?> compound = Class.forName("net.minecraft.nbt.CompoundTag");
            compoundCtor = compound.getConstructor();
            tagContains = compound.getMethod("contains", String.class);
            tagGetCompound = compound.getMethod("getCompound", String.class);
            tagPut = compound.getMethod("put", String.class,
                    Class.forName("net.minecraft.nbt.Tag"));
            tagKeySet = compound.getMethod("keySet");
            tagGet = compound.getMethod("get", String.class);

            Class<?> blockEntity = Class.forName("net.minecraft.world.level.block.entity.BlockEntity");

            // registry provider 类型：优先 HolderLookup$Provider（saveWithFullMetadata 的形参声明），
            // RegistryAccess 是其子接口；极旧版本回退 RegistryAccess。
            Class<?> providerClass;
            try {
                providerClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
            } catch (ClassNotFoundException e) {
                providerClass = Class.forName("net.minecraft.core.RegistryAccess");
            }

            // 1) 新版（build134+）：saveWithFullMetadata(HolderLookup.Provider)
            Method newSave = findMethod(blockEntity, "saveWithFullMetadata", providerClass);
            if (newSave != null) {
                saveMode = MODE_NEW_PROVIDER;
                saveFull = newSave;
                oldLoadWithComponents = null;
                oldLoad = null;

                Class<?> valueInputClass = Class.forName("net.minecraft.world.level.storage.ValueInput");
                newLoadWithComponents = blockEntity.getMethod("loadWithComponents", valueInputClass);
                Class<?> tagValueInputClass = Class.forName("net.minecraft.world.level.storage.TagValueInput");
                Class<?> problemReporterClass = Class.forName("net.minecraft.util.ProblemReporter");
                tviCreate = tagValueInputClass.getMethod("create",
                        problemReporterClass, providerClass, compound);
                discardingReporter = problemReporterClass.getField("DISCARDING").get(null);
            } else {
                // 2) 旧版：按优先级尝试，取第一个存在的保存方法。
                Method sf = findMethod(blockEntity, "saveWithFullMetadata", compound);
                if (sf != null) {
                    saveMode = MODE_TAG_PARAM;
                } else {
                    sf = findMethod(blockEntity, "saveWithFullMetadata");
                    if (sf != null) {
                        saveMode = MODE_NOARG;
                    } else {
                        sf = findMethod(blockEntity, "save", compound);
                        if (sf == null) {
                            throw new NoSuchMethodException(
                                    "BlockEntity 中找不到 saveWithFullMetadata(Provider)/saveWithFullMetadata(CompoundTag)/saveWithFullMetadata()/save(CompoundTag)");
                        }
                        saveMode = MODE_TAG_PARAM;
                    }
                }
                saveFull = sf;
                tviCreate = null;
                discardingReporter = null;
                newLoadWithComponents = null;
                oldLoadWithComponents = findMethod(blockEntity, "loadWithComponents", compound, providerClass);
                oldLoad = findMethod(blockEntity, "load", compound, providerClass);
                if (oldLoadWithComponents == null && oldLoad == null) {
                    throw new NoSuchMethodException("BlockEntity 中找不到 loadWithComponents/load");
                }
            }

            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            getBlockEntity = level.getMethod("getBlockEntity", blockPos);
            getChunk = level.getMethod("getChunk", int.class, int.class);
            getLightEngine = level.getMethod("getLightEngine");

            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
            registryAccess = serverLevel.getMethod("registryAccess");

            // TagParser：build134 起只有 parseCompoundFully（仅解析 compound）；
            // 任意值通过 {v:<value>} 包装后解析。旧版可能存在 parseTag（任意 Tag）。
            parseCompoundFully = findMethod(
                    Class.forName("net.minecraft.nbt.TagParser"), "parseCompoundFully", String.class);
            parseTag = findMethod(Class.forName("net.minecraft.nbt.TagParser"), "parseTag", String.class);

            // StarLight
            Class<?> starlightChunk = Class.forName(
                    "ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk");
            getBlockNibbles = starlightChunk.getMethod("starlight$getBlockNibbles");
            getSkyNibbles = starlightChunk.getMethod("starlight$getSkyNibbles");

            Class<?> swmr = Class.forName(
                    "ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray");
            swmrCtor = swmr.getConstructor(byte[].class);
            swmrSet = swmr.getMethod("set", int.class, int.class);
            swmrUpdateVisible = swmr.getMethod("updateVisible");

            Class<?> heightAccessor =
                    Class.forName("net.minecraft.world.level.LevelHeightAccessor");
            Method m;
            try {
                m = heightAccessor.getMethod("getMinSectionY");
            } catch (NoSuchMethodException e) {
                m = heightAccessor.getMethod("getMinSection");
            }
            getMinSection = m;

            Class<?> lightEngine =
                    Class.forName("net.minecraft.world.level.lighting.LevelLightEngine");
            starlightGetInterface = lightEngine.getMethod("starlight$getLightEngine");
            Class<?> starlightInterface = Class.forName(
                    "ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface");
            checkBlockEdges = starlightInterface.getMethod("checkBlockEdges", int.class, int.class);
            checkChunkEdges = starlightInterface.getMethod("checkChunkEdges", int.class, int.class);
            propagateChanges = starlightInterface.getMethod("propagateChanges");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("ComponentsAPI NMS 桥接初始化失败: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- 方块组件

    /** 读取方块实体 NBT 中 capi 复合标签：组件 id → SNBT 值。无则空 Map。 */
    public Map<String, String> readComponents(Block block) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Object serverLevel = handle(block.getWorld());
            Object pos = blockPosCtor.newInstance(block.getX(), block.getY(), block.getZ());
            Object blockEntity = getBlockEntity.invoke(serverLevel, pos);
            if (blockEntity == null) {
                return result;
            }
            Object fullTag = saveToTag(blockEntity, serverLevel);
            if (!(Boolean) tagContains.invoke(fullTag, "capi")) {
                return result;
            }
            Object capi = tagGetCompound.invoke(fullTag, "capi");
            for (String key : (Iterable<String>) tagKeySet.invoke(capi)) {
                Object valueTag = tagGet.invoke(capi, key);
                result.put(key, String.valueOf(valueTag));
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("读取方块组件失败: " + e.getMessage(), e);
        }
        return result;
    }

    /** 把组件 Map 整体写入方块实体 NBT（保留其余 NBT）。 */
    public void writeComponents(Block block, Map<String, String> components) {
        try {
            Object serverLevel = handle(block.getWorld());
            Object pos = blockPosCtor.newInstance(block.getX(), block.getY(), block.getZ());
            Object blockEntity = getBlockEntity.invoke(serverLevel, pos);
            if (blockEntity == null) {
                throw new IllegalStateException("该方块不是方块实体，无法存储组件");
            }
            Object fullTag = saveToTag(blockEntity, serverLevel);

            Object capi = compoundCtor.newInstance();
            for (Map.Entry<String, String> entry : components.entrySet()) {
                Object valueTag = parseAnyTag(entry.getValue());
                tagPut.invoke(capi, entry.getKey(), valueTag);
            }
            tagPut.invoke(fullTag, "capi", capi);

            loadFromTag(blockEntity, serverLevel, fullTag);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("写入方块组件失败: " + e.getMessage(), e);
        }
    }

    /** 该位置是否存在方块实体。 */
    public boolean hasBlockEntity(Block block) {
        try {
            Object serverLevel = handle(block.getWorld());
            Object pos = blockPosCtor.newInstance(block.getX(), block.getY(), block.getZ());
            return getBlockEntity.invoke(serverLevel, pos) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 将方块实体完整序列化为 CompoundTag（自动适配新旧体系）。 */
    private Object saveToTag(Object blockEntity, Object serverLevel) throws ReflectiveOperationException {
        switch (saveMode) {
            case MODE_NEW_PROVIDER: {
                Object provider = registryAccess.invoke(serverLevel);
                return saveFull.invoke(blockEntity, provider);
            }
            case MODE_TAG_PARAM:
                return saveFull.invoke(blockEntity, compoundCtor.newInstance());
            case MODE_NOARG:
            default:
                return saveFull.invoke(blockEntity);
        }
    }

    /** 从 CompoundTag 回写方块实体（自动适配新旧体系）。 */
    private void loadFromTag(Object blockEntity, Object serverLevel, Object fullTag)
            throws ReflectiveOperationException {
        if (saveMode == MODE_NEW_PROVIDER) {
            Object provider = registryAccess.invoke(serverLevel);
            Object valueInput = tviCreate.invoke(null, discardingReporter, provider, fullTag);
            newLoadWithComponents.invoke(blockEntity, valueInput);
        } else {
            Object provider = registryAccess.invoke(serverLevel);
            if (oldLoadWithComponents != null) {
                oldLoadWithComponents.invoke(blockEntity, fullTag, provider);
            } else {
                oldLoad.invoke(blockEntity, fullTag, provider);
            }
        }
    }

    /**
     * 解析任意 SNBT 值为 Tag。
     * build134 起 TagParser 只有 parseCompoundFully（仅 compound），故把任意值包成 {v:VALUE}
     * 解析后取 "v"；旧版若存在 parseTag 则直接解析任意值。
     */
    private Object parseAnyTag(String snbt) throws ReflectiveOperationException {
        if (parseCompoundFully != null) {
            Object wrapped = parseCompoundFully.invoke(null, "{v:" + snbt + "}");
            return tagGet.invoke(wrapped, "v");
        }
        return parseTag.invoke(null, snbt);
    }

    // ---------------------------------------------------------------- 光照

    /**
     * 直接写方块光照：level 0-15；eatEnvironmentLight=true 时同时清除该点天空光。
     */
    public void setBlockLight(World world, int x, int y, int z,
                              int level, boolean eatEnvironmentLight) {
        try {
            Object serverLevel = handle(world);
            Object chunk = getChunk.invoke(serverLevel, x >> 4, z >> 4);

            int sectionY = y >> 4;
            int minSection = (Integer) getMinSection.invoke(serverLevel);
            int nibbleIndex = sectionY - minSection + 1;
            int local = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);

            Object[] blockNibbles = (Object[]) getBlockNibbles.invoke(chunk);
            Object nibble = blockNibbles[nibbleIndex];
            if (nibble == null) {
                nibble = swmrCtor.newInstance(new Object[]{new byte[4096]});
                blockNibbles[nibbleIndex] = nibble;
            }
            swmrSet.invoke(nibble, local, level);
            swmrUpdateVisible.invoke(nibble);

            if (eatEnvironmentLight) {
                Object[] skyNibbles = (Object[]) getSkyNibbles.invoke(chunk);
                Object sky = skyNibbles[nibbleIndex];
                if (sky != null) {
                    swmrSet.invoke(sky, local, 0);
                    swmrUpdateVisible.invoke(sky);
                }
            }

            // 触发边缘重算并让变更传播
            Object lightEngine = getLightEngine.invoke(serverLevel);
            Object starlight = starlightGetInterface.invoke(lightEngine);
            checkBlockEdges.invoke(starlight, x >> 4, z >> 4);
            checkChunkEdges.invoke(starlight, x >> 4, z >> 4);
            propagateChanges.invoke(starlight);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("设置方块光照失败: " + e.getMessage(), e);
        }
    }

    private Object handle(World world) throws ReflectiveOperationException {
        return craftWorldGetHandle.invoke(world);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
