package com.componentsapi.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public final class ClientState {

    public record BlockRender(String model, int light, boolean eatEnvironmentLight) {}

    private static final ClientState INSTANCE = new ClientState();
    private String dimension = "";
    private final Long2ObjectMap<BlockRender> blocks = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectMap<String> entities = new Int2ObjectOpenHashMap<>();

    public static ClientState get() { return INSTANCE; }

    public void handle(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String newDimension = in.readUTF();
            if (!newDimension.equals(dimension)) { dimension = newDimension; blocks.clear(); entities.clear(); }
            blocks.clear();
            int blockCount = in.readInt();
            for (int i = 0; i < blockCount; i++) {
                long packed = in.readLong();
                String model = in.readUTF();
                int light = in.readInt();
                boolean eat = in.readByte() != 0;
                blocks.put(packed, new BlockRender(model.isEmpty() ? null : model, light, eat));
            }
            entities.clear();
            int entityCount = in.readInt();
            for (int i = 0; i < entityCount; i++) {
                int entityId = in.readInt();
                String model = in.readUTF();
                entities.put(entityId, model);
            }
        } catch (Exception e) { ComponentsApiClient.LOGGER.warn("解析组件快照失败", e); }
    }

    public String blockModel(BlockPos pos) {
        BlockRender render = blocks.get(packPos(pos.getX(), pos.getY(), pos.getZ()));
        return render == null ? null : render.model();
    }

    public String entityModel(int entityId) { return entities.get(entityId); }

    public static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private ClientState() {}
}
