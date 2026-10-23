package com.componentsapi.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基岩版几何模型（Bedrock geometry，format 1.16.0）。
 *
 * <p><b>坐标系（重写，1.14.0）</b>：geo.json 里的 pivot / origin 都是基岩版
 * 「全局、Y 轴向上」的坐标。本类在解析阶段一次性转换为 Minecraft Java 版实体
 * 模型（{@code ModelPart}）使用的「局部、Y 轴向下」坐标，并把每个 cube 烘焙成
 * 6 个 quad（顶点坐标 + 归一化 UV + 轴向法线）。渲染时与原版
 * {@code ModelPart.Cube} 走完全相同的数值，因此精度与 Blockbench 预览一致。
 *
 * <p>转换规则（来自 TaCZ / 原版 ModelPart，逐项核对）：
 * <ul>
 *   <li>bone pivot 相对父骨骼：x / z = pivot − parentPivot；y = parentPivot − pivot
 *       （根骨骼以 y = 24 作为父基准，对应实体脚部原点）。</li>
 *   <li>无旋转 cube 的 origin 相对 bone：x / z = origin − pivot；
 *       y = pivot − origin − size（Y 轴反向且立方体向下延伸）。</li>
 *   <li>旋转 cube：旋转中心（pivot）相对 bone 走与上面相同的规则；顶点坐标相对
 *       该 cube 的 pivot（y = cubePivot − origin − size）。</li>
 *   <li>角度存度，渲染时转弧度，按 Z → Y → X 顺序旋转（与 TaCZ / ModelPart 一致）。</li>
 * </ul>
 *
 * <p>两种 UV：盒子 UV（{@code "uv":[u,v]}，与原版立方体展开图一致）与每面 UV
 * （{@code "uv":{"north":{...},...}}，未写的面不生成）。
 *
 * <p>手持物品手臂定位组：骨骼名以 {@code lefthand_pos} / {@code righthand_pos}
 * 结尾（TaCZ 约定）。定位组自身的参考 cube <b>不渲染</b>，第一人称会在该组位置
 * 渲染玩家真实手臂；普通手持挂点仍由 {@code capi:hand} / 名称启发式识别。
 */
public final class BedrockModel {

    /** 根骨骼在基岩坐标中的 Y 基准（实体脚部原点，1.5 格 = 24 像素）。 */
    private static final float ROOT_Y = 24.0f;

    /** 像素 → 方块（16 像素 = 1 方块）。 */
    private static final float UNIT = 1.0f / 16.0f;

    /**
     * 一个烘焙好的四边形面。4 个顶点已是最终坐标（单位方块）与归一化 UV，
     * 渲染器直接按序写入即可。
     */
    public static final class Polygon {
        public final float[] x = new float[4];
        public final float[] y = new float[4];
        public final float[] z = new float[4];
        public final float[] u = new float[4];
        public final float[] v = new float[4];
        public float nx, ny, nz;
        /** 每面 UV 未提供时为 false（该面不绘制）。 */
        public final boolean visible;

