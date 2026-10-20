package dev.vladeezyy.chesttransporter.neoforge;

import dev.vladeezyy.chesttransporter.TransporterConfig;
import dev.vladeezyy.chesttransporter.client.LoadRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClient {
  private NeoForgeClient() {}

  public static void init(IEventBus modBus) {
    // the load on the truck, the second part of its item model
    modBus.addListener((RegisterSpecialModelRendererEvent event) -> event.register(LoadRenderer.ID, LoadRenderer.Unbaked.MAP_CODEC));
    NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> TransporterConfig.resetSynced());
  }
}
