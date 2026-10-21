package com.componentsapi.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 客户端侧组件扫描器（单人世界支持）。
 *
 * 问题：ComponentsAPI 插件端只在 Paper 服务端运行，单人世界使用内置服务器
 * （IntegratedServer），插件不加载，所以同步报文从不发送，客户端 ClientState
 * 永远为空，方块/实体模型组件不显示。
 *
 * 解决方案：在客户端直接扫描世界中的 BlockEntity/Entity，读取 capi 复合标签，
 * 更新 ClientState。不依赖插件端同步。
 *
 * 工作方式：
 * - 每 20 tick（1秒）扫描一次当前维度玩家周围的已加载区块
 * - 对每个 BlockEntity，调用 saveWithFullMetadata() 获取完整 NBT，读取 capi 复合标签
 * - 对每个 Entity，读取 PDC 中的 capi 复合标签
 * - 直接更新 ClientState（与插件端同步报文相同的数据结构）
 *
 * 注意：
 * - 多人世界中插件端会发送同步报文，客户端扫描器作为补充（读取客户端可见的 NBT）
 * - 单人世界中这是唯一的组件数据来源
 * - 扫描范围限制为玩家周围 8 个区块，避免性能问题
 */
public final class ClientComponentScanner {

    /** 扫描间隔（tick）。 */
    private static final int SCAN_INTERVAL = 20;

    /** 扫描半径（区块）。玩家周围 SCAN_RADIUS x SCAN_RADIUS 个区块。 */
    private static final int SCAN_RADIUS = 8;

    private static int tickCounter = 0;

    private ClientComponentScanner() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter < SCAN_INTERVAL) {
                return;
            }
            tickCounter = 0;
            scan(client);
        });
    }

    private static void scan(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            return;
        }

        HolderLookup.Provider registryAccess = level.registryAccess();
        BlockPos playerPos = client.player.blockPosition();
        int pcx = playerPos.getX() >> 4;
        int pcz = playerPos.getZ() >> 4;

        ClientState cs = ClientState.get();

        // 扫描玩家周围的区块
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                LevelChunk chunk;
                try {
                    chunk = level.getChunk(cx, cz);
                } catch (Exception e) {
                    continue;
                }
                scanBlockEntities(level, chunk, registryAccess, cs);
            }
        }

        // 扫描实体
        scanEntities(level, cs);
    }

    /**
     * 扫描区块中的 BlockEntity，读取 capi 复合标签，更新 ClientState。
     *
     * LevelChunk 没有公开的 getBlockEntities() 方法，所以遍历区块中的所有位置，
     * 调用 level.getBlockEntity(pos) 获取 BlockEntity。为了提高效率，只检查
     * 非空气方块的位置。
     *
     * capi 复合标签格式（与插件端 NmsBridge 一致）：
     * {capi: {block_model: "capi:block/xxx", block_light: {light: 15, eat_environment_light: false}}}
     */
    private static void scanBlockEntities(ClientLevel level, LevelChunk chunk,
                                          HolderLookup.Provider registryAccess, ClientState cs) {
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        int minX = cx << 4;
        int minZ = cz << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                    BlockPos pos = new BlockPos(minX + x, y, minZ + z);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be == null) {
                        continue;
                    }
                    try {
                        CompoundTag fullTag = be.saveWithFullMetadata(registryAccess);
                        if (!fullTag.contains("capi")) {
                            continue;
                        }
                        CompoundTag capi = fullTag.getCompound("capi").orElse(null);
                        if (capi == null) {
                            continue;
                        }

                        String model = null;
                        if (capi.contains("block_model")) {
                            model = capi.getString("block_model").orElse(null);
                        }

                        int light = -1;
                        boolean eat = false;
                        if (capi.contains("block_light")) {
                            CompoundTag lightTag = capi.getCompound("block_light").orElse(null);
                            if (lightTag != null) {
                                light = lightTag.getInt("light").orElse(-1);
                                eat = lightTag.getBoolean("eat_environment_light").orElse(false);
                            }
                        }

                        if (model != null || light >= 0) {
                            updateBlockState(cs, pos, model, light, eat);
                        }
                    } catch (Exception e) {
                        // 忽略单个 BlockEntity 的扫描错误
                    }
                }
            }
        }
    }

    /**
     * 扫描实体，读取 CustomData 中的 capi 复合标签，更新 ClientState。
     *
     * 注意：插件端 EntityComponents 用 PDC 存储 capi 标签，但客户端 Entity
     * 只有 CustomData 组件（DataComponents.CUSTOM_DATA）。单人世界中
     * 服务器端 Entity 的 PDC 不会自动同步到客户端 CustomData，所以实体模型
     * 在单人世界中可能需要通过其他方式同步。这里先尝试读取 CustomData。
     */
    private static void scanEntities(ClientLevel level, ClientState cs) {
        for (Entity entity : level.entitiesForRendering()) {
            try {
                net.minecraft.world.item.component.CustomData cd = entity.get(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (cd == null) {
                    continue;
                }
                CompoundTag tag = cd.copyTag();
                if (!tag.contains("capi")) {
                    continue;
                }
                CompoundTag capi = tag.getCompound("capi").orElse(null);
                if (capi == null) {
                    continue;
                }
                if (capi.contains("entity_model")) {
                    String model = capi.getString("entity_model").orElse(null);
                    if (model != null) {
                        updateEntityState(cs, entity.getId(), model);
                    }
                }
            } catch (Exception e) {
                // 忽略单个实体的扫描错误
            }
        }
    }

    /**
     * 更新 ClientState 中的方块渲染信息。
     * 需要 ClientState 提供公共更新方法。
     */
    private static void updateBlockState(ClientState cs, BlockPos pos, String model,
                                         int light, boolean eat) {
        // 通过反射或公共方法更新 ClientState
        // 暂时使用 handle() 的格式构建数据
        // 实际上需要修改 ClientState 提供公共更新方法
        cs.updateBlock(pos, model, light, eat);
    }

    private static void updateEntityState(ClientState cs, int entityId, String model) {
        cs.updateEntity(entityId, model);
    }
}
