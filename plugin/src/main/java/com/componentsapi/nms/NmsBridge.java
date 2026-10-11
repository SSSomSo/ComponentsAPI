package com.componentsapi.nms;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NMS 全反射桥接：
 * 1) 方块实体 NBT 中 capi 复合标签的读写（因此支持 /data merge block）；
 * 2) 直接写 StarLight 的方块光照 nibble（Paper 26.2 使用 StarLight/Moonrise）。
 */
public final class NmsBridge {

    private final Method craftWorldGetHandle;
    private final Constructor<?> blockPosCtor;
    private final Constructor<?> compoundCtor;
    private final Method getBlockEntity;
    private final Method saveFull;
    private final boolean saveFullHasParam;
    private final Method loadWithComponents;
    private final Method load;
    private final Method registryAccess;
    private final Method tagContains;
    private final Method tagGetCompound;
    private final Method tagPut;
    private final Method tagKeySet;
    private final Method tagGet;
    private final Method parseTag;
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
            // 保存方法在不同版本/分支签名不同：
            //   Paper 部分版本: saveWithFullMetadata(CompoundTag)
            //   Leaf/较新原版: saveWithFullMetadata() 无参返回 CompoundTag
            //   旧版: save(CompoundTag)
            // 按优先级尝试，取第一个存在的。
            Method sf = findMethod(blockEntity, "saveWithFullMetadata", compound);
            if (sf == null) {
                sf = findMethod(blockEntity, "saveWithFullMetadata");
            }
            if (sf == null) {
                sf = findMethod(blockEntity, "save", compound);
            }
            if (sf == null) {
                throw new NoSuchMethodException(
                        "BlockEntity 中找不到 saveWithFullMetadata()/saveWithFullMetadata(CompoundTag)/save(CompoundTag)");
            }
            saveFull = sf;
            saveFullHasParam = sf.getParameterCount() > 0;

            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            getBlockEntity = level.getMethod("getBlockEntity", blockPos);
            getChunk = level.getMethod("getChunk", int.class, int.class);
            getLightEngine = level.getMethod("getLightEngine");

            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> registryAccessClass;
            Method ra;
            try {
                registryAccessClass = Class.forName("net.minecraft.core.RegistryAccess");
                ra = serverLevel.getMethod("registryAccess");
            } catch (ClassNotFoundException e) {
                registryAccessClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
                ra = serverLevel.getMethod("registryAccess");
            }
            registryAccess = ra;
            loadWithComponents = findMethod(blockEntity, "loadWithComponents", compound, registryAccessClass);
            load = findMethod(blockEntity, "load", compound, registryAccessClass);

            // TagParser：任意 SNBT 值 → Tag
            Class<?> tagParser = Class.forName("net.minecraft.nbt.TagParser");
            Method pt = findMethod(tagParser, "parseTag", String.class);
            if (pt == null) {
                pt = tagParser.getMethod("parseCompoundFully", String.class);
            }
            parseTag = pt;

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
            Object fullTag = saveFullHasParam
                    ? saveFull.invoke(blockEntity, compoundCtor.newInstance())
                    : saveFull.invoke(blockEntity);
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
            Object fullTag = saveFullHasParam
                    ? saveFull.invoke(blockEntity, compoundCtor.newInstance())
                    : saveFull.invoke(blockEntity);

            Object capi = compoundCtor.newInstance();
            for (Map.Entry<String, String> entry : components.entrySet()) {
                Object valueTag = parseTag.invoke(null, entry.getValue());
                tagPut.invoke(capi, entry.getKey(), valueTag);
            }
            tagPut.invoke(fullTag, "capi", capi);

            Object provider = registryAccess.invoke(serverLevel);
            if (loadWithComponents != null) {
                loadWithComponents.invoke(blockEntity, fullTag, provider);
            } else {
                load.invoke(blockEntity, fullTag, provider);
            }
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

    private Object parseSnbt(String snbt) throws ReflectiveOperationException {
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
