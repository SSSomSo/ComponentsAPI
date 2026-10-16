package com.componentsapi.client.config;

import com.componentsapi.client.crypto.PackCipher;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ComponentsAPI 客户端配置：config/capi/config.toml。
 *
 * <p>当前仅识别一个键 {@code resource_pack_key}（base64，解码后 ≥ 64 字节 / 512 位）。
 * 首次启动自动生成随机密钥；为了不引入额外 TOML 依赖，这里只做该键的极简解析。
 *
 * <p>目录约定：
 * <ul>
 *   <li>{@code config/capi/rp/}：放待加密的资源包 .zip</li>
 *   <li>{@code config/capi/rp/dat/}：启动后生成的同名 .dat</li>
 * </ul>
 */
public final class CapiConfig {

    private static final String KEY_FIELD = "resource_pack_key";

    private final Path configDir;
    private final Path configFile;
    private final Path rpDir;
    private final Path datDir;
    private byte[] keyMaterial;

    public CapiConfig() {
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("capi");
        this.configFile = configDir.resolve("config.toml");
        this.rpDir = configDir.resolve("rp");
        this.datDir = rpDir.resolve("dat");
    }

    public Path configDir() {
        return configDir;
    }

    public Path rpDir() {
        return rpDir;
    }

    public Path datDir() {
        return datDir;
    }

    public byte[] keyMaterial() {
        return keyMaterial;
    }

    /** 读取配置；缺失或密钥非法时生成随机密钥并写出默认 config.toml。 */
    public void loadOrCreate() throws IOException {
        Files.createDirectories(configDir);
        Files.createDirectories(rpDir);
        Files.createDirectories(datDir);

        if (Files.exists(configFile)) {
            String text = Files.readString(configFile, StandardCharsets.UTF_8);
            String key = parseString(text, KEY_FIELD);
            if (key != null) {
                try {
                    keyMaterial = PackCipher.decodeKey(key);
                    return;
                } catch (IllegalArgumentException ignored) {
                    // 密钥非法：落到重新生成分支
                }
            }
        }
        keyMaterial = PackCipher.randomKeyMaterial();
        writeDefault(PackCipher.encodeKey(keyMaterial));
    }

    /** 极简 TOML 解析：取顶层 key = "value" 或 key = 'value' / 裸值。 */
    private static String parseString(String text, String wanted) {
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = line.substring(0, eq).trim();
            if (!k.equals(wanted)) {
                continue;
            }
            String v = line.substring(eq + 1).trim();
            if ((v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2)
                    || (v.startsWith("'") && v.endsWith("'") && v.length() >= 2)) {
                return v.substring(1, v.length() - 1);
            }
            return v;
        }
        return null;
    }

    private void writeDefault(String b64) throws IOException {
        String content =
                "# ComponentsAPI 客户端配置\n"
                + "# 资源包加密密钥（base64，解码后必须 >= 64 字节即 512 位）。\n"
                + "# 首次启动自动生成；请妥善保管——丢失后已加密的 .dat 将无法解密。\n"
                + "# 工作流：把资源包 .zip 放进 config/capi/rp/，启动后会在\n"
                + "# config/capi/rp/dat/ 生成同名 .dat；再把 .dat 放进模组 jar 的\n"
                + "# capi_packs/ 目录，运行时会在内存中解密并直接加载（不落临时文件）。\n"
                + KEY_FIELD + " = \"" + b64 + "\"\n";
        Files.writeString(configFile, content, StandardCharsets.UTF_8);
    }
}
