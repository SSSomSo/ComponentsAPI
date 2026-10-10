package com.componentsapi.client.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncPayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("componentsapi", "sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBytes(payload.data),
                    buf -> { byte[] bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); return new SyncPayload(bytes); });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
