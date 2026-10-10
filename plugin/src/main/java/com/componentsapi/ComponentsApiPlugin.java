package com.componentsapi;

import com.componentsapi.block.BlockComponents;
import com.componentsapi.block.FallbackBlockStore;
import com.componentsapi.command.CapiCommand;
import com.componentsapi.entity.EntityComponents;
import com.componentsapi.light.LightManager;
import com.componentsapi.net.SyncManager;
import com.componentsapi.nms.NmsBridge;
import com.componentsapi.pack.PackServer;
import org.bukkit.plugin.java.JavaPlugin;

public final class ComponentsApiPlugin extends JavaPlugin {

    private PackServer packServer;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("SSCustomItems") == null) {
            getLogger().severe("未检测到 SSCustomItems 插件，ComponentsAPI 无法启动。请先安装 SSCustomItems。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        NmsBridge nms = new NmsBridge();
        LightManager lights = new LightManager(nms);
        EntityComponents entityComponents = new EntityComponents();
        FallbackBlockStore fallback = new FallbackBlockStore(this);
        BlockComponents blockComponents = new BlockComponents(nms, lights, fallback);
        SyncManager syncManager = new SyncManager(this, nms, lights, entityComponents, fallback);
        packServer = new PackServer(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, SyncManager.CHANNEL);
        getServer().getPluginManager().registerEvents(syncManager, this);
        getServer().getPluginManager().registerEvents(packServer, this);
        CapiCommand capiCommand = new CapiCommand(this, blockComponents, entityComponents);
        getCommand("capi").setExecutor(capiCommand);
        getCommand("capi").setTabCompleter(capiCommand);
        int interval = getConfig().getInt("sync-interval-ticks", 20);
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.World world : getServer().getWorlds()) syncManager.syncWorld(world);
        }, interval, interval);
        getLogger().info("ComponentsAPI 已启用（依赖 SSCustomItems）。作者 Mc_SomSo，许可证 CC BY-SA 4.0。");
    }

    @Override
    public void onDisable() {
        if (packServer != null) packServer.stop();
    }
}
