package com.componentsapi.net;

import com.componentsapi.ComponentsApiPlugin;
import com.componentsapi.block.FallbackBlockStore;
import com.componentsapi.component.ComponentRegistry;
import com.componentsapi.entity.EntityComponents;
import com.componentsapi.light.LightManager;
import com.componentsapi.nms.NmsBridge;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 组件快照同步：
 * 周期性扫描已加载区块的方块实体与世界实体，二进制快照通过插件消息通道
 * "componentsapi:sync" 推送给客户端模组。
 *
 * 报文格式：
 *   UTF 维度 id
 *   int 方块数；每个：long 打包坐标、UTF 模型（""=无）、int 光照（-1=无）、byte 吃环境光
 *   int 实体数；每个：int 实体网络 id、UTF 模型
 */
public final class SyncManager implements Listener {

    public static final String CHANNEL = "componentsapi:sync";

    private final ComponentsApiPlugin plugin;
    private final NmsBridge nms;
    private final LightManager lights;
    private final EntityComponents entityComponents;
    private final FallbackBlockStore fallback;

    /** 世界 UID → 已加载区块 key 集合 */
    private final Map<java.util.UUID, Set<Long>> loadedChunks = new HashMap<>();
    /** 世界 UUID → 位置打包 → 已应用光照签名（避免重复写 nibble） */
    private final Map<java.util.UUID, Map<Long, String>> appliedLight = new HashMap<>();

    public SyncManager(ComponentsApiPlugin plugin, NmsBridge nms, LightManager lights,
                       EntityComponents entityComponents, FallbackBlockStore fallback) {
        this.plugin = plugin;
        this.nms = nms;
        this.lights = lights;
        this.entityComponents = entityComponents;
        this.fallback = fallback;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        loadedChunks.computeIfAbsent(event.getWorld().getUID(), k -> new HashSet<>())
                .add(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Set<Long> set = loadedChunks.get(event.getWorld().getUID());
        if (set != null) {
            set.remove(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        }
    }

    /** 构建并向该世界所有玩家推送一次快照。 */
    public void syncWorld(World world) {
        try {
            byte[] payload = buildSnapshot(world);
            for (Player player : world.getPlayers()) {
                player.sendPluginMessage(plugin, CHANNEL, payload);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("同步组件快照失败: " + e.getMessage());
        }
    }

    private byte[] buildSnapshot(World world) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF(world.getKey().asString());

        Map<Long, String> lightSignatures =
                appliedLight.computeIfAbsent(world.getUID(), k -> new HashMap<>());

        // ---- 方块
        ByteArrayOutputStream blockBytes = new ByteArrayOutputStream();
        DataOutputStream blockOut = new DataOutputStream(blockBytes);
        int blockCount = 0;

        Set<Long> chunks = loadedChunks.getOrDefault(world.getUID(), Set.of());
        for (Long key : chunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key.longValue();
            if (!world.isChunkLoaded(cx, cz)) {
                continue;
            }
            Chunk chunk = world.getChunkAt(cx, cz);
            for (BlockState state : chunk.getTileEntities()) {
                Map<String, String> components;
                try {
                    components = nms.readComponents(state.getBlock());
                } catch (Exception e) {
                    continue;
                }
                if (components.isEmpty()) {
                    continue;
                }

                String model = "";
                String modelSnbt = components.get(ComponentRegistry.BLOCK_MODEL);
                if (modelSnbt != null) {
                    model = ComponentRegistry.unwrapString(modelSnbt);
                }

                int light = -1;
                byte eat = 0;
                String lightSnbt = components.get(ComponentRegistry.BLOCK_LIGHT);
                if (lightSnbt != null) {
                    ComponentRegistry.LightSpec spec =
                            ComponentRegistry.parseBlockLight(lightSnbt);
                    light = spec.light();
                    eat = (byte) (spec.eatEnvironmentLight() ? 1 : 0);

                    long packed = packPos(state.getX(), state.getY(), state.getZ());
                    String signature = light + "/" + eat;
                    if (!signature.equals(lightSignatures.get(packed))) {
                        lights.apply(state.getBlock(), components);
                        lightSignatures.put(packed, signature);
                    }
                }

                if (!model.isEmpty() || light >= 0) {
                    blockOut.writeLong(packPos(state.getX(), state.getY(), state.getZ()));
                    blockOut.writeUTF(model);
                    blockOut.writeInt(light);
                    blockOut.writeByte(eat);
                    blockCount++;
                }
            }
        }
        // ---- 非方块实体方块（兜底存储）
        for (Map.Entry<String, Map<String, String>> entry : fallback.entries(world)) {
            int[] xyz = FallbackBlockStore.parseKey(entry.getKey());
            Map<String, String> components = entry.getValue();

            String model = "";
            String modelSnbt = components.get(ComponentRegistry.BLOCK_MODEL);
            if (modelSnbt != null) {
                model = ComponentRegistry.unwrapString(modelSnbt);
            }

            int light = -1;
            byte eat = 0;
            String lightSnbt = components.get(ComponentRegistry.BLOCK_LIGHT);
            if (lightSnbt != null) {
                ComponentRegistry.LightSpec spec = ComponentRegistry.parseBlockLight(lightSnbt);
                light = spec.light();
                eat = (byte) (spec.eatEnvironmentLight() ? 1 : 0);
                long packed = packPos(xyz[0], xyz[1], xyz[2]);
                String signature = light + "/" + eat;
                if (!signature.equals(lightSignatures.get(packed))) {
                    lights.apply(world.getBlockAt(xyz[0], xyz[1], xyz[2]), components);
                    lightSignatures.put(packed, signature);
                }
            }

            if (!model.isEmpty() || light >= 0) {
                blockOut.writeLong(packPos(xyz[0], xyz[1], xyz[2]));
                blockOut.writeUTF(model);
                blockOut.writeInt(light);
                blockOut.writeByte(eat);
                blockCount++;
            }
        }

        out.writeInt(blockCount);
        out.write(blockBytes.toByteArray());

        // ---- 实体
        ByteArrayOutputStream entityBytes = new ByteArrayOutputStream();
        DataOutputStream entityOut = new DataOutputStream(entityBytes);
        int entityCount = 0;
        for (Entity entity : world.getEntities()) {
            Map<String, String> components;
            try {
                components = entityComponents.getComponents(entity);
            } catch (Exception e) {
                continue;
            }
            String modelSnbt = components.get(ComponentRegistry.ENTITY_MODEL);
            if (modelSnbt == null) {
                continue;
            }
            entityOut.writeInt(entity.getEntityId());
            entityOut.writeUTF(ComponentRegistry.unwrapString(modelSnbt));
            entityCount++;
        }
        out.writeInt(entityCount);
        out.write(entityBytes.toByteArray());

        return bytes.toByteArray();
    }

    /** 26+12+26 位打包坐标。 */
    public static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
