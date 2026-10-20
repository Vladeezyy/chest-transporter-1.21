package dev.vladeezyy.chesttransporter.fabric;

import dev.vladeezyy.chesttransporter.CarryEffects;
import dev.vladeezyy.chesttransporter.ConfigSyncPayload;
import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.client.LoadRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

public final class ChestTransporterFabricClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    // the load on the truck, the second part of its item model (ID_MAPPER opened by chest_transporter.accesswidener)
    SpecialModelRenderers.ID_MAPPER.put(LoadRenderer.ID, LoadRenderer.Unbaked.MAP_CODEC);
    ClientTickEvents.END_CLIENT_TICK.register(mc -> {
      if (mc.player != null) {
        CarryEffects.tick(mc.player);
      }
    });
    ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> TransporterConfig.receive(payload.values()));
    ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> TransporterConfig.resetSynced());
  }
}
