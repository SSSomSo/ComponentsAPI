package com.componentsapi.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 基岩版几何模型（Bedrock geometry，format 1.16.0）。
 * 单位为基岩像素（16 = 1 方块），y 向上，坐标原点在实体脚部。
 *
 * 同时支持两种 UV：
 * - 盒子 UV（"uv": [u, v]）；
 * - 每面 UV（"uv": {"north": {"uv": [u0,v0,u1,v1]}, ...}），未写的面不生成。
 *
 * 手持物品挂点：
 * - 骨骼 JSON 中写 "capi:hand": "right"/"left"；
 * - 或按骨骼名识别（包含 hand/righthand/lefthand 等，见 parseHand）。
 */
public final class BedrockModel {

    public static final String[] FACE_NAMES =
            {"north", "south", "east", "west", "up", "down"};

    public static final class Cube {
        public float originX, originY, originZ;
        public float sizeX, sizeY, sizeZ;
        public float inflate;
        public float uvU, uvV;
        public boolean mirror;
        public float pivotX, pivotY, pivotZ;
        public float rotX, rotY, rotZ;
        /** 每面 UV（4 浮点：u0,v0,u1,v1），null = 该面使用盒子 UV；盒子 UV 也没有则不绘制。 */
        public final float[][] faceUV = new float[6][];
        /** 每面 UV 旋转角度（0/90/180/270）。 */
        public final int[] faceUVRotation = new int[6];
        public boolean perFace;
    }

    public static final class Bone {
        public String name;
        public String parent;
        public float pivotX, pivotY, pivotZ;
        public float rotX, rotY, rotZ;
        /** 0=无挂点，1=右手，2=左手。 */
        public int hand;
        public final List<Cube> cubes = new ArrayList<>();
    }

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

        if (geo.has("bones")) {
            for (JsonElement boneElement : geo.getAsJsonArray("bones")) {
                JsonObject bo = boneElement.getAsJsonObject();
                Bone bone = new Bone();
                bone.name = bo.get("name").getAsString();
                if (bo.has("parent")) {
                    bone.parent = bo.get("parent").getAsString();
                }
                float[] pivot = vec(bo, "pivot");
                bone.pivotX = pivot[0]; bone.pivotY = pivot[1]; bone.pivotZ = pivot[2];
                float[] rot = vec(bo, "rotation");
                bone.rotX = rot[0]; bone.rotY = rot[1]; bone.rotZ = rot[2];
                bone.hand = parseHand(bo, bone.name);

                if (bo.has("cubes")) {
                    for (JsonElement cubeElement : bo.getAsJsonArray("cubes")) {
                        JsonObject co = cubeElement.getAsJsonObject();
                        Cube cube = new Cube();
                        float[] origin = vec(co, "origin");
                        cube.originX = origin[0]; cube.originY = origin[1]; cube.originZ = origin[2];
                        float[] size = vec(co, "size");
                        cube.sizeX = size[0]; cube.sizeY = size[1]; cube.sizeZ = size[2];
                        if (co.has("inflate")) {
                            cube.inflate = co.get("inflate").getAsFloat();
                        }
                        JsonElement uvElement = co.get("uv");
                        if (uvElement.isJsonArray()) {
                            float[] uv = readFloats(uvElement.getAsJsonArray());
                            cube.uvU = uv[0]; cube.uvV = uv[1];
                        } else if (uvElement.isJsonObject()) {
                            cube.perFace = true;
                            JsonObject uvObject = uvElement.getAsJsonObject();
                            for (int f = 0; f < 6; f++) {
                                if (uvObject.has(FACE_NAMES[f])) {
                                    JsonObject faceObject = uvObject.getAsJsonObject(FACE_NAMES[f]);
                                    float[] face = readFloats(faceObject.getAsJsonArray("uv"));
                                    if (faceObject.has("uv_size")) {
                                        float[] uvSizeArr = readFloats(
                                                faceObject.getAsJsonArray("uv_size"));
                                        // uv 为左上角，uv_size 为宽高（允许负值）。
                                        cube.faceUV[f] = new float[]{
                                                face[0], face[1],
                                                face[0] + uvSizeArr[0], face[1] + uvSizeArr[1]};
                                    } else {
                                        // uv 为 [u0,v0,u1,v1] 两角形式。
                                        cube.faceUV[f] = new float[]{
                                                face[0], face[1], face[2], face[3]};
                                    }
                                    if (faceObject.has("rotation")) {
                                        cube.faceUVRotation[f] =
                                                (int) faceObject.get("rotation").getAsFloat();
                                    }
                                }
                            }
                        }
                        if (co.has("mirror")) {
                            cube.mirror = co.get("mirror").getAsBoolean();
                        }
                        if (co.has("pivot")) {
                            float[] p = vec(co, "pivot");
                            cube.pivotX = p[0]; cube.pivotY = p[1]; cube.pivotZ = p[2];
                        } else {
                            cube.pivotX = origin[0] + size[0] / 2f;
                            cube.pivotY = origin[1] + size[1] / 2f;
                            cube.pivotZ = origin[2] + size[2] / 2f;
                        }
                        if (co.has("rotation")) {
                            float[] r = vec(co, "rotation");
                            cube.rotX = r[0]; cube.rotY = r[1]; cube.rotZ = r[2];
                        }
                        bone.cubes.add(cube);
                    }
                }
                model.bones.add(bone);
            }
        }
        return model;
    }

    /** 手部挂点：优先 "capi:hand" 字段，否则按骨骼名启发式。 */
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
        // 仅匹配明确的 hand 命名，避免把 arm 肩膀当成挂点。
        if (n.contains("hand")) {
            if (n.startsWith("l") || n.contains("left") || n.contains("handl") || n.contains("lhand")) {
                return 2;
            }
            if (n.startsWith("r") || n.contains("right") || n.contains("handr") || n.contains("rhand")) {
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
