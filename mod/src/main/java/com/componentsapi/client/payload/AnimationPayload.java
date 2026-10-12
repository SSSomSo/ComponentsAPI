package com.componentsapi.client.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端 第一人称动画播放报文。
 * 由 /playbedrockanimation 命令触发，通过插件消息通道 componentsapi:anim 发送。
 *
 * 报文格式（DataOutputStream）：
 *   byte type (0=play, 1=stop)
 *   UTF  animFile (namespace:animations/xxx.animation.json)
 *   UTF  animName
 *   int  durationTicks (-1=循环)
 */
public record AnimationPayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnimationPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("componentsapi", "anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnimationPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBytes(payload.data),
                    buf -> {
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        return new AnimationPayload(bytes);
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
