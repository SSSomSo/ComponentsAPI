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

public final class SyncManager implements Listener {

    public static final String CHANNEL = "componentsapi:sync";

    private final ComponentsApiPlugin plugin;
    private final NmsBridge nms;
    private final LightManager lights;
    private final EntityComponents entityComponents;
    private final FallbackBlockStore fallback;
    private final Map<java.util.UUID, Set<Long>> loadedChunks = new HashMap<>();
    private final Map<java.util.UUID, Map<Long, String>> appliedLight = new HashMap<>();

    public SyncManager(ComponentsApiPlugin plugin, NmsBridge nms, LightManager lights,
                       EntityComponents entityComponents, FallbackBlockStore fallback) {
        this.plugin = plugin;
        this.nms = nms;
        this.lights = lights;
        this.entityComponents = entityComponents;
        this.fallback = fallback;
    }

    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        loadedChunks.computeIfAbsent(event.getWorld().getUID(), k -> new HashSet<>())
                .add(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler public void onChunkUnload(ChunkUnloadEvent event) {
        Set<Long> set = loadedChunks.get(event.getWorld().getUID());
        if (set != null) set.remove(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }

    public void syncWorld(World world) {
        try {
            byte[] payload = buildSnapshot(world);
            for (Player player : world.getPlayers()) player.sendPluginMessage(plugin, CHANNEL, payload);
        } catch (Exception e) { plugin.getLogger().warning("同步组件快照失败: " + e.getMessage()); }
    }

    private byte[] buildSnapshot(World world) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF(world.getKey().asString());
        Map<Long, String> lightSignatures = appliedLight.computeIfAbsent(world.getUID(), k -> new HashMap<>());

        ByteArrayOutputStream blockBytes = new ByteArrayOutputStream();
        DataOutputStream blockOut = new DataOutputStream(blockBytes);
        int blockCount = 0;
        for (Long key : loadedChunks.getOrDefault(world.getUID(), Set.of())) {
            int cx = (int) (key >> 32), cz = (int) key.longValue();
            if (!world.isChunkLoaded(cx, cz)) continue;
            Chunk chunk = world.getChunkAt(cx, cz);
            for (BlockState state : chunk.getTileEntities()) {
                Map<String, String> components;
                try { components = nms.readComponents(state.getBlock()); }
                catch (Exception e) { continue; }
                if (components.isEmpty()) continue;
                String model = "";
                String modelSnbt = components.get(ComponentRegistry.BLOCK_MODEL);
                if (modelSnbt != null) model = ComponentRegistry.unwrapString(modelSnbt);
                int light = -1; byte eat = 0;
                String lightSnbt = components.get(ComponentRegistry.BLOCK_LIGHT);
                if (lightSnbt != null) {
                    ComponentRegistry.LightSpec spec = ComponentRegistry.parseBlockLight(lightSnbt);
                    light = spec.light(); eat = (byte) (spec.eatEnvironmentLight() ? 1 : 0);
                    long packed = packPos(state.getX(), state.getY(), state.getZ());
                    String signature = light + "/" + eat;
                    if (!signature.equals(lightSignatures.get(packed))) {
                        lights.apply(state.getBlock(), components);
                        lightSignatures.put(packed, signature);
                    }
                }
                if (!model.isEmpty() || light >= 0) {
                    blockOut.writeLong(packPos(state.getX(), state.getY(), state.getZ()));
                    blockOut.writeUTF(model); blockOut.writeInt(light); blockOut.writeByte(eat);
                    blockCount++;
                }
            }
        }
        for (Map.Entry<String, Map<String, String>> entry : fallback.entries(world)) {
            int[] xyz = FallbackBlockStore.parseKey(entry.getKey());
            Map<String, String> components = entry.getValue();
            String model = "";
            String modelSnbt = components.get(ComponentRegistry.BLOCK_MODEL);
            if (modelSnbt != null) model = ComponentRegistry.unwrapString(modelSnbt);
            int light = -1; byte eat = 0;
            String lightSnbt = components.get(ComponentRegistry.BLOCK_LIGHT);
            if (lightSnbt != null) {
                ComponentRegistry.LightSpec spec = ComponentRegistry.parseBlockLight(lightSnbt);
                light = spec.light(); eat = (byte) (spec.eatEnvironmentLight() ? 1 : 0);
                long packed = packPos(xyz[0], xyz[1], xyz[2]);
                String signature = light + "/" + eat;
                if (!signature.equals(lightSignatures.get(packed))) {
                    lights.apply(world.getBlockAt(xyz[0], xyz[1], xyz[2]), components);
                    lightSignatures.put(packed, signature);
                }
            }
            if (!model.isEmpty() || light >= 0) {
                blockOut.writeLong(packPos(xyz[0], xyz[1], xyz[2]));
                blockOut.writeUTF(model); blockOut.writeInt(light); blockOut.writeByte(eat);
                blockCount++;
            }
        }
        out.writeInt(blockCount);
        out.write(blockBytes.toByteArray());

        ByteArrayOutputStream entityBytes = new ByteArrayOutputStream();
        DataOutputStream entityOut = new DataOutputStream(entityBytes);
        int entityCount = 0;
        for (Entity entity : world.getEntities()) {
            Map<String, String> components;
            try { components = entityComponents.getComponents(entity); }
            catch (Exception e) { continue; }
            String modelSnbt = components.get(ComponentRegistry.ENTITY_MODEL);
            if (modelSnbt == null) continue;
            entityOut.writeInt(entity.getEntityId());
            entityOut.writeUTF(ComponentRegistry.unwrapString(modelSnbt));
            entityCount++;
        }
        out.writeInt(entityCount);
        out.write(entityBytes.toByteArray());
        return bytes.toByteArray();
    }

    public static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static long chunkKey(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }
}
