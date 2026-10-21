package com.componentsapi.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义附魔颜色注册表。
 *
 * 为什么需要独立类：Mixin 类中的静态方法必须是 private，不能是 public
 * （否则 Mixin apply 时会抛 InvalidMixinException）。所以把静态 Map 和
 * 公共访问方法放在这个非 Mixin 类中，ItemModelResolverMixin 写入，
 * ItemFeatureRendererMixin 读取。
 *
 * 传递方式：以 LayerRenderState.quads() 列表的 identityHashCode 作为 key，
 * ARGB int 作为 value。quads 列表在 prepareMainSubmit 和 prepareFoilSubmit
 * 之间是同一个对象实例，所以 identityHashCode 稳定。
 */
public final class GlintColorRegistry {

    /** key = System.identityHashCode(quads列表), value = ARGB int。 */
    private static final Map<Integer, Integer> CUSTOM_GLINT_COLORS = new HashMap<>();

    private GlintColorRegistry() {}

    /** ItemModelResolverMixin 调用：写入自定义附魔颜色。 */
    public static void put(int quadsIdentityHash, int argb) {
        CUSTOM_GLINT_COLORS.put(quadsIdentityHash, argb);
    }

    /** ItemFeatureRendererMixin 调用：读取自定义附魔颜色，无则返回 null。 */
    public static Integer get(int quadsIdentityHash) {
        return CUSTOM_GLINT_COLORS.get(quadsIdentityHash);
    }

    /** 可选：清理已渲染完成的颜色（避免内存泄漏）。目前不调用，Map 会随 GC 回收。 */
    public static void remove(int quadsIdentityHash) {
        CUSTOM_GLINT_COLORS.remove(quadsIdentityHash);
    }
}
