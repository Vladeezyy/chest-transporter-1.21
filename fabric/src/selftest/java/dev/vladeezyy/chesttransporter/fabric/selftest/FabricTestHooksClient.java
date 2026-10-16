package dev.vladeezyy.chesttransporter.fabric.selftest;

import dev.vladeezyy.chesttransporter.selftest.ClientScene;
import dev.vladeezyy.chesttransporter.selftest.NetScene;
import dev.vladeezyy.chesttransporter.selftest.StressScene;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class FabricTestHooksClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(mc -> {
      ClientScene.onClientTick();
      StressScene.onClientTick();
      NetScene.onClientTick();
    });
    // one world render per frame while in a world, which is when the stress scene counts frames
    WorldRenderEvents.END.register(context -> StressScene.onFrame());
  }
}
