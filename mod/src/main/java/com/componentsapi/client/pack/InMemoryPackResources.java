package com.componentsapi.client.pack;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 完全驻留内存的资源包：资源以 {@code zip 内部路径 -> 字节} 的 map 形式持有，
 * 解密后直接喂给资源系统，不向磁盘（包括临时目录）写出任何文件。
 *
 * <p>每次 {@code openPrimary/openFull} 都会新建一个实例，但它们共享同一个
 * 只读 map，因此 {@link #close()} 不销毁底层数据。
 */
public final class InMemoryPackResources extends AbstractPackResources {

    private final Map<String, byte[]> files;

    public InMemoryPackResources(PackLocationInfo location, Map<String, byte[]> files) {
        super(location);
        this.files = files;
    }

    private IoSupplier<InputStream> supplier(String path) {
        byte[] data = files.get(path);
        if (data == null) {
            return null;
        }
        return () -> new ByteArrayInputStream(data);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return supplier(String.join("/", paths));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        return supplier(type.getDirectory() + "/" + id.getNamespace() + "/" + id.getPath());
    }

    @Override
    public void listResources(PackType type, String prefix, String suffix, ResourceOutput output) {
        String base = type.getDirectory() + "/";
        String dirPrefix = prefix.isEmpty() ? "" : prefix + "/";
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String full = entry.getKey();
            if (!full.startsWith(base)) {
                continue;
            }
            String rest = full.substring(base.length()); // <namespace>/<path>
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String namespace = rest.substring(0, slash);
            String path = rest.substring(slash + 1);
            if (!path.startsWith(dirPrefix)) {
                continue;
            }
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (!fileName.endsWith(suffix)) {
                continue;
            }
            byte[] data = entry.getValue();
            output.accept(Identifier.fromNamespaceAndPath(namespace, path),
                    () -> new ByteArrayInputStream(data));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        String base = type.getDirectory() + "/";
        Set<String> namespaces = new HashSet<>();
        for (String full : files.keySet()) {
            if (!full.startsWith(base)) {
                continue;
            }
            String rest = full.substring(base.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                namespaces.add(rest.substring(0, slash));
            }
        }
        return namespaces;
    }

    @Override
    public void close() {
        // 共享只读 map，不做释放
    }
}
