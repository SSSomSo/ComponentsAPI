package com.componentsapi.pack;

import com.componentsapi.ComponentsApiPlugin;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PackServer implements Listener {

    private static final String PACK_RESOURCE = "/resourcepack/capi-pack.zip";

    private final ComponentsApiPlugin plugin;
    private File packFile;
    private String sha1;
    private String url;
    private boolean force;
    private HttpServer http;
    private final UUID packId = UUID.randomUUID();

    public PackServer(ComponentsApiPlugin plugin) { this.plugin = plugin; }

    public void start() {
        boolean enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        int port = plugin.getConfig().getInt("resource-pack.port", 8732);
        force = plugin.getConfig().getBoolean("resource-pack.require-download", false);
        String host = plugin.getConfig().getString("resource-pack.host", "auto");
        try {
            File packDir = new File(plugin.getDataFolder(), "pack");
            Files.createDirectories(packDir.toPath());
            File target = new File(packDir, "capi-pack.zip");
            File staging = new File(packDir, "staging");
            assemble(staging, target);
            packFile = target;
            byte[] data = Files.readAllBytes(packFile.toPath());
            sha1 = toHex(MessageDigest.getInstance("SHA-1").digest(data));
            String address;
            if (host == null || host.equals("auto") || host.isEmpty()) {
                String bindIp = plugin.getServer().getIp();
                if (bindIp != null && !bindIp.isEmpty() && !bindIp.equals("0.0.0.0")) address = bindIp;
                else address = InetAddress.getLocalHost().getHostAddress();
            } else address = host;
            url = "http://" + address + ":" + port + "/capi-pack.zip";
            http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/capi-pack.zip", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
                exchange.close();
            });
            http.start();
            plugin.getLogger().info("资源包托管已启动: " + url + " (SHA-1 " + sha1 + ")");
        } catch (Exception e) { plugin.getLogger().warning("资源包托管启动失败: " + e.getMessage()); }
    }

    private void assemble(File staging, File target) throws Exception {
        if (staging.exists()) deleteRecursively(staging);
        Files.createDirectories(staging.toPath());
        try (InputStream in = plugin.getResource("resourcepack/capi-pack.zip")) {
            if (in == null) throw new IllegalStateException("jar 内缺少基础包 resourcepack/capi-pack.zip");
            unzip(in, staging);
        }
        File pluginsRoot = plugin.getDataFolder().getParentFile();
        File ssciPack = new File(new File(pluginsRoot, "SSCustomItems"), "resourcepack");
        if (ssciPack.isDirectory()) {
            copyDirectory(ssciPack.toPath(), staging.toPath());
            plugin.getLogger().info("已合并 SSCustomItems 资源目录: " + ssciPack.getPath());
        }
        if (target.exists()) Files.delete(target.toPath());
        zip(staging, target);
    }

    public void stop() { if (http != null) http.stop(0); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        boolean enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        if (packFile == null || !enabled) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline())
                player.setResourcePack(packId, url, sha1, net.kyori.adventure.text.Component.text("ComponentsAPI 资源包"), force);
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!force || packFile == null) return;
        if (!packId.equals(event.getID())) return;
        switch (event.getStatus()) {
            case DECLINED, FAILED_RELOAD, FAILED_DOWNLOAD, INVALID_URL, DISCARDED ->
                event.getPlayer().kick(net.kyori.adventure.text.Component.text("必须接受 ComponentsAPI 资源包才能进入服务器"));
            default -> { }
        }
    }

    private static void unzip(InputStream input, File targetDir) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(targetDir, entry.getName());
                if (!out.toPath().normalize().startsWith(targetDir.toPath().normalize()))
                    throw new IllegalStateException("非法 zip 条目: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(out.toPath());
                else { Files.createDirectories(out.getParentFile().toPath()); Files.copy(zip, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }

    private static void zip(File sourceDir, File target) throws Exception {
        Path root = sourceDir.toPath();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    String name = root.relativize(file).toString().replace('\\', '/');
                    out.putNextEntry(new ZipEntry(name)); Files.copy(file, out); out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void copyDirectory(Path source, Path target) throws Exception {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString())); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                Path dest = target.resolve(source.relativize(file).toString());
                Files.createDirectories(dest.getParent()); Files.copy(file, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(File file) throws Exception {
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws java.io.IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, java.io.IOException exc) throws java.io.IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }

    private static String toHex(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