        Polygon(boolean visible, float nx, float ny, float nz) {
            this.visible = visible;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        /** 镜像：4 顶点（位置 + UV 一起）整体逆序，法线 X 取反（与 TaCZ 一致）。 */
        void mirror() {
            for (int i = 0; i < 2; i++) {
                int j = 3 - i;
                swap(x, i, j); swap(y, i, j); swap(z, i, j);
                swap(u, i, j); swap(v, i, j);
            }
            nx = -nx;
        }

        private static void swap(float[] a, int i, int j) {
            float t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    public static final class Cube {
        /** 旋转中心相对 bone 的局部坐标（单位方块，Y 向下）；无旋转为 0。 */
        public float offX, offY, offZ;
        /** 旋转角度（度）。 */
        public float rotX, rotY, rotZ;
        public boolean hasRot;
        /** 6 个面，索引见 {@link #FACE_KEYS}。 */
        public final Polygon[] polys = new Polygon[6];
    }

    public static final class Bone {
        public String name;
        public String parent;

        /** 基岩全局 pivot（Y 向上），保留用于定位组 / 锚点。 */
        public float pivotX, pivotY, pivotZ;
        /** Java 局部位置（Y 向下，相对父，单位方块），解析时烘焙。 */
        public float posX, posY, posZ;
        /** 静态旋转（度，基岩原始值，渲染按 Z→Y→X 转弧度）。 */
        public float rotX, rotY, rotZ;

        /** 0=无手持挂点，1=右手，2=左手。 */
        public int hand;
        /** true=手臂定位组（lefthand_pos/righthand_pos）：参考 cube 不画，替换为玩家手臂。 */
        public boolean handPos;
        /** 定位组手臂（handPos 时有效）：1=右手，2=左手。 */
        public int handPosArm;

        public final List<Cube> cubes = new ArrayList<>();
    }

    /**
     * 面的固定顺序（数组索引）。每个面记录：JSON 名、4 个顶点索引（0..7）、
     * 轴向法线（Java Y 向下）。顶点编号对应 {@link #buildCube} 中 v1..v8。
     */
    private static final String[] FACE_KEYS =
            {"down", "up", "north", "south", "west", "east"};
    // 索引：0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST
    private static final int[][] FACE_VERTS = {
            {5, 4, 0, 1}, // DOWN  : v6 v5 v1 v2
            {2, 3, 7, 6}, // UP    : v3 v4 v8 v7
            {1, 0, 3, 2}, // NORTH : v2 v1 v4 v3
            {4, 5, 6, 7}, // SOUTH : v5 v6 v7 v8
            {0, 4, 7, 3}, // WEST  : v1 v5 v8 v4
            {5, 1, 2, 6}, // EAST  : v6 v2 v3 v7
    };
    private static final float[][] FACE_NORMAL = {
            {0, -1, 0},  // DOWN
            {0, 1, 0},   // UP
            {0, 0, -1},  // NORTH
            {0, 0, 1},   // SOUTH
            {-1, 0, 0},  // WEST
            {1, 0, 0},   // EAST
    };

    public String texture;
    public int textureWidth = 64;
    public int textureHeight = 64;
    public final List<Bone> bones = new ArrayList<>();

    /** 解析单个 .geo.json（取其中第一个 geometry）。 */
    public static BedrockModel parse(JsonObject root) {
        BedrockModel model = new BedrockModel();
        JsonArray geometries = root.has("minecraft:geometry")
                ? root.getAsJsonArray("minecraft:geometry")
                : root.getAsJsonArray("geometry");
        JsonObject geo = geometries.get(0).getAsJsonObject();

        JsonObject description = geo.getAsJsonObject("description");
        model.textureWidth = description.get("texture_width").getAsInt();
        model.textureHeight = description.get("texture_height").getAsInt();
        if (description.has("texture")) {
            model.texture = description.get("texture").getAsString();
        }

        if (!geo.has("bones")) {
            return model;
        }

        // 第一遍：读入基岩原始数据（pivot / rotation / 名称 / 挂点）。
        Map<String, JsonObject> raw = new HashMap<>();
        for (JsonElement boneElement : geo.getAsJsonArray("bones")) {
            JsonObject bo = boneElement.getAsJsonObject();
            String name = bo.get("name").getAsString();
            Bone bone = new Bone();
            bone.name = name;
            if (bo.has("parent")) {
                bone.parent = bo.get("parent").getAsString();
            }
            float[] pivot = vec(bo, "pivot");
            bone.pivotX = pivot[0];
            bone.pivotY = pivot[1];
            bone.pivotZ = pivot[2];
            float[] rot = vec(bo, "rotation");
            bone.rotX = rot[0];
            bone.rotY = rot[1];
            bone.rotZ = rot[2];
            bone.hand = parseHand(bo, name);
            // 定位组：lefthand_pos / righthand_pos（TaCZ 约定，组名唯一）。
            String lower = name.toLowerCase();
            if (lower.endsWith("_pos") && (lower.contains("hand") || lower.contains("arm"))) {
                bone.handPos = true;
                if (lower.startsWith("left") || lower.contains("lefthand") || lower.contains("leftarm")) {
                    bone.handPosArm = 2;
                } else if (lower.startsWith("right") || lower.contains("righthand") || lower.contains("rightarm")) {
                    bone.handPosArm = 1;
                } else {
                    // 兜底：名称含 left/right 子串。
                    bone.handPosArm = lower.contains("left") ? 2 : 1;
                }
            }
            model.bones.add(bone);
            raw.put(name, bo);
        }

        // 第二遍：烘焙 Java 局部位置（需要父 pivot）。
        Map<String, Bone> byName = new HashMap<>();
        for (Bone bone : model.bones) {
            byName.put(bone.name, bone);
        }
        for (Bone bone : model.bones) {
            Bone parent = bone.parent != null ? byName.get(bone.parent) : null;
            float parentY = parent != null ? parent.pivotY : ROOT_Y;
            float parentX = parent != null ? parent.pivotX : 0f;
            float parentZ = parent != null ? parent.pivotZ : 0f;
            // x/z：子 − 父；y：父 − 子（Y 轴向下反转）。
            bone.posX = bone.pivotX - parentX;
            bone.posY = parentY - bone.pivotY;
            bone.posZ = bone.pivotZ - parentZ;
            bone.posX *= UNIT;
            bone.posY *= UNIT;
            bone.posZ *= UNIT;

            // 构建 cubes（定位组的 cube 也会被解析，但渲染时整组跳过）。
            JsonObject bo = raw.get(bone.name);
            if (bo.has("cubes")) {
                for (JsonElement cubeElement : bo.getAsJsonArray("cubes")) {
                    buildCube(model, bone, cubeElement.getAsJsonObject());
                }
            }
        }
        return model;
    }

    /** 解析并烘焙一个 cube 为 6 个面。 */
    private static void buildCube(BedrockModel model, Bone bone, JsonObject co) {
        float[] origin = vec(co, "origin");
        float[] size = vec(co, "size");
        float inflate = co.has("inflate") ? co.get("inflate").getAsFloat() : 0f;
        boolean mirror = co.has("mirror") && co.get("mirror").getAsBoolean();

        boolean hasRotation = co.has("rotation");
        float[] cubePivot = co.has("pivot")
                ? vec(co, "pivot")
                : new float[]{origin[0] + size[0] / 2f,
                              origin[1] + size[1] / 2f,
                              origin[2] + size[2] / 2f};
        float[] rot = vec(co, "rotation");

        Cube cube = new Cube();
        cube.hasRot = hasRotation && (rot[0] != 0 || rot[1] != 0 || rot[2] != 0);
        cube.rotX = rot[0];
        cube.rotY = rot[1];
        cube.rotZ = rot[2];

        // ---- 旋转中心相对 bone 的 Java 局部坐标（Y 向下），转单位方块 ----
        // 无旋转 cube：旋转中心就是 bone 原点（off=0），顶点相对 bone。
        // 有旋转 cube：旋转中心为 cube pivot，顶点相对 cube pivot。
        if (cube.hasRot) {
            float offX = cubePivot[0] - bone.pivotX;
            float offY = bone.pivotY - cubePivot[1];
            float offZ = cubePivot[2] - bone.pivotZ;
            cube.offX = offX * UNIT;
            cube.offY = offY * UNIT;
            cube.offZ = offZ * UNIT;
        }

        // ---- 顶点参考原点（基岩，Y 向上）→ Java 局部（Y 向下）----
        // 顶点相对哪个 pivot：有旋转用 cubePivot，无旋转用 bone pivot。
        float refX = cube.hasRot ? cubePivot[0] : bone.pivotX;
        float refY = cube.hasRot ? cubePivot[1] : bone.pivotY;
        float refZ = cube.hasRot ? cubePivot[2] : bone.pivotZ;
        // min 角（Java Y 向下）：x/z = origin − ref；y = ref − origin − size。
        float minX = origin[0] - refX;
        float minY = refY - origin[1] - size[1];
        float minZ = origin[2] - refZ;
        float maxX = minX + size[0];
        float maxY = minY + size[1];
        float maxZ = minZ + size[2];
        // inflate：min 角减、max 角加。
        float x0 = (minX - inflate) * UNIT;
        float y0 = (minY - inflate) * UNIT;
        float z0 = (minZ - inflate) * UNIT;
        float x1 = (maxX + inflate) * UNIT;
        float y1 = (maxY + inflate) * UNIT;
        float z1 = (maxZ + inflate) * UNIT;

        // 8 个顶点 v1..v8（Java 标准 ModelPart 布局）。
        float[][] v = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1},
        };

        // ---- UV ----
        JsonElement uvElement = co.get("uv");
        boolean perFace = uvElement != null && uvElement.isJsonObject();
        float boxU = 0f, boxV = 0f;
        JsonObject faceUvObject = null;
        if (!perFace) {
            if (uvElement != null && uvElement.isJsonArray()) {
                float[] uv = readFloats(uvElement.getAsJsonArray());
                boxU = uv[0];
                boxV = uv[1];
            }
        } else {
            faceUvObject = uvElement.getAsJsonObject();
        }
        float dx = size[0], dy = size[1], dz = size[2];

        for (int f = 0; f < 6; f++) {
            float[] nn = FACE_NORMAL[f];
            // 该面 UV 矩形（u1,v1,u2,v2，未归一化）。
            float[] rect;
            boolean visible;
            if (perFace) {
                float[] pf = readPerFace(faceUvObject, FACE_KEYS[f]);
                if (pf == null) {
                    cube.polys[f] = new Polygon(false, nn[0], nn[1], nn[2]);
                    continue;
                }
                rect = pf;
                visible = true;
            } else {
                if (uvElement == null) {
                    cube.polys[f] = new Polygon(false, nn[0], nn[1], nn[2]);
                    continue;
                }
                rect = boxUvRect(f, boxU, boxV, dx, dy, dz);
                visible = true;
            }
            Polygon poly = new Polygon(visible, nn[0], nn[1], nn[2]);
            int[] vi = FACE_VERTS[f];
            float u1 = rect[0], v1 = rect[1], u2 = rect[2], v2 = rect[3];
            // 4 个顶点的 UV（BedrockPolygon 约定）：
            //   vert0=(u2,v1) vert1=(u1,v1) vert2=(u1,v2) vert3=(u2,v2)
            float[][] uv4 = {
                    {u2, v1}, {u1, v1}, {u1, v2}, {u2, v2},
            };
            // 非镜像：顶点 vi[k] 配对 uv4[k]。
            for (int k = 0; k < 4; k++) {
                int vertexIndex = vi[k];
                poly.x[k] = v[vertexIndex][0];
                poly.y[k] = v[vertexIndex][1];
                poly.z[k] = v[vertexIndex][2];
                poly.u[k] = uv4[k][0] / model.textureWidth;
                poly.v[k] = uv4[k][1] / model.textureHeight;
            }
            // 镜像：位置 + UV 整体逆序，法线 X 取反。
            if (mirror) {
                poly.mirror();
            }
            cube.polys[f] = poly;
        }
        bone.cubes.add(cube);
    }

