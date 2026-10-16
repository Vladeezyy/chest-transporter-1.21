package dev.vladeezyy.chesttransporter;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The server's config, sent to a client when it joins */
public record ConfigSyncPayload(TransporterConfig values) implements CustomPacketPayload {
  public static final Type<ConfigSyncPayload> TYPE = new Type<>(ChestTransporter.id("config"));
  public static final StreamCodec<ByteBuf, ConfigSyncPayload> STREAM_CODEC = TransporterConfig.STREAM_CODEC.map(ConfigSyncPayload::new, ConfigSyncPayload::values);

  @Override
  public Type<ConfigSyncPayload> type() {
    return TYPE;
  }
}
