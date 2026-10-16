package dev.vladeezyy.chesttransporter.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/** What the common code needs from the mod loader (NeoForge or Fabric) */
public interface Platform {
  Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
    .orElseThrow(() -> new IllegalStateException("No Chest Transporter platform for this mod loader"));

  /** "neoforge" or "fabric" */
  String name();

  Path configDir();

  /** Registers an entry (NeoForge: deferred register, Fabric: right away); the supplier gives it once registered */
  <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory);

  /** False if a protection (claim mods) cancels breaking the block by the player */
  boolean mayBreak(Level level, BlockPos pos, BlockState state, Player player);

  /**
   * Called right before the block is set: the guard is asked after setting it whether protections allow the placement
   * (NeoForge place event; Fabric has no such event and relies on the vanilla build checks done before). A guard
   * that refuses has already put the old block back.
   */
  PlaceGuard beforePlace(Level level, BlockPos pos);

  interface PlaceGuard {
    boolean confirm(BlockState placedAgainst, Player player);
  }

  void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