    /**
     * 盒子 UV 各面矩形（与原版 ModelPart.Cube 展开图一致）。
     * 面索引：0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST。
     */
    private static float[] boxUvRect(int face, float u, float v,
                                     float dx, float dy, float dz) {
        float p1 = u + dz;
        float p2 = u + dz + dx;
        float p3 = u + dz + dx + dx;
        float p4 = u + dz + dx + dz;
        float p5 = u + dz + dx + dz + dx;
        float p6 = v + dz;
        float p7 = v + dz + dy;
        float p8 = v;
        float p9 = u;
        switch (face) {
            case 0: return new float[]{p1, p8, p2, p6}; // DOWN
            case 1: return new float[]{p2, p6, p3, p8}; // UP
            case 2: return new float[]{p1, p6, p2, p7}; // NORTH
            case 3: return new float[]{p4, p6, p5, p7}; // SOUTH
            case 4: return new float[]{p9, p6, p1, p7}; // WEST
            case 5: return new float[]{p2, p6, p4, p7}; // EAST
            default: return new float[]{p1, p6, p2, p7};
        }
    }

    /** 读取每面 UV，返回 {u1,v1,u2,v2}（未归一化）；缺失返回 null。 */
    private static float[] readPerFace(JsonObject faceUvObject, String key) {
        if (!faceUvObject.has(key)) {
            return null;
        }
        JsonObject face = faceUvObject.getAsJsonObject(key);
        if (!face.has("uv")) {
            return null;
        }
        float[] uv = readFloats(face.getAsJsonArray("uv"));
        if (face.has("uv_size")) {
            float[] size = readFloats(face.getAsJsonArray("uv_size"));
            // uv 为左上角，uv_size 为宽高（允许负值）。
            return new float[]{uv[0], uv[1], uv[0] + size[0], uv[1] + size[1]};
        }
        // 两角形式 [u0,v0,u1,v1]。
        return new float[]{uv[0], uv[1], uv[2], uv[3]};
    }

