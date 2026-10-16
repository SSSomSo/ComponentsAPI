package com.componentsapi.client.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 资源包加密：AES-256-GCM 认证加密 + 自定义 .dat 容器。
 *
 * <p>这<b>不是</b> ZIP 自带加密：加密对象是整个资源包 zip 的字节流，
 * 输出的 .dat 是自定义二进制容器，无法用任何压缩软件打开。
 *
 * <p>密钥材料要求至少 64 字节（512 位），以 base64 存于 config/capi/config.toml；
 * 每包使用随机 salt，经 SHA-512 派生出 32 字节 AES-256 密钥；
 * GCM 同时提供机密性与完整性（密钥错误 / 被篡改会在解密时校验失败）。
 *
 * <p>容器布局（大端）：
 * <pre>
 *   magic "CAPIDAT1" (8) | salt (16) | nonce (12) | 明文长度 int (4) | 密文 + 128bit GCM tag
 * </pre>
 */
public final class PackCipher {

    public static final byte[] MAGIC = "CAPIDAT1".getBytes(StandardCharsets.UTF_8);
    public static final int SALT_LEN = 16;
    public static final int NONCE_LEN = 12;
    public static final int KEY_MATERIAL_BYTES = 64; // 512 位
    private static final int TAG_BITS = 128;
    private static final int AES_KEY_BYTES = 32;    // AES-256
    private static final int HEADER_LEN = MAGIC.length + SALT_LEN + NONCE_LEN + 4;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 生成 64 字节（512 位）随机密钥材料。 */
    public static byte[] randomKeyMaterial() {
        byte[] k = new byte[KEY_MATERIAL_BYTES];
        RANDOM.nextBytes(k);
        return k;
    }

    public static String encodeKey(byte[] material) {
        return Base64.getEncoder().encodeToString(material);
    }

    /** 解码并校验 base64 密钥材料，不足 64 字节抛异常。 */
    public static byte[] decodeKey(String b64) {
        if (b64 == null) {
            throw new IllegalArgumentException("缺少资源包密钥 resource_pack_key");
        }
        byte[] k = Base64.getDecoder().decode(b64.trim());
        if (k.length < KEY_MATERIAL_BYTES) {
            throw new IllegalArgumentException(
                    "资源包密钥必须至少 64 字节（512 位），当前为 " + k.length + " 字节");
        }
        return k;
    }

    /** 由 512 位材料 + 每包 salt 派生 AES-256 密钥。 */
    private static SecretKey deriveKey(byte[] material, byte[] salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(salt);
        md.update(material);
        byte[] h1 = md.digest();
        md.reset();
        md.update(h1);
        md.update(salt);
        byte[] h2 = md.digest(); // 64 字节
        return new SecretKeySpec(h2, 0, AES_KEY_BYTES, "AES");
    }

    /** 加密一段资源包 zip 字节为 .dat。 */
    public static byte[] encrypt(byte[] plain, byte[] material) throws Exception {
        if (material == null || material.length < KEY_MATERIAL_BYTES) {
            throw new IllegalArgumentException("密钥材料不足 512 位");
        }
        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);
        byte[] nonce = new byte[NONCE_LEN];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(material, salt),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] ct = cipher.doFinal(plain);

        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_LEN + ct.length);
        out.write(MAGIC);
        out.write(salt);
        out.write(nonce);
        int len = plain.length;
        out.write((len >>> 24) & 0xFF);
        out.write((len >>> 16) & 0xFF);
        out.write((len >>> 8) & 0xFF);
        out.write(len & 0xFF);
        out.write(ct);
        return out.toByteArray();
    }

    /** 解密 .dat 为资源包 zip 字节；magic 不符 / GCM 校验失败抛异常。 */
    public static byte[] decrypt(byte[] dat, byte[] material) throws Exception {
        if (dat == null || dat.length < HEADER_LEN) {
            throw new IllegalArgumentException(".dat 文件过小或已损坏");
        }
        int p = 0;
        for (byte m : MAGIC) {
            if (dat[p++] != m) {
                throw new IllegalArgumentException("不是有效的 ComponentsAPI 加密包（magic 不匹配）");
            }
        }
        byte[] salt = new byte[SALT_LEN];
        System.arraycopy(dat, p, salt, 0, SALT_LEN);
        p += SALT_LEN;
        byte[] nonce = new byte[NONCE_LEN];
        System.arraycopy(dat, p, nonce, 0, NONCE_LEN);
        p += NONCE_LEN;
        int len = ((dat[p] & 0xFF) << 24) | ((dat[p + 1] & 0xFF) << 16)
                | ((dat[p + 2] & 0xFF) << 8) | (dat[p + 3] & 0xFF);
        p += 4;
        byte[] ct = new byte[dat.length - p];
        System.arraycopy(dat, p, ct, 0, ct.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(material, salt),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] plain;
        try {
            plain = cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalArgumentException("解密失败：密钥错误或资源包已被篡改（" + e.getMessage() + "）");
        }
        if (plain.length != len) {
            throw new IllegalArgumentException("解密后长度与记录不符，资源包可能已损坏");
        }
        return plain;
    }

    private PackCipher() {}
}
