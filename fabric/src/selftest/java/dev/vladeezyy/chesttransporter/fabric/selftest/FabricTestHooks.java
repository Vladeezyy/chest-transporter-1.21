package dev.vladeezyy.chesttransporter.fabric.selftest;

import dev.vladeezyy.chesttransporter.selftest.SelfTest;
import dev.vladeezyy.chesttransporter.selftest.ServerTickTimes;
import dev.vladeezyy.chesttransporter.selftest.TestHooks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

/** Wires the shared tests (common/src/selftest) to Fabric API events; dev runs only */
public final class FabricTestHooks implements ModInitializer {
  @Override
  public void onInitialize() {
    TestHooks.fakePlayer = FakePlayer::get;
    ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::serverStarted);
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      SelfTest.serverTick(server);
      ServerTickTimes.onServerTick(server);
    });
    PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !SelfTest.protect);
  }
}