    /** 手部挂点：优先 "capi:hand" 字段，否则按骨骼名启发式。兼容原版/TaCZ/YSM。 */
    private static int parseHand(JsonObject boneObject, String boneName) {
        if (boneObject.has("capi:hand")) {
            String value = boneObject.get("capi:hand").getAsString().toLowerCase();
            if (value.startsWith("l")) {
                return 2;
            }
            if (value.startsWith("r")) {
                return 1;
            }
        }
        String n = boneName.toLowerCase();
        if (n.contains("hand")) {
            if (n.startsWith("l") || n.contains("left") || n.contains("handl") || n.contains("lhand")) {
                return 2;
            }
            if (n.startsWith("r") || n.contains("right") || n.contains("handr") || n.contains("rhand")) {
                return 1;
            }
        }
        if (n.contains("arm") && !n.contains("shoulder")) {
            if (n.startsWith("l") || n.contains("left") || n.contains("arml") || n.contains("larm")) {
                return 2;
            }
            if (n.startsWith("r") || n.contains("right") || n.contains("armr") || n.contains("rarm")) {
                return 1;
            }
        }
        return 0;
    }

    private static float[] vec(JsonObject object, String key) {
        if (!object.has(key)) {
            return new float[]{0, 0, 0};
        }
        return readFloats(object.getAsJsonArray(key));
    }

    private static float[] readFloats(JsonArray array) {
        float[] result = new float[Math.max(4, array.size())];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
    }
}
