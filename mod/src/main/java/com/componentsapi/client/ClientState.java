package com.componentsapi.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * 客户端组件状态：当前维度内方块（打包坐标 → 渲染信息）与生物（网络 id → 模型）。
 * 由同步报文刷新。
 *
 * 性能优化：维护 occupiedChunks 集合（有自定义方块的 chunk 坐标），
 * 渲染线程在 tesselateBlock 中先做 chunk 级 O(1) 过滤，空 chunk 直接跳过，
 * 避免每帧对视野内数千个方块都走 Long2ObjectMap 查询。
 */
public final class ClientState {

    public record BlockRender(String model, int light, boolean eatEnvironmentLight) {}

    private static final ClientState INSTANCE = new ClientState();

    private String dimension = "";
    private final Long2ObjectMap<BlockRender> blocks = new Long2ObjectOpenHashMap<>();
    private final LongSet occupiedChunks = new LongOpenHashSet();
    private final Int2ObjectMap<String> entities = new Int2ObjectOpenHashMap<>();

    public static ClientState get() {
        return INSTANCE;
    }

    public void handle(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String newDimension = in.readUTF();
            if (!newDimension.equals(dimension)) {
                dimension = newDimension;
                blocks.clear();
                entities.clear();
            }

            blocks.clear();
            occupiedChunks.clear();
            int blockCount = in.readInt();
            for (int i = 0; i < blockCount; i++) {
                long packed = in.readLong();
                String model = in.readUTF();
                int light = in.readInt();
                boolean eat = in.readByte() != 0;
                blocks.put(packed, new BlockRender(model.isEmpty() ? null : model, light, eat));
                // 解包坐标，标记所在 chunk
                int x = (int) (packed >> 38);
                int z = (int) ((packed >> 12) & 0x3FFFFFF);
                if ((x & 0x2000000) != 0) x -= 0x4000000;
                if ((z & 0x2000000) != 0) z -= 0x4000000;
                occupiedChunks.add(packChunk(x >> 4, z >> 4));
            }

            entities.clear();
            int entityCount = in.readInt();
            for (int i = 0; i < entityCount; i++) {
                int entityId = in.readInt();
                String model = in.readUTF();
                entities.put(entityId, model);
            }
        } catch (Exception e) {
            ComponentsApiClient.LOGGER.warn("解析组件快照失败", e);
        }
    }

    public String blockModel(BlockPos pos) {
        BlockRender render = blocks.get(packPos(pos.getX(), pos.getY(), pos.getZ()));
        return render == null ? null : render.model();
    }

    /** chunk 级快速过滤：该 chunk 内是否存在任意带组件的方块。渲染热路径优先调用此方法。 */
    public boolean chunkHasBlock(int chunkX, int chunkZ) {
        return occupiedChunks.contains(packChunk(chunkX, chunkZ));
    }

    public String entityModel(int entityId) {
        return entities.get(entityId);
    }

    /** 与插件 SyncManager#packPos 相同口径。 */
    public static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
    }

    /** chunk 坐标打包：x 占高 32 位，z 占低 32 位。 */
    private static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
