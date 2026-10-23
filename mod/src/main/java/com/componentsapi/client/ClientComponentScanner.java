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

import java.util.HashSet;
import java.util.Set;

/**
 * 客户端侧组件扫描器（单人世界支持）。
 *
 * 问题：Paper 插件只在 Paper 服务端运行，单人世界内置服务器不加载插件，
 * 同步报文从不发送，ClientState 为空。
 *
 * 性能策略（关键：避免渲染线程卡顿导致单人世界看门狗超时/连接中断）：
 * - 所有扫描在主线程执行（Minecraft 世界数据非线程安全，不能用独立线程）
 * - 每个 tick 只扫描一个区块的一列（x=0..15，z=固定列，y=全高度），
 *   一个区块 16 列，16 tick 扫完一个区块
 * - 已扫描区块用 HashSet 缓存，不重复扫描
 * - 先判断 isAir()，空气位置跳过 getBlockEntity
 * - 实体扫描每 40 tick 一次
 */
public final class ClientComponentScanner {

    private static final int CHUNK_RADIUS = 4;
    private static final int ENTITY_INTERVAL = 40;
    private static final int RESET_INTERVAL = 200;

    private static final Set<Long> scannedChunks = new HashSet<>();
    private static int tickCounter = 0;

    private ClientComponentScanner() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientComponentScanner::tick);
    }

    private static void tick(Minecraft client) {
        tickCounter++;
        if (client.level == null || client.player == null) {
            scannedChunks.clear();
            return;
        }

        // 每 200 tick 清空一次缓存，让玩家移动到新区域或 BlockEntity 更新时重新扫描
        if (tickCounter % RESET_INTERVAL == 0) {
            // 只清远离玩家的区块，保留周围的
            int pcx = client.player.blockPosition().getX() >> 4;
            int pcz = client.player.blockPosition().getZ() >> 4;
            scannedChunks.removeIf(key -> {
                int cx = (int) (key & 0xFFFFFFFFL);
                int cz = (int) (key >>> 32);
                return Math.abs(cx - pcx) > CHUNK_RADIUS || Math.abs(cz - pcz) > CHUNK_RADIUS;
            });
        }

        // 每 tick 扫描一个区块的一列
        scanOneColumn(client);

        // 实体扫描
        if (tickCounter % ENTITY_INTERVAL == 0) {
            scanEntities(client);
        }
    }

    /**
     * 找到下一个待扫描的区块，扫描其当前列（用静态计数器记录列号）。
     * 为了简单，每个 tick 扫描一个新的未完成区块的第一列，
     * 列进度用 progress 记录。
     */
    private static int currentCx = Integer.MIN_VALUE;
    private static int currentCz = Integer.MIN_VALUE;
    private static int currentColumn = 0;

    private static void scanOneColumn(Minecraft client) {
        ClientLevel level = client.level;
        int pcx = client.player.blockPosition().getX() >> 4;
        int pcz = client.player.blockPosition().getZ() >> 4;

        // 若当前区块列已扫完，找下一个未扫描区块
        if (currentColumn >= 16 || currentCx == Integer.MIN_VALUE) {
            int[] next = findNextChunk(level, pcx, pcz);
            if (next == null) {
                currentCx = Integer.MIN_VALUE;
                currentCz = Integer.MIN_VALUE;
                currentColumn = 0;
                return;
            }
            currentCx = next[0];
            currentCz = next[1];
            currentColumn = 0;
            long key = ((long) currentCx & 0xFFFFFFFFL) | (((long) currentCz & 0xFFFFFFFFL) << 32);
            scannedChunks.add(key);
        }

        if (!level.hasChunk(currentCx, currentCz)) {
            currentColumn = 16;
            return;
        }
        LevelChunk chunk = level.getChunk(currentCx, currentCz);
        HolderLookup.Provider ra = level.registryAccess();

        // 扫描一列：x=0..15，z=currentColumn，y=全高度
        int baseX = currentCx << 4;
        int baseZ = (currentCz << 4) + currentColumn;
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        for (int x = 0; x < 16; x++) {
            for (int y = minY; y < maxY; y++) {
                BlockPos pos = new BlockPos(baseX + x, y, baseZ);
                if (level.getBlockState(pos).isAir()) continue;
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null) continue;
                try {
                    CompoundTag fullTag = be.saveWithFullMetadata(ra);
                    if (fullTag == null || !fullTag.contains("capi")) continue;
                    CompoundTag capi = fullTag.getCompound("capi").orElse(null);
                    if (capi == null) continue;

                    String model = null;
                    if (capi.contains("block_model")) {
                        model = capi.getString("block_model").orElse(null);
                    }
                    int light = -1;
                    boolean eat = false;
                    if (capi.contains("block_light")) {
                        CompoundTag lt = capi.getCompound("block_light").orElse(null);
                        if (lt != null) {
                            light = lt.getInt("light").orElse(-1);
                            eat = lt.getBoolean("eat_environment_light").orElse(false);
                        }
                    }
                    if (model != null || light >= 0) {
                        ClientState.get().updateBlock(pos, model, light, eat);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        currentColumn++;
    }

    /** 螺旋搜索下一个未扫描的已加载区块。 */
    private static int[] findNextChunk(ClientLevel level, int pcx, int pcz) {
        for (int r = 0; r <= CHUNK_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    long key = ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
                    if (scannedChunks.contains(key)) continue;
                    if (!level.hasChunk(cx, cz)) continue;
                    return new int[]{cx, cz};
                }
            }
        }
        return null;
    }

    /** 扫描实体（主线程，每 ENTITY_INTERVAL tick 一次）。 */
    private static void scanEntities(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) return;
        ClientState cs = ClientState.get();
        for (Entity entity : level.entitiesForRendering()) {
            try {
                net.minecraft.world.item.component.CustomData cd = entity.get(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (cd == null) continue;
                CompoundTag tag = cd.copyTag();
                if (!tag.contains("capi")) continue;
                CompoundTag capi = tag.getCompound("capi").orElse(null);
                if (capi == null || !capi.contains("entity_model")) continue;
                String model = capi.getString("entity_model").orElse(null);
                if (model != null) {
                    cs.updateEntity(entity.getId(), model);
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
