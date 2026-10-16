package dev.vladeezyy.chesttransporter.fabric;

import dev.vladeezyy.chesttransporter.CarryEffects;
import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.ConfigSyncPayload;
import dev.vladeezyy.chesttransporter.MinecartTransport;
import dev.vladeezyy.chesttransporter.ModRegistry;
import dev.vladeezyy.chesttransporter.Transport;
import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.TransporterTier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;

public final class ChestTransporterFabric implements ModInitializer {
  @Override
  public void onInitialize() {
    ChestTransporter.init();
    // before the clicked block's own use, so a chest is picked up instead of opened
    UseBlockCallback.EVENT.register(Transport::onUseBlock);
    UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> MinecartTransport.onUseEntity(player, level, hand, entity));
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
        CarryEffects.tick(player);
      }
    });
    ServerLifecycleEvents.SERVER_STARTING.register(server -> TransporterConfig.loadServer());
    PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC.cast());
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      if (ServerPlayNetworking.canSend(handler.getPlayer(), ConfigSyncPayload.TYPE)) {
        ServerPlayNetworking.send(handler.getPlayer(), new ConfigSyncPayload(TransporterConfig.server()));
      }
    });
    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
      for (TransporterTier tier : TransporterTier.values()) {
        entries.accept(ModRegistry.truck(tier));
      }
    });
  }
}
