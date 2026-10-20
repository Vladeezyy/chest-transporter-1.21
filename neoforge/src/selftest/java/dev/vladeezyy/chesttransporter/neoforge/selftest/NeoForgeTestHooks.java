package dev.vladeezyy.chesttransporter.neoforge.selftest;

import dev.vladeezyy.chesttransporter.ChestTransporter;
import dev.vladeezyy.chesttransporter.selftest.ClientScene;
import dev.vladeezyy.chesttransporter.selftest.NetScene;
import dev.vladeezyy.chesttransporter.selftest.SelfTest;
import dev.vladeezyy.chesttransporter.selftest.ServerTickTimes;
import dev.vladeezyy.chesttransporter.selftest.StressScene;
import dev.vladeezyy.chesttransporter.selftest.TestHooks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires the shared tests (common/src/selftest) to NeoForge events; dev runs only */
@EventBusSubscriber(modid = ChestTransporter.MOD_ID)
public final class NeoForgeTestHooks {
  static {
    TestHooks.fakePlayer = FakePlayerFactory::get;
  }

  private NeoForgeTestHooks() {}

  @SubscribeEvent
  static void serverStarted(ServerStartedEvent event) {
    SelfTest.serverStarted(event.getServer());
  }

  @SubscribeEvent
  static void serverTick(ServerTickEvent.Post event) {
    SelfTest.serverTick(event.getServer());
    ServerTickTimes.onServerTick(event.getServer());
  }

  @SubscribeEvent
  static void protection(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
    if (SelfTest.protect) {
      event.setCanceled(true);
    }
  }

  @EventBusSubscriber(modid = ChestTransporter.MOD_ID, value = Dist.CLIENT)
  static final class Client {
    private Client() {}

    @SubscribeEvent
    static void clientTick(ClientTickEvent.Post event) {
      ClientScene.onClientTick();
      StressScene.onClientTick();
      NetScene.onClientTick();
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Post event) {
      StressScene.onFrame();
    }
  }
}
