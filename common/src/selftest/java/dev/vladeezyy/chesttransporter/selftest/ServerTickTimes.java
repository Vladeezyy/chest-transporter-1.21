package dev.vladeezyy.chesttransporter.selftest;

import net.minecraft.server.MinecraftServer;

/** Server tick time samples for the stress scene; no client classes, so dedicated servers can call it */
public final class ServerTickTimes {
  static volatile boolean measuring;
  static long nanosSum;
  static int samples;

  private ServerTickTimes() {}

  /** After every server tick (wired by the loader's test hooks) */
  public static void onServerTick(MinecraftServer server) {
    if (measuring) {
      // the server's own average over its last 100 ticks, sampled every tick
      nanosSum += server.getAverageTickTimeNanos();
      samples++;
    }
  }
}
