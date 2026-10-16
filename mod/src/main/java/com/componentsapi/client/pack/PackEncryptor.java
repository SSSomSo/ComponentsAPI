package com.componentsapi.client.pack;

import com.componentsapi.client.ComponentsApiClient;
import com.componentsapi.client.config.CapiConfig;
import com.componentsapi.client.crypto.PackCipher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * 启动时扫描 config/capi/rp/ 下的所有 .zip，
 * 用当前密钥加密为同名 .dat 输出到 config/capi/rp/dat/。
 * 原始 .zip 不删除（保留给开发者打包）。
 */
public final class PackEncryptor {

    /** 加密 rp 目录下全部 zip，返回成功处理的数量。 */
    public static int encryptAll(CapiConfig cfg) {
        Path rp = cfg.rpDir();
        if (!Files.isDirectory(rp)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> stream = Files.list(rp)) {
            var zips = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".zip"))
                    .filter(Files::isRegularFile)
                    .toList();
            for (Path zip : zips) {
                String name = zip.getFileName().toString();
                String base = name.substring(0, name.length() - ".zip".length());
                Path dat = cfg.datDir().resolve(base + ".dat");
                try {
                    byte[] plain = Files.readAllBytes(zip);
                    byte[] enc = PackCipher.encrypt(plain, cfg.keyMaterial());
                    Files.write(dat, enc,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    ComponentsApiClient.LOGGER.info("[CAPI] 已加密资源包 {} -> {}，请把该 .dat 放入模组 jar 的 capi_packs/ 目录",
                            name, dat);
                    count++;
                } catch (Exception e) {
                    ComponentsApiClient.LOGGER.error("[CAPI] 加密资源包失败: " + name, e);
                }
            }
        } catch (IOException e) {
            ComponentsApiClient.LOGGER.error("[CAPI] 扫描资源包目录失败: " + rp, e);
        }
        return count;
    }

    private PackEncryptor() {}
}
