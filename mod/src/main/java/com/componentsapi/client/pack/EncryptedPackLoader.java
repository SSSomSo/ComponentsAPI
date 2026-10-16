package com.componentsapi.client.pack;

import com.componentsapi.client.ComponentsApiClient;
import com.componentsapi.client.config.CapiConfig;
import com.componentsapi.client.crypto.PackCipher;
import com.componentsapi.client.mixin.PackRepositoryAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 从模组 jar 内 {@code capi_packs/*.dat} 读取加密资源包，在内存中解密并
 * 直接注入到客户端资源系统，全过程不向磁盘（包括临时目录）写出解密内容。
 */
public final class EncryptedPackLoader {

    private static final String DIR = "capi_packs";

    private record DecryptedPack(String id, String title, Map<String, byte[]> files) {}

    /** 在客户端启动完成（资源系统就绪）后执行一次注入。 */
    public static void register(CapiConfig cfg) {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> initialize(cfg));
    }

    private static void initialize(CapiConfig cfg) {
        try {
            List<DecryptedPack> packs = loadFromJar(cfg);
            if (packs.isEmpty()) {
                ComponentsApiClient.LOGGER.info("[CAPI] 模组 jar 内 capi_packs/ 无 .dat，跳过加密资源包注入。");
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            PackRepository repo = mc.getResourcePackRepository();
            Set<RepositorySource> sources =
                    ((PackRepositoryAccessor) (Object) repo).capi$getSources();

            List<String> ids = new ArrayList<>();
            for (DecryptedPack pack : packs) {
                final Map<String, byte[]> files = pack.files;
                RepositorySource source = consumer -> {
                    PackLocationInfo info = new PackLocationInfo(
                            pack.id, Component.literal(pack.title),
                            PackSource.BUILT_IN, Optional.empty());
                    Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                        @Override
                        public PackResources openPrimary(PackLocationInfo loc) {
                            return new InMemoryPackResources(loc, files);
                        }

                        @Override
                        public PackResources openFull(PackLocationInfo loc, Pack.Metadata metadata) {
                            return new InMemoryPackResources(loc, files);
                        }
                    };
                    Pack created = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
                            new PackSelectionConfig(false, Pack.Position.TOP, false));
                    if (created != null) {
                        consumer.accept(created);
                    }
                };
                sources.add(source);
                ids.add(pack.id);
            }

            repo.reload();
            for (String id : ids) {
                if (repo.isAvailable(id)) {
                    repo.addPack(id);
                    ComponentsApiClient.LOGGER.info("[CAPI] 已启用内存解密资源包: {}", id);
                } else {
                    ComponentsApiClient.LOGGER.warn("[CAPI] 资源包未能出现在可用列表（检查 pack.mcmeta）: {}", id);
                }
            }
            mc.reloadResourcePacks();
            ComponentsApiClient.LOGGER.info("[CAPI] 已从 jar 解密并加载 {} 个加密资源包（全程内存）。", ids.size());
        } catch (Throwable t) {
            ComponentsApiClient.LOGGER.error("[CAPI] 加密资源包注入失败", t);
        }
    }

    /** 读取 jar 内 capi_packs/*.dat，解密并解析为内存 zip。 */
    private static List<DecryptedPack> loadFromJar(CapiConfig cfg) throws Exception {
        Optional<Path> dirOpt = FabricLoader.getInstance()
                .getModContainer("componentsapi")
                .flatMap(c -> c.findPath(DIR));
        List<DecryptedPack> result = new ArrayList<>();
        if (dirOpt.isEmpty() || !Files.isDirectory(dirOpt.get())) {
            return result;
        }
        List<Path> dats;
        try (var walk = Files.walk(dirOpt.get())) {
            dats = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dat"))
                    .toList();
        }
        for (Path dat : dats) {
            String fileName = dat.getFileName().toString();
            String base = fileName.substring(0, fileName.length() - ".dat".length());
            byte[] enc = Files.readAllBytes(dat);
            byte[] zip = PackCipher.decrypt(enc, cfg.keyMaterial());
            Map<String, byte[]> files = unzip(zip);
            result.add(new DecryptedPack(sanitize(base), base, files));
        }
        return result;
    }

    /** 解压 zip 字节为路径->字节 map（不写盘）。 */
    private static Map<String, byte[]> unzip(byte[] zip) throws java.io.IOException {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                while (name.startsWith("/")) {
                    name = name.substring(1);
                }
                map.put(name, zis.readAllBytes());
            }
        }
        return map;
    }

    /** 资源包 id 只允许小写字母数字与 -_。 */
    private static String sanitize(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return r.isEmpty() ? "capi_pack" : r;
    }

    private EncryptedPackLoader() {}
}
