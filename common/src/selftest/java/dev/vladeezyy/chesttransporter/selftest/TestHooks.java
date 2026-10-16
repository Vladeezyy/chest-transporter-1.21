package dev.vladeezyy.chesttransporter.selftest;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiFunction;

/** What the shared tests need from the loader, set by its test bootstrap */
public final class TestHooks {
  /** A fake player of the loader (NeoForge or Fabric API) */
  public static BiFunction<ServerLevel, GameProfile, ServerPlayer> fakePlayer;

  static final GameProfile TESTER = new GameProfile(UUID.fromString("41c82c87-7afb-4024-ba57-13d2c99cae77"), "[Chest Transporter test]");

  private TestHooks() {}
}
